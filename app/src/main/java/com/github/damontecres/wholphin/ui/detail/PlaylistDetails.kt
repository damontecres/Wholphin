package com.github.damontecres.wholphin.ui.detail

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.LibraryDisplayInfoDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.filter.DefaultPlaylistItemsOptions
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.data.model.LibraryDisplayInfo
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.FilterOptionCache
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.MusicServiceState
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PlaylistCreator
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.cards.ItemCardImage
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import com.github.damontecres.wholphin.ui.components.FilterByButton
import com.github.damontecres.wholphin.ui.components.GridTitle
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.MusicContextActions
import com.github.damontecres.wholphin.ui.components.Optional
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.SortByButton
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.BoxSetSortOptions
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.detail.music.MusicQueueMarker
import com.github.damontecres.wholphin.ui.detail.music.MusicViewModel
import com.github.damontecres.wholphin.ui.enableMarquee
import com.github.damontecres.wholphin.ui.equalsNotNull
import com.github.damontecres.wholphin.ui.formatDateTime
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.formatTime
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.settings.MoveDirection
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.roundSeconds
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.LocalClock
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration

@HiltViewModel(assistedFactory = PlaylistViewModel.Factory::class)
class PlaylistViewModel
    @AssistedInject
    constructor(
        @ApplicationContext context: Context,
        api: ApiClient,
        navigationManager: NavigationManager,
        musicService: MusicService,
        mediaManagementService: MediaManagementService,
        private val backdropService: BackdropService,
        private val serverRepository: ServerRepository,
        private val libraryDisplayInfoDao: LibraryDisplayInfoDao,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val mediaReportService: MediaReportService,
        private val filterOptionCache: FilterOptionCache,
        private val playlistCreator: PlaylistCreator,
        @Assisted itemId: UUID,
    ) : MusicViewModel(itemId, context, api, musicService, navigationManager, mediaManagementService) {
        @AssistedFactory
        interface Factory {
            fun create(itemId: UUID): PlaylistViewModel
        }

        val state = MutableStateFlow(PlaylistDetailsState())
        val musicState = musicService.state

        init {
            init()
        }

        override fun init() {
            state.update { it.copy(loading = LoadingState.Loading) }
            viewModelScope.launchDefault {
                try {
                    val playlist =
                        api.userLibraryApi
                            .getItem(itemId)
                            .content
                            .let { BaseItem(it, false) }
                    val user = serverRepository.currentUser
                    val canEdit =
                        user?.let { user ->
                            try {
                                val permission by api.playlistsApi.getPlaylistUser(itemId, user.id)
                                permission.canEdit
                            } catch (ex: CancellationException) {
                                throw ex
                            } catch (ex: InvalidStatusException) {
                                if (ex.status == 404) {
                                    // Server will return this if no permission exists
                                    Timber.w(
                                        "User doesn't have permission to edit playlist %s",
                                        itemId,
                                    )
                                } else {
                                    Timber.e(
                                        ex,
                                        "Error checking user permission for playlist %s",
                                        itemId,
                                    )
                                }
                                false
                            } catch (ex: Exception) {
                                Timber.e(
                                    ex,
                                    "Error checking user permission for playlist %s",
                                    itemId,
                                )
                                false
                            }
                        } ?: false
                    state.update {
                        it.copy(
                            playlist = playlist,
                            canEdit = canEdit,
                        )
                    }

                    val libraryDisplayInfo =
                        user?.let { user ->
                            libraryDisplayInfoDao.getItem(user, itemId)
                        }
                    val filter = libraryDisplayInfo?.filter ?: GetItemsFilter()
                    val sortAndDirection =
                        libraryDisplayInfo?.sortAndDirection ?: SortAndDirection(
                            ItemSortBy.DEFAULT,
                            SortOrder.ASCENDING,
                        )
                    loadItems(filter, sortAndDirection).join()
                    determineMediaType()
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching playlist %s", itemId)
                    state.update { it.copy(loading = LoadingState.Error(ex)) }
                }
            }
        }

        fun loadItems(
            filter: GetItemsFilter,
            sortAndDirection: SortAndDirection,
        ) = viewModelScope.launchIO {
            backdropService.clearBackdrop()
            state.update {
                it.copy(
                    loading = LoadingState.Loading,
                    filterAndSort = FilterAndSort(filter, sortAndDirection),
                )
            }

            serverRepository.currentUser?.let { user ->
                viewModelScope.launchIO {
                    val libraryDisplayInfo =
                        libraryDisplayInfoDao.getItem(user, itemId)?.copy(
                            filter = filter,
                            sort = sortAndDirection.sort,
                            direction = sortAndDirection.direction,
                        )
                            ?: LibraryDisplayInfo(
                                userId = user.rowId,
                                itemId = itemId.toServerString(),
                                sort = sortAndDirection.sort,
                                direction = sortAndDirection.direction,
                                filter = filter,
                                viewOptions = null,
                            )
                    libraryDisplayInfoDao.saveItem(libraryDisplayInfo)
                }

                val request =
                    filter.applyTo(
                        GetItemsRequest(
                            parentId = itemId,
                            userId = user.id,
                            fields = SlimItemFields,
                            sortBy = listOf(sortAndDirection.sort),
                            sortOrder = listOf(sortAndDirection.direction),
                        ),
                    )
                try {
                    val pager =
                        ApiRequestPager(
                            api,
                            request,
                            GetItemsRequestHandler,
                            viewModelScope,
                        ).init()
                    state.update {
                        it.copy(
                            items = pager,
                            loading = LoadingState.Success,
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching playlist %s", itemId)
                    state.update {
                        it.copy(
                            items = emptyList(),
                            loading = LoadingState.Error(ex),
                        )
                    }
                }
            }
        }

        /**
         * This method tries to determine the [MediaType] of a playlist
         *
         * In theory, the server will set the type, but sometimes it doesn't
         */
        private suspend fun determineMediaType() {
            // Use the type the server says
            var mediaType =
                state.value.playlist
                    ?.data
                    ?.mediaType ?: MediaType.UNKNOWN
            mediaType =
                if (mediaType == MediaType.UNKNOWN) {
                    // Otherwise, if a most of the list is one type, we can assume that type
                    val pager = (state.value.items as? ApiRequestPager<*>)
                    if (pager != null && pager.size <= 50) {
                        val types =
                            (0..<50.coerceAtMost(pager.size)).groupBy { index ->
                                val pagerItem = pager.getBlocking(index)
                                when (pagerItem?.type) {
                                    BaseItemKind.AUDIO -> MediaType.AUDIO

                                    BaseItemKind.VIDEO,
                                    BaseItemKind.EPISODE,
                                    BaseItemKind.MOVIE,
                                    BaseItemKind.BOX_SET,
                                    -> MediaType.VIDEO

                                    else -> MediaType.UNKNOWN
                                }
                            }
                        if (types.keys.size == 1) {
                            types.keys.first()
                        } else {
                            MediaType.UNKNOWN
                        }
                    } else {
                        MediaType.UNKNOWN
                    }
                } else {
                    mediaType
                }
            Timber.d("mediaType=%s", mediaType)
            state.update {
                it.copy(mediaType = mediaType)
            }
        }

        suspend fun getFilterOptionValues(filterOption: ItemFilterBy<*>): List<FilterValueOption> =
            filterOptionCache.getFilterOptionValues(
                itemId,
                filterOption,
            )

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
        }

        fun sendMediaReport(itemId: UUID) {
            viewModelScope.launchDefault { mediaReportService.sendReportFor(itemId) }
        }

        fun removeFromPlaylist(
            index: Int,
            itemId: UUID,
        ) {
            viewModelScope.launchIO {
                try {
                    playlistCreator.removeFromServerPlaylist(
                        playlistId = this@PlaylistViewModel.itemId,
                        itemId = itemId,
                    )
                    (state.value.items as? ApiRequestPager<*>)?.refreshPagesAfter(index)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(
                        ex,
                        "Error removing %s from playlist %s",
                        itemId,
                        this@PlaylistViewModel.itemId,
                    )
                    showToast(context, "Error: ${ex.localizedMessage}")
                }
            }
        }

        fun onMoveItem(
            index: Int,
            itemId: UUID,
            direction: MoveDirection,
        ) {
            viewModelScope.launchIO {
                val newIndex = index + if (direction == MoveDirection.UP) -1 else 1
                api.playlistsApi.moveItem(
                    playlistId = this@PlaylistViewModel.itemId.toServerString(),
                    itemId = itemId.toServerString(),
                    newIndex = newIndex,
                )
                (state.value.items as? ApiRequestPager<*>)?.refreshPagesAfter(index - 1)
            }
        }
    }

@Immutable
data class FilterAndSort(
    val filter: GetItemsFilter,
    val sortAndDirection: SortAndDirection,
)

data class PlaylistDetailsState(
    val playlist: BaseItem? = null,
    val mediaType: MediaType = MediaType.UNKNOWN,
    val items: List<BaseItem?> = emptyList(),
    val filterAndSort: FilterAndSort =
        FilterAndSort(
            filter = GetItemsFilter(),
            sortAndDirection =
                SortAndDirection(
                    ItemSortBy.DEFAULT,
                    SortOrder.ASCENDING,
                ),
        ),
    val loading: LoadingState = LoadingState.Pending,
    val canEdit: Boolean = false,
)

@Composable
fun PlaylistDetails(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel =
        hiltViewModel<PlaylistViewModel, PlaylistViewModel.Factory>(
            creationCallback = { it.create(destination.itemId) },
        ),
    addToPlaylistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val musicState by viewModel.musicState.collectAsState()

    var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    var showConfirmTypeDialog by remember { mutableStateOf<Triple<Int, BaseItem, Boolean>?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val addPlaylistState by addToPlaylistViewModel.playlistState.collectAsState()

    fun play(
        index: Int,
        item: BaseItem,
        shuffle: Boolean,
        mediaTypeOverride: MediaType? = null,
    ) {
        when (mediaTypeOverride ?: state.mediaType) {
            MediaType.VIDEO -> {
                viewModel.navigationManager.navigateTo(
                    Destination.PlaybackList(
                        itemId = destination.itemId,
                        startIndex = index,
                        shuffle = shuffle,
                        filter = state.filterAndSort.filter,
                        sortAndDirection = state.filterAndSort.sortAndDirection,
                    ),
                )
            }

            MediaType.AUDIO -> {
                viewModel.play(item, index, shuffle)
            }

            else -> {
                showConfirmTypeDialog = Triple(index, item, shuffle)
            }
        }
    }
    val musicContextActions =
        remember {
            MusicContextActions(
                navigateTo = { viewModel.navigationManager.navigateTo(it) },
                onClickPlay = { index, item -> play(index, item, false, MediaType.AUDIO) },
                onClickPlayNext = { index, item -> viewModel.playNext(item) },
                onClickAddToQueue = { item -> viewModel.addToQueue(item, Int.MAX_VALUE) },
                onClickFavorite = { id, favorite -> viewModel.setFavorite(id, favorite) },
                onClickAddPlaylist = { itemId ->
                    addToPlaylistViewModel.loadPlaylists()
                    showPlaylistDialog.makePresent(itemId)
                },
                onClickRemoveFromQueue = { _, _ -> },
                onDeleteItem = viewModel::deleteItem,
                onRemoveFromPlaylist = viewModel::removeFromPlaylist,
            )
        }
    val contextActions =
        remember {
            ContextMenuActions(
                navigateTo = { viewModel.navigationManager.navigateTo(it) },
                onClickWatch = { id, watched -> viewModel.setWatched(id, watched) },
                onClickFavorite = { id, favorite -> viewModel.setFavorite(id, favorite) },
                onClickAddPlaylist = { itemId ->
                    addToPlaylistViewModel.loadPlaylists()
                    showPlaylistDialog.makePresent(itemId)
                },
                onSendMediaInfo = viewModel::sendMediaReport,
                onDeleteItem = viewModel::deleteItem,
                onClickAddToQueue = { viewModel.addToQueue(it, 0) },
                onShowOverview = {},
                onChooseVersion = { _, _ -> },
                onChooseTracks = {},
                onClearChosenStreams = {},
                onClickRemoveFromNextUp = {},
                onRemoveFromPlaylist = viewModel::removeFromPlaylist,
            )
        }

    PlaylistDetailsContent(
        loadingState = state.loading,
        playlist = state.playlist,
        items = state.items,
        musicState = musicState,
        onChangeBackdrop = viewModel::updateBackdrop,
        onClickIndex = { index, item ->
            play(index, item, false)
        },
        onClickPlay = { shuffle ->
            state.playlist?.let {
                play(0, it, shuffle)
            }
        },
        onShowContextMenu = { index, item, fromLongClick ->
            showContextMenu =
                if (item.type == BaseItemKind.AUDIO) {
                    ContextMenu.ForMusic(
                        fromLongClick = fromLongClick,
                        item = item,
                        index = index,
                        canDelete = viewModel.canDelete(item, preferences.appPreferences),
                        canRemoveFromQueue = false,
                        actions = musicContextActions,
                        showRemoveFromPlaylist = state.canEdit,
                    )
                } else {
                    ContextMenu.ForBaseItem(
                        fromLongClick = fromLongClick,
                        item = item,
                        index = index,
                        chosenStreams = null,
                        showGoTo = true,
                        showStreamChoices = false,
                        canDelete = viewModel.canDelete(item, preferences.appPreferences),
                        canRemoveContinueWatching = false,
                        canRemoveNextUp = false,
                        actions = contextActions,
                        showRemoveFromPlaylist = state.canEdit,
                    )
                }
        },
        filterAndSort = state.filterAndSort,
        onFilterAndSortChange = viewModel::loadItems,
        getPossibleFilterValues = viewModel::getFilterOptionValues,
        canEdit = state.canEdit,
        onMoveItem = viewModel::onMoveItem,
        modifier = modifier,
    )
    showContextMenu?.let { contextMenu ->
        ContextMenuDialog(
            onDismissRequest = { showContextMenu = null },
            getMediaSource = null,
            contextMenu = contextMenu,
            preferredSubtitleLanguage = null,
        )
    }
    showConfirmTypeDialog?.let { (index, item, shuffle) ->
        ConfirmMediaTypeDialog(
            onConfirm = { mediaType -> play(index, item, shuffle, mediaType) },
            onCancel = { showConfirmTypeDialog = null },
        )
    }
    showPlaylistDialog.compose { itemId ->
        PlaylistDialog(
            title = stringResource(R.string.add_to_playlist),
            state = addPlaylistState,
            onDismissRequest = { showPlaylistDialog.makeAbsent() },
            onClick = {
                addToPlaylistViewModel.addToPlaylist(it.id, itemId)
                showPlaylistDialog.makeAbsent()
            },
            createEnabled = true,
            onCreatePlaylist = {
                addToPlaylistViewModel.createPlaylistAndAddItem(it, itemId)
                showPlaylistDialog.makeAbsent()
            },
            onSearch = addToPlaylistViewModel::loadPlaylists,
            elevation = 3.dp,
        )
    }
}

@Composable
fun PlaylistDetailsContent(
    playlist: BaseItem?,
    items: List<BaseItem?>,
    musicState: MusicServiceState,
    onClickIndex: (Int, BaseItem) -> Unit,
    onShowContextMenu: (Int, BaseItem, Boolean) -> Unit,
    onClickPlay: (shuffle: Boolean) -> Unit,
    onChangeBackdrop: (BaseItem) -> Unit,
    onMoveItem: (Int, UUID, MoveDirection) -> Unit,
    filterAndSort: FilterAndSort,
    onFilterAndSortChange: (GetItemsFilter, SortAndDirection) -> Unit,
    getPossibleFilterValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
    loadingState: LoadingState,
    canEdit: Boolean,
    modifier: Modifier = Modifier,
) {
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusedIndex by remember { mutableIntStateOf(savedIndex) }
    val focusedItem = items.getOrNull(focusedIndex)
    LaunchedEffect(focusedItem) {
        focusedItem?.let(onChangeBackdrop)
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(loadingState) {
        if (loadingState is LoadingState.Success || loadingState is LoadingState.Error) {
            focusRequester.tryRequestFocus()
        }
    }

    val playButtonFocusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .fillMaxSize(),
        ) {
            val title =
                if (loadingState is LoadingState.Success) {
                    playlist?.name ?: stringResource(R.string.playlist)
                } else {
                    ""
                }
            GridTitle(title)
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier =
                    Modifier
                        .fillMaxWidth(),
            ) {
                PlaylistDetailsHeader(
                    focusedItem = focusedItem,
                    onClickPlay = onClickPlay,
                    playButtonFocusRequester = playButtonFocusRequester,
                    focusRequester = if (items.isEmpty()) focusRequester else remember { FocusRequester() },
                    filterAndSort = filterAndSort,
                    onFilterAndSortChange = onFilterAndSortChange,
                    getPossibleFilterValues = getPossibleFilterValues,
                    modifier =
                        Modifier
                            .padding(top = 80.dp)
                            .fillMaxWidth(.25f),
                )
                PlaylistItems(
                    loadingState = loadingState,
                    items = items,
                    musicState = musicState,
                    playButtonFocusRequester = playButtonFocusRequester,
                    onFocusItem = { index, item ->
                        focusedIndex = index
                    },
                    onClickItem = { index, item ->
                        savedIndex = index
                        item?.let { onClickIndex.invoke(index, item) }
                    },
                    onShowContextMenu = { index, item, fromLongClick ->
                        savedIndex = index
                        item?.let { onShowContextMenu.invoke(index, item, fromLongClick) }
                    },
                    canMove = canEdit && filterAndSort.sortAndDirection.sort == ItemSortBy.DEFAULT,
                    onMoveItem = onMoveItem,
                    modifier =
                        Modifier
                            .padding(start = 16.dp)
                            .weight(1f)
                            .focusRequester(focusRequester),
                )
            }
        }
    }
}

@Composable
fun PlaylistDetailsHeader(
    focusedItem: BaseItem?,
    onClickPlay: (shuffle: Boolean) -> Unit,
    playButtonFocusRequester: FocusRequester,
    focusRequester: FocusRequester,
    filterAndSort: FilterAndSort,
    onFilterAndSortChange: (GetItemsFilter, SortAndDirection) -> Unit,
    getPossibleFilterValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier,
        ) {
            ExpandablePlayButton(
                title = R.string.play,
                resume = Duration.ZERO,
                icon = Icons.Default.PlayArrow,
                onClick = { onClickPlay.invoke(false) },
                modifier = Modifier.focusRequester(playButtonFocusRequester),
            )
            ExpandableFaButton(
                title = R.string.shuffle,
                iconStringRes = R.string.fa_shuffle,
                onClick = { onClickPlay.invoke(true) },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier,
        ) {
            FilterByButton(
                filterOptions = DefaultPlaylistItemsOptions,
                current = filterAndSort.filter,
                onFilterChange = {
                    onFilterAndSortChange.invoke(
                        it,
                        filterAndSort.sortAndDirection,
                    )
                },
                getPossibleValues = getPossibleFilterValues,
                modifier = Modifier.focusRequester(focusRequester),
            )
            SortByButton(
                sortOptions = BoxSetSortOptions,
                current = filterAndSort.sortAndDirection,
                onSortChange = { onFilterAndSortChange.invoke(filterAndSort.filter, it) },
            )
        }
        Text(
            text = focusedItem?.title ?: "",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = focusedItem?.subtitle ?: "",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        if (focusedItem?.type == BaseItemKind.EPISODE && focusedItem.data.premiereDate != null) {
            Text(
                text = formatDateTime(focusedItem.data.premiereDate!!),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        OverviewText(
            overview = focusedItem?.data?.overview ?: "",
            maxLines = 10,
            onClick = {},
            enabled = false,
        )
    }
}

@Composable
fun PlaylistItems(
    loadingState: LoadingState,
    items: List<BaseItem?>,
    musicState: MusicServiceState,
    playButtonFocusRequester: FocusRequester,
    canMove: Boolean,
    onMoveItem: (Int, UUID, MoveDirection) -> Unit,
    onFocusItem: (Int, BaseItem?) -> Unit,
    onClickItem: (Int, BaseItem?) -> Unit,
    onShowContextMenu: (Int, BaseItem?, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    when (loadingState) {
        is LoadingState.Error -> {
            ErrorMessage(loadingState, modifier)
        }

        LoadingState.Pending, LoadingState.Loading -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            if (items.isNotEmpty()) {
                LazyColumn(
                    contentPadding = PaddingValues(8.dp),
                    modifier =
                        modifier
                            .padding(bottom = 32.dp)
                            .fillMaxHeight()
                            .background(
                                MaterialTheme.colorScheme
                                    .surfaceColorAtElevation(1.dp)
                                    .copy(alpha = .75f),
                                shape = RoundedCornerShape(16.dp),
                            ).focusProperties {
                                left = playButtonFocusRequester
                                previous = playButtonFocusRequester
                            }.focusGroup()
                            .focusRestorer(),
                ) {
                    itemsIndexed(items) { index, item ->
                        PlaylistItem(
                            item = item,
                            index = index,
                            onClick = {
                                onClickItem.invoke(index, item)
                            },
                            onLongClick = {
                                onShowContextMenu.invoke(index, item, true)
                            },
                            isPlaying =
                                equalsNotNull(
                                    musicState.currentItemId,
                                    item?.id,
                                ),
                            isQueued = item?.id in musicState.queuedIds,
                            canMove = canMove,
                            moveUpAllowed = index > 0,
                            moveDownAllowed = index < items.lastIndex,
                            onClickMove = { direction ->
                                item?.let { onMoveItem.invoke(index, item.id, direction) }
                                when (direction) {
                                    MoveDirection.UP -> focusManager.moveFocus(FocusDirection.Up)
                                    MoveDirection.DOWN -> focusManager.moveFocus(FocusDirection.Down)
                                }
                            },
                            onClickMore = { onShowContextMenu.invoke(index, item, false) },
                            modifier =
                                Modifier
                                    .animateItem()
                                    .ifElse(
                                        item?.type != BaseItemKind.AUDIO,
                                        Modifier.height(80.dp),
                                    ).onFocusChanged {
                                        if (it.hasFocus) {
                                            onFocusItem(index, item)
                                        }
                                    },
//                                    .focusProperties {
//                                        left = playButtonFocusRequester
//                                        previous = playButtonFocusRequester
//                                    },
                        )
                    }
                }
            } else {
                LaunchedEffect(Unit) {
                    playButtonFocusRequester.tryRequestFocus()
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.no_results),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistItem(
    item: BaseItem?,
    index: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    canMove: Boolean,
    moveUpAllowed: Boolean,
    moveDownAllowed: Boolean,
    onClickMove: (MoveDirection) -> Unit,
    onClickMore: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    isPlaying: Boolean = false,
    isQueued: Boolean = false,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val imageWidth = 160.dp
    val density = LocalDensity.current
    val imageWidthPx = remember(imageWidth, density) { with(density) { imageWidth.roundToPx() } }
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp, max = 88.dp),
    ) {
        ListItem(
            selected = false,
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            headlineContent = {
                Text(
                    text = item?.title ?: "",
                    modifier = Modifier.enableMarquee(focused),
                )
            },
            supportingContent = {
                Text(
                    text = item?.subtitle ?: "",
                    modifier = Modifier.enableMarquee(focused),
                )
            },
            trailingContent = {
                val duration =
                    when (item?.type) {
                        BaseItemKind.AUDIO -> {
                            item.data.runTimeTicks
                                ?.ticks
                                ?.roundSeconds
                        }

                        else -> {
                            item
                                ?.data
                                ?.runTimeTicks
                                ?.ticks
                                ?.roundMinutes
                        }
                    }
                duration?.let { duration ->
                    val now by LocalClock.current.now
                    val context = LocalContext.current
                    val endTimeStr =
                        remember(item, now, context) {
                            val endTime = now.toLocalTime().plusSeconds(duration.inWholeSeconds)
                            formatTime(context, endTime)
                        }
                    val resources = LocalResources.current
                    val durationText =
                        remember(resources, duration) { resources.formatDuration(duration) }
                    Column {
                        Text(
                            text = durationText,
                        )
                        if (item?.type != BaseItemKind.AUDIO) {
                            Text(
                                text = stringResource(R.string.ends_at, endTimeStr),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            leadingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (item?.type == BaseItemKind.AUDIO) {
                        MusicQueueMarker(
                            isPlaying = isPlaying,
                            isQueued = isQueued,
                        )
                    } else {
                        ItemCardImage(
                            item = item,
                            name = item?.name,
                            showOverlay = true,
                            favorite = item?.data?.userData?.isFavorite ?: false,
                            watched = item?.data?.userData?.played ?: false,
                            unwatchedCount = item?.data?.userData?.unplayedItemCount ?: -1,
                            watchedPercent = 0.0,
                            numberOfVersions = item?.data?.mediaSourceCount ?: 0,
                            modifier = Modifier.width(imageWidth),
                            useFallbackText = false,
                            fillWidth = imageWidthPx,
                        )
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
        val contentHeight = 24.dp
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.wrapContentWidth(),
        ) {
            if (canMove) {
                Button(
                    onClick = { onClickMove.invoke(MoveDirection.UP) },
                    enabled = moveUpAllowed,
                    contentHeight = contentHeight,
                ) {
                    Text(
                        text = stringResource(R.string.fa_caret_up),
                        fontFamily = FontAwesome,
                    )
                }
                Button(
                    onClick = { onClickMove.invoke(MoveDirection.DOWN) },
                    enabled = moveDownAllowed,
                    contentHeight = contentHeight,
                ) {
                    Text(
                        text = stringResource(R.string.fa_caret_down),
                        fontFamily = FontAwesome,
                    )
                }
            }
            Button(
                onClick = onClickMore,
                enabled = true,
                contentHeight = contentHeight,
            ) {
                Text(
                    text = stringResource(R.string.fa_ellipsis_vertical),
                    fontFamily = FontAwesome,
                )
            }
        }
    }
}

@Composable
fun ConfirmMediaTypeDialog(
    onConfirm: (MediaType) -> Unit,
    onCancel: () -> Unit,
) {
    BasicDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(),
        elevation = 3.dp,
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.wrapContentSize(),
        ) {
            item {
                Text(
                    text = stringResource(R.string.play_as_type),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillParentMaxWidth(),
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(
                        stringRes = R.string.audio,
                        onClick = { onConfirm.invoke(MediaType.AUDIO) },
                    )
                    TextButton(
                        stringRes = R.string.video,
                        onClick = { onConfirm.invoke(MediaType.VIDEO) },
                    )
                }
            }
        }
    }
}
