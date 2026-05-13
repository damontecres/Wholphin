package com.github.damontecres.wholphin.ui.detail

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.HomeSettingsService
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.ThemeSongPlayer
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.services.tvAccess
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.Optional
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.playback.scale
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.MediaType
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = HomeRowGridViewModel.Factory::class)
class HomeRowGridViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext private val context: Context,
        val serverRepository: ServerRepository,
        private val userPreferencesService: UserPreferencesService,
        private val navDrawerService: NavDrawerService,
        private val homeSettingsService: HomeSettingsService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val backdropService: BackdropService,
        private val navigationManager: NavigationManager,
        private val themeSongPlayer: ThemeSongPlayer,
        private val mediaManagementService: MediaManagementService,
        private val musicService: MusicService,
        val streamChoiceService: StreamChoiceService,
        val mediaReportService: MediaReportService,
        @Assisted private val title: String,
        @Assisted private val rowConfig: HomeRowConfig,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                title: String,
                rowConfig: HomeRowConfig,
            ): HomeRowGridViewModel
        }

        private val _state = MutableStateFlow(HomeRowGridState())
        val state: StateFlow<HomeRowGridState> = _state

        init {
            viewModelScope.launchDefault {
                try {
                    val preferences = userPreferencesService.getCurrent()
                    val prefs = preferences.appPreferences.homePagePreferences
                    serverRepository.currentUserDto.value?.let { userDto ->
                        val libraries =
                            navDrawerService.getAllUserLibraries(userDto.id, userDto.tvAccess)
                        val result =
                            homeSettingsService.fetchDataForRow(
                                row = rowConfig,
                                scope = viewModelScope,
                                prefs = prefs,
                                userDto = userDto,
                                libraries = libraries,
                                isRefresh = false,
                                limit = 100, // TODO
                            )
                        _state.update { it.copy(loading = result) }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching: %s", rowConfig)
                    _state.update { it.copy(loading = HomeRowLoadingState.Error(title, null, ex)) }
                }
            }
        }

        fun setWatched(
            position: Int,
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            (state.value.loading as? HomeRowLoadingState.Success)?.let {
                (it.items as? ApiRequestPager<*>)?.refreshItem(position, itemId)
            }
        }

        fun setFavorite(
            position: Int,
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            (state.value.loading as? HomeRowLoadingState.Success)?.let {
                (it.items as? ApiRequestPager<*>)?.refreshItem(position, itemId)
            }
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }

        fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }

        fun canDelete(
            item: BaseItem,
            appPreferences: AppPreferences,
        ): Boolean = mediaManagementService.canDelete(item, appPreferences)

        fun deleteItem(
            index: Int,
            item: BaseItem,
        ) {
            deleteItem(context, mediaManagementService, item) {
                viewModelScope.launchDefault {
                    // TODO refresh
                }
            }
        }
    }

data class HomeRowGridState(
    val loading: HomeRowLoadingState = HomeRowLoadingState.Pending(""),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeRowGrid(
    preferences: UserPreferences,
    destination: Destination.MoreHomeRow,
    modifier: Modifier = Modifier,
    viewModel: HomeRowGridViewModel =
        hiltViewModel<HomeRowGridViewModel, HomeRowGridViewModel.Factory>(
            creationCallback = { it.create(destination.title, destination.config) },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var position by rememberInt(preferences.appPreferences.homePagePreferences.maxItemsPerRow)
    val gridFocusRequester = remember { FocusRequester() }
    val viewOptions = destination.config.viewOptions

    var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)

    val contextActions =
        remember {
            ContextMenuActions(
                navigateTo = viewModel::navigateTo,
                onClickWatch = { itemId, watched ->
                    viewModel.setWatched(position, itemId, watched)
                },
                onClickFavorite = { itemId, favorite ->
                    viewModel.setFavorite(position, itemId, favorite)
                },
                onClickAddPlaylist = { itemId ->
                    playlistViewModel.loadPlaylists(MediaType.VIDEO)
                    showPlaylistDialog.makePresent(itemId)
                },
                onSendMediaInfo = viewModel.mediaReportService::sendReportFor,
                onDeleteItem = { viewModel.deleteItem(position, it) },
                onShowOverview = { overviewDialog = ItemDetailsDialogInfo(it) },
                onChooseVersion = { _, _ ->
                    // Not supported on this page
                },
                onChooseTracks = { result ->
                    // Not supported on this page
                },
                onClearChosenStreams = {
                    // Not supported on this page
                },
            )
        }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        when (val st = state.loading) {
            is HomeRowLoadingState.Error -> {
                ErrorMessage(st.message, st.exception, Modifier.fillMaxSize())
            }

            is HomeRowLoadingState.Loading,
            is HomeRowLoadingState.Pending,
            -> {
                LoadingPage(Modifier.fillMaxSize())
            }

            is HomeRowLoadingState.Success -> {
                val onClickItem =
                    remember {
                        { index: Int, item: BaseItem ->
                            viewModel.navigateTo(item.destination(index))
                        }
                    }
                val onLongClickItem =
                    remember {
                        { index: Int, item: BaseItem ->
                            showContextMenu =
                                ContextMenu.ForBaseItem(
                                    fromLongClick = true,
                                    item = item,
                                    chosenStreams = null,
                                    showGoTo = true,
                                    showStreamChoices = false,
                                    canDelete = viewModel.canDelete(item, preferences.appPreferences),
                                    canRemoveContinueWatching = false,
                                    canRemoveNextUp = false, // TODO
                                    actions = contextActions,
                                )
                        }
                    }
                CardGrid(
                    pager = st.items,
                    onClickItem = onClickItem,
                    onLongClickItem = onLongClickItem,
                    onClickPlay = { _, _ -> },
                    letterPosition = { -1 },
                    gridFocusRequester = gridFocusRequester,
                    showJumpButtons = false,
                    showLetterButtons = false,
                    modifier = Modifier.fillMaxSize(),
                    initialPosition = preferences.appPreferences.homePagePreferences.maxItemsPerRow,
                    positionCallback = { columns, newPosition ->
                        position = newPosition
                    },
                    cardContent = { (item, index, onClick, onLongClick, widthPx, mod) ->
                        GridCard(
                            item = item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            imageContentScale = viewOptions.contentScale.scale,
                            imageAspectRatio = viewOptions.aspectRatio.ratio,
                            imageType = viewOptions.imageType,
                            showTitle = viewOptions.showTitles,
                            fillWidth = widthPx,
                            modifier = mod,
                        )
                    },
                    columns = if (viewOptions.aspectRatio.ratio > 1f) 4 else 6,
                    spacing = viewOptions.spacing.dp,
                    bringIntoViewSpec = LocalBringIntoViewSpec.current,
                )
            }
        }
    }
    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            showFilePath =
                viewModel.serverRepository.currentUserDto.value
                    ?.policy
                    ?.isAdministrator == true,
            onDismissRequest = { overviewDialog = null },
        )
    }
    showContextMenu?.let { contextMenu ->
        ContextMenuDialog(
            onDismissRequest = { showContextMenu = null },
            getMediaSource = null,
            contextMenu = contextMenu,
            preferredSubtitleLanguage = null,
        )
    }
    showPlaylistDialog.compose { itemId ->
        PlaylistDialog(
            title = stringResource(R.string.add_to_playlist),
            state = playlistState,
            onDismissRequest = { showPlaylistDialog.makeAbsent() },
            onClick = {
                playlistViewModel.addToPlaylist(it.id, itemId)
                showPlaylistDialog.makeAbsent()
            },
            createEnabled = true,
            onCreatePlaylist = {
                playlistViewModel.createPlaylistAndAddItem(it, itemId)
                showPlaylistDialog.makeAbsent()
            },
            elevation = 3.dp,
        )
    }
}
