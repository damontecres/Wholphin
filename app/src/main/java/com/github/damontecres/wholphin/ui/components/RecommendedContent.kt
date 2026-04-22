package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.detail.music.addToQueue
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.HomePageContent
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.MediaType
import java.util.UUID

/**
 * Abstract [ViewModel] for the "Recommended" tab for a library
 */
abstract class RecommendedViewModel(
    @param:ApplicationContext val context: Context,
    val api: ApiClient,
    val serverRepository: ServerRepository,
    val navigationManager: NavigationManager,
    val favoriteWatchManager: FavoriteWatchManager,
    val mediaReportService: MediaReportService,
    private val musicService: MusicService,
    private val backdropService: BackdropService,
    private val mediaManagementService: MediaManagementService,
) : ViewModel() {
    abstract fun init()

    abstract val rows: MutableStateFlow<List<HomeRowLoadingState>>

    val loading = MutableLiveData<LoadingState>(LoadingState.Loading)

    fun refreshItem(
        position: RowColumn,
        itemId: UUID,
    ) {
        viewModelScope.launchIO {
            val row = rows.value.getOrNull(position.row)
            if (row is HomeRowLoadingState.Success) {
                (row.items as? ApiRequestPager<*>)?.refreshItem(position.column, itemId)
            }
        }
    }

    fun setWatched(
        position: RowColumn,
        itemId: UUID,
        watched: Boolean,
    ) {
        viewModelScope.launchIO {
            favoriteWatchManager.setWatched(itemId, watched)
            refreshItem(position, itemId)
        }
    }

    fun setFavorite(
        position: RowColumn,
        itemId: UUID,
        watched: Boolean,
    ) {
        viewModelScope.launchIO {
            favoriteWatchManager.setFavorite(itemId, watched)
            refreshItem(position, itemId)
        }
    }

    fun updateBackdrop(item: BaseItem) {
        viewModelScope.launchIO {
            backdropService.submit(item)
        }
    }

    abstract fun update(
        @StringRes title: Int,
        row: HomeRowLoadingState,
    ): HomeRowLoadingState

    fun update(
        @StringRes title: Int,
        viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
        block: suspend () -> List<BaseItem>,
    ): Deferred<HomeRowLoadingState> =
        viewModelScope.async(Dispatchers.IO) {
            val titleStr = context.getString(title)
            val row =
                try {
                    HomeRowLoadingState.Success(titleStr, block.invoke(), viewOptions)
                } catch (ex: Exception) {
                    HomeRowLoadingState.Error(titleStr, null, ex)
                }
            update(title, row)
        }

    fun deleteItem(
        position: RowColumn,
        item: BaseItem,
    ) {
        deleteItem(context, mediaManagementService, item) {
            viewModelScope.launchDefault {
                val row = rows.value.getOrNull(position.row)
                if (row is HomeRowLoadingState.Success) {
                    (row.items as? ApiRequestPager<*>)?.refreshPagesAfter(position.column)
                }
            }
        }
    }

    fun canDelete(
        item: BaseItem,
        appPreferences: AppPreferences,
    ): Boolean = mediaManagementService.canDelete(item, appPreferences)

    fun addToQueue(
        item: BaseItem,
        index: Int,
    ) = addToQueue(api, musicService, item, index)
}

@Composable
fun RecommendedContent(
    preferences: UserPreferences,
    viewModel: RecommendedViewModel,
    modifier: Modifier = Modifier,
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
    onFocusPosition: ((RowColumn) -> Unit)? = null,
) {
    var showContextMenu by remember { mutableStateOf<PositionContextMenu?>(null) }
    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    var showDeleteDialog by remember { mutableStateOf<RowColumnItem?>(null) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)

    OneTimeLaunchedEffect {
        viewModel.init()
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    val rows by viewModel.rows.collectAsState()

    when (val state = loading) {
        is LoadingState.Error -> {
            ErrorMessage(state, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            var position by rememberPosition()
            HomePageContent(
                homeRows = rows,
                position = position,
                onClickItem = { _, item ->
                    viewModel.navigationManager.navigateTo(item.destination())
                },
                onLongClickItem = { position, item ->
                    showContextMenu =
                        PositionContextMenu(
                            position,
                            ContextMenu.ForBaseItem(
                                fromLongClick = true,
                                item = item,
                                chosenStreams = null,
                                showGoTo = true,
                                showStreamChoices = false,
                                canDelete = viewModel.canDelete(item, preferences.appPreferences),
                                canRemoveContinueWatching = false,
                                canRemoveNextUp = false,
                            ),
                        )
                },
                onClickPlay = { _, item ->
                    viewModel.navigationManager.navigateTo(Destination.Playback(item))
                },
                onFocusPosition = {
                    position = it
                    val nonEmptyRowBefore =
                        rows
                            .subList(0, it.row)
                            .count {
                                it is HomeRowLoadingState.Success && it.items.isEmpty()
                            }
                    onFocusPosition?.invoke(
                        RowColumn(
                            it.row - nonEmptyRowBefore,
                            it.column,
                        ),
                    )
                },
                showClock = preferences.appPreferences.interfacePreferences.showClock,
                onUpdateBackdrop = viewModel::updateBackdrop,
                showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                modifier = modifier,
            )
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
    showContextMenu?.let { (position, contextMenu) ->
        ContextMenuDialog(
            onDismissRequest = { showContextMenu = null },
            getMediaSource = null,
            contextMenu = contextMenu,
            preferredSubtitleLanguage = null,
            actions =
                ContextMenuActions(
                    navigateTo = viewModel.navigationManager::navigateTo,
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
                    onClickDelete = { showDeleteDialog = RowColumnItem(position, it) },
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
                ),
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
    showDeleteDialog?.let { (position, item) ->
        ConfirmDeleteDialog(
            itemTitle = listOfNotNull(item.title, item.subtitle).joinToString(" - "),
            onCancel = { showDeleteDialog = null },
            onConfirm = {
                viewModel.deleteItem(position, item)
                showDeleteDialog = null
            },
        )
    }
}

data class RowColumnItem(
    val position: RowColumn,
    val item: BaseItem,
)

data class PositionContextMenu(
    val position: RowColumn,
    val contextMenu: ContextMenu,
)
