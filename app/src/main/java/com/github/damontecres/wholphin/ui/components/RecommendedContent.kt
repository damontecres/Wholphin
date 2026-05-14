package com.github.damontecres.wholphin.ui.components

import android.content.Context
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
import com.github.damontecres.wholphin.services.SuggestionService
import com.github.damontecres.wholphin.services.SuggestionsResource
import com.github.damontecres.wholphin.services.UserPreferencesService
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
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.RequestHandler
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = RecommendedViewModel.Factory::class)
class RecommendedViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
        private val userPreferencesService: UserPreferencesService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val musicService: MusicService,
        private val backdropService: BackdropService,
        private val mediaManagementService: MediaManagementService,
        private val suggestionService: SuggestionService,
        val mediaReportService: MediaReportService,
        @Assisted private val parentId: UUID,
        @Assisted private val suggestionsType: BaseItemKind,
        @Assisted private val recommendedRows: List<RecommendedRow<*>>,
        @Assisted private val viewOptions: HomeRowViewOptions,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                parentId: UUID,
                suggestionsType: BaseItemKind,
                recommendedRows: List<RecommendedRow<*>>,
                viewOptions: HomeRowViewOptions,
            ): RecommendedViewModel
        }

        private val _state = MutableStateFlow(RecommendedState())
        val state: StateFlow<RecommendedState> = _state

        fun init() {
            viewModelScope.launchDefault {
                val limit =
                    userPreferencesService.flow
                        .first()
                        .appPreferences.homePagePreferences.maxItemsPerRow
                _state.update {
                    it.copy(
                        loading = LoadingState.Loading,
                        rows =
                            recommendedRows.map { HomeRowLoadingState.Loading(context.getString(it.title)) } +
                                listOf(HomeRowLoadingState.Loading(context.getString(R.string.suggestions))),
                    )
                }
                val jobs =
                    recommendedRows.mapIndexed { index, row ->
                        val title = context.getString(row.title)
                        viewModelScope.launchIO {
                            val result =
                                try {
                                    HomeRowLoadingState.Success(
                                        title,
                                        execute(row, limit),
                                        viewOptions,
                                    )
                                } catch (ex: Exception) {
                                    Timber.e(ex, "Exception fetching %s", title)
                                    HomeRowLoadingState.Error(title, null, ex)
                                }
                            _state.update {
                                it.copy(
                                    rows =
                                        it.rows.toMutableList().apply {
                                            set(index, result)
                                        },
                                )
                            }
                        }
                    }
                fetchSuggestions()
                jobs.forEachIndexed { index, job ->
                    job.join()
                    val row = state.value.rows[index]
                    if (row is HomeRowLoadingState.Success && row.items.isNotEmpty()) {
                        // Report loading success once the first non-empty row is ready
                        _state.update { it.copy(loading = LoadingState.Success) }
                    }
                }
            }
        }

        private suspend fun <T> execute(
            row: RecommendedRow<T>,
            limit: Int?,
        ): List<BaseItem?> =
            if (limit != null) {
                val request = row.handler.prepare(row.request, 0, limit, false)
                row.handler
                    .execute(api, request)
                    .toBaseItems(api, true)
            } else {
                ApiRequestPager(
                    api,
                    row.request,
                    row.handler,
                    viewModelScope,
                    useSeriesForPrimary = true,
                ).init()
            }

        private fun fetchSuggestions() {
            viewModelScope.launch(Dispatchers.IO) {
                val title = context.getString(R.string.suggestions)
                try {
                    suggestionService
                        .getSuggestionsFlow(parentId, suggestionsType)
                        .collect { resource ->
                            val result =
                                when (resource) {
                                    is SuggestionsResource.Loading -> {
                                        HomeRowLoadingState.Loading(title)
                                    }

                                    is SuggestionsResource.Success -> {
                                        HomeRowLoadingState.Success(
                                            title,
                                            resource.items,
                                            viewOptions,
                                        )
                                    }

                                    is SuggestionsResource.Empty -> {
                                        HomeRowLoadingState.Success(
                                            title,
                                            emptyList(),
                                            viewOptions,
                                        )
                                    }
                                }
                            _state.update {
                                it.copy(
                                    rows =
                                        it.rows.toMutableList().apply {
                                            set(lastIndex, result)
                                        },
                                )
                            }
                        }
                } catch (_: CancellationException) {
                    // no-op
                } catch (ex: Exception) {
                    Timber.e(ex, "Failed to fetch suggestions")
                    _state.update {
                        it.copy(
                            rows =
                                it.rows.toMutableList().apply {
                                    set(lastIndex, HomeRowLoadingState.Error(title, null, ex))
                                },
                        )
                    }
                }
            }
        }

        fun refreshItem(
            position: RowColumn,
            itemId: UUID,
        ) {
            viewModelScope.launchIO {
                val row = state.value.rows.getOrNull(position.row)
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

        fun deleteItem(
            position: RowColumn,
            item: BaseItem,
        ) {
            deleteItem(context, mediaManagementService, item) {
                viewModelScope.launchDefault {
                    val row = state.value.rows.getOrNull(position.row)
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

        fun onClickViewMore(
            position: RowColumn,
            row: HomeRowLoadingState.Success,
        ) {
            val recommendedRow = recommendedRows[position.row]
            navigationManager.navigateTo(
                Destination.ItemGrid(
                    title = row.title,
                    titleRes = recommendedRow.title,
                    request = recommendedRow.request as GetItemsRequest, // TODO
                    initialPosition = row.items.size,
                    viewOptions =
                        ViewOptions(
                            aspectRatio = viewOptions.aspectRatio,
                        ),
                ),
            )
        }
    }

data class RecommendedState(
    val loading: LoadingState = LoadingState.Pending,
    val rows: List<HomeRowLoadingState> = emptyList(),
)

data class RecommendedRow<T>(
    val title: Int,
    val handler: RequestHandler<T>,
    val request: T,
)

@Composable
fun RecommendedContent(
    preferences: UserPreferences,
    viewModel: RecommendedViewModel,
    modifier: Modifier = Modifier,
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
    onFocusPosition: ((RowColumn) -> Unit)? = null,
) {
    var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)

    OneTimeLaunchedEffect {
        viewModel.init()
    }
    val state by viewModel.state.collectAsState()

    when (val st = state.loading) {
        is LoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            var position by rememberPosition()
            val contextActions =
                remember {
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

            HomePageContent(
                homeRows = state.rows,
                position = position,
                onClickItem = { _, item ->
                    viewModel.navigationManager.navigateTo(item.destination())
                },
                onLongClickItem = { position, item ->
                    showContextMenu =
                        ContextMenu.ForBaseItem(
                            fromLongClick = true,
                            item = item,
                            chosenStreams = null,
                            showGoTo = true,
                            showStreamChoices = false,
                            canDelete = viewModel.canDelete(item, preferences.appPreferences),
                            canRemoveContinueWatching = false,
                            canRemoveNextUp = false,
                            actions = contextActions,
                        )
                },
                onClickPlay = { _, item ->
                    viewModel.navigationManager.navigateTo(Destination.Playback(item))
                },
                onFocusPosition = {
                    position = it
                    val nonEmptyRowBefore =
                        state.rows
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
                showViewMore = true,
                onClickViewMore = viewModel::onClickViewMore,
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

data class RowColumnItem(
    val position: RowColumn,
    val item: BaseItem,
)
