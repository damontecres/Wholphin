package com.github.damontecres.wholphin.ui.detail

import android.content.Context
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.cards.FavoriteIndicator
import com.github.damontecres.wholphin.ui.cards.WatchedIcon
import com.github.damontecres.wholphin.ui.collectLatestIn
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.components.CollectionFolderState
import com.github.damontecres.wholphin.ui.components.CollectionFolderViewContent
import com.github.damontecres.wholphin.ui.components.CollectionFolderViewModel
import com.github.damontecres.wholphin.ui.components.CollectionFolderViewScope
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.GridClickActions
import com.github.damontecres.wholphin.ui.components.ListItemWrapper
import com.github.damontecres.wholphin.ui.components.MusicContextActions
import com.github.damontecres.wholphin.ui.components.Optional
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.components.ViewOptionsType
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.PlaylistSortOptions
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.detail.music.MusicQueueMarker
import com.github.damontecres.wholphin.ui.detail.music.MusicViewModel
import com.github.damontecres.wholphin.ui.enableMarquee
import com.github.damontecres.wholphin.ui.equalsNotNull
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.formatTime
import com.github.damontecres.wholphin.ui.gt
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.settings.MoveDirection
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.roundSeconds
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.ui.util.LocalClock
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.successValue
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = PlaylistViewModel.Factory::class)
class PlaylistViewModel
    @AssistedInject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        @ApplicationContext context: Context,
        api: ApiClient,
        navigationManager: NavigationManager,
        musicService: MusicService,
        mediaManagementService: MediaManagementService,
        private val serverRepository: ServerRepository,
        @Assisted itemId: UUID,
        @Assisted private val collectionState: StateFlow<CollectionFolderState>,
    ) : MusicViewModel(itemId, context, api, musicService, navigationManager, mediaManagementService) {
        @AssistedFactory
        interface Factory {
            fun create(
                itemId: UUID,
                collectionState: StateFlow<CollectionFolderState>,
            ): PlaylistViewModel
        }

        private val _state = MutableStateFlow(PlaylistDetailsState())
        val state: StateFlow<PlaylistDetailsState> = _state

        val musicState = musicService.state

        var position: Int
            get() = savedStateHandle.get<Int>("position") ?: 0
            set(value) {
                savedStateHandle["position"] = value
            }

        init {
            init()
        }

        override fun init() {
            viewModelScope.launchDefault {
                try {
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

                    _state.update { it.copy(canEdit = canEdit) }
                    determineMediaType()
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching playlist info for %s", itemId)
//                    _state.update { it.copy(item = DataLoadingState.Error(ex)) }
                }
            }
        }

        /**
         * This method tries to determine the [MediaType] of a playlist
         *
         * In theory, the server will set the type, but sometimes it doesn't
         */
        private fun determineMediaType() {
            collectionState
                .map { Pair(it.item, it.items) }
                .distinctUntilChanged()
                .catch { ex -> Timber.e(ex, "Error in determineMediaType") }
                .collectLatestIn(viewModelScope) { (item, items) ->
                    var mediaType = item.successValue?.data?.mediaType ?: MediaType.UNKNOWN
                    mediaType =
                        if (mediaType == MediaType.UNKNOWN) {
                            // Otherwise, if a most of the list is one type, we can assume that type
                            val pager = (items.successValue as? ApiRequestPager<*>)
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
                    _state.update {
                        it.copy(mediaType = mediaType)
                    }
                }
        }

        fun removeFromPlaylist(
            index: Int,
            itemId: UUID,
        ) {
            viewModelScope.launchIO {
                try {
                    api.playlistsApi.removeItemFromPlaylist(
                        this@PlaylistViewModel.itemId.toServerString(),
                        listOf(itemId.toServerString()),
                    )
                    (collectionState.value.items.successValue as? ApiRequestPager<*>)
                        ?.refreshPagesAfter(index)
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
                (collectionState.value.items.successValue as? ApiRequestPager<*>)?.refreshPagesAfter(
                    index - 1,
                )
            }
        }
    }

data class PlaylistDetailsState(
    val mediaType: MediaType = MediaType.UNKNOWN,
    val canEdit: Boolean = false,
)

@Composable
fun PlaylistDetails(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    collectionViewModel: CollectionFolderViewModel =
        hiltViewModel<CollectionFolderViewModel, CollectionFolderViewModel.Factory>(
            creationCallback = {
                it.create(
                    itemId = destination.itemId.toServerString(),
                    initialSortAndDirection =
                        SortAndDirection(
                            ItemSortBy.DEFAULT,
                            SortOrder.ASCENDING,
                        ),
                    recursive = false,
                    collectionFilter = CollectionFolderFilter(),
                    useSeriesForPrimary = false,
                    defaultViewOptions = playlistDefaultViewOptions,
                )
            },
        ),
    viewModel: PlaylistViewModel =
        hiltViewModel<PlaylistViewModel, PlaylistViewModel.Factory>(
            creationCallback = { it.create(destination.itemId, collectionViewModel.state) },
        ),
    addToPlaylistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val state by collectionViewModel.state.collectAsState()
    val playlistState by viewModel.state.collectAsState()
    val musicState by viewModel.musicState.collectAsState()
    val focusManager = LocalFocusManager.current

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
        when (mediaTypeOverride ?: playlistState.mediaType) {
            MediaType.VIDEO -> {
                collectionViewModel.navigateTo(
                    Destination.PlaybackList(
                        itemId = destination.itemId,
                        startIndex = index,
                        shuffle = shuffle,
                        filter = state.filter,
                        sortAndDirection = state.sortAndDirection,
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

    fun showContextMenu(
        index: Int,
        item: BaseItem,
        fromLongClick: Boolean,
    ) {
        showContextMenu =
            if (item.type == BaseItemKind.AUDIO) {
                val musicContextActions =
                    MusicContextActions(
                        navigateTo = { collectionViewModel.navigateTo(it) },
                        onClickPlay = { index, item ->
                            play(
                                index,
                                item,
                                false,
                                MediaType.AUDIO,
                            )
                        },
                        onClickPlayNext = { index, item -> viewModel.playNext(item) },
                        onClickAddToQueue = { item ->
                            viewModel.addToQueue(
                                item,
                                Int.MAX_VALUE,
                            )
                        },
                        onClickFavorite = { id, favorite ->
                            collectionViewModel.setFavorite(index, id, favorite)
                        },
                        onClickAddPlaylist = { itemId ->
                            addToPlaylistViewModel.loadPlaylists()
                            showPlaylistDialog.makePresent(itemId)
                        },
                        onClickRemoveFromQueue = { _, _ -> },
                        onDeleteItem = { collectionViewModel.deleteItem(index, item) },
                        onRemoveFromPlaylist = viewModel::removeFromPlaylist,
                    )
                ContextMenu.ForMusic(
                    fromLongClick = fromLongClick,
                    item = item,
                    index = index,
                    canDelete = viewModel.canDelete(item, preferences.appPreferences),
                    canRemoveFromQueue = false,
                    actions = musicContextActions,
                    showRemoveFromPlaylist = playlistState.canEdit,
                )
            } else {
                val contextActions =
                    ContextMenuActions(
                        navigateTo = { collectionViewModel.navigateTo(it) },
                        onClickWatch = { id, watched ->
                            collectionViewModel.setWatched(
                                index,
                                id,
                                watched,
                            )
                        },
                        onClickFavorite = { id, favorite ->
                            collectionViewModel.setFavorite(index, id, favorite)
                        },
                        onClickAddPlaylist = { itemId ->
                            addToPlaylistViewModel.loadPlaylists()
                            showPlaylistDialog.makePresent(itemId)
                        },
                        onSendMediaInfo = collectionViewModel::sendReportFor,
                        onDeleteItem = { collectionViewModel.deleteItem(index, item) },
                        onClickAddToQueue = { viewModel.addToQueue(it, 0) },
                        onShowOverview = {},
                        onChooseVersion = { _, _ -> },
                        onChooseTracks = {},
                        onClearChosenStreams = {},
                        onClickRemoveFromNextUp = {},
                        onRemoveFromPlaylist = viewModel::removeFromPlaylist,
                    )
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
                    showRemoveFromPlaylist = playlistState.canEdit,
                )
            }
    }

    val actions =
        remember {
            GridClickActions(
                onClickItem = { index, item -> collectionViewModel.navigateTo(item.destination(index)) },
                onLongClickItem = { index, item -> showContextMenu(index, item, true) },
                onClickPlayAll = { shuffle ->
                    state.item.successValue?.let { playlist -> play(0, playlist, shuffle) }
                },
                onClickPlayRemoteButton = { index, item -> play(index, item, false) },
            )
        }
    CollectionFolderViewContent(
        preferences = preferences,
        state = state,
        savedPosition = viewModel.position,
        itemId = destination.itemId.toServerString(),
        initialFilter = CollectionFolderFilter(filter = state.filter),
        recursive = false,
        actions = actions,
        sortOptions = PlaylistSortOptions,
        playEnabled = true,
        defaultViewOptions = playlistDefaultViewOptions,
        viewActions = collectionViewModel,
        provider = collectionViewModel,
        positionCallback = { _, position -> viewModel.position = position },
        modifier = modifier,
        listItemContent = { (item, index, onClick, onLongClick, _, mod) ->
            PlaylistItem(
                item = item,
                index = index,
                onClick = onClick,
                onLongClick = onLongClick,
                canMove = playlistState.canEdit,
                moveUpAllowed = index > 0,
                moveDownAllowed =
                    state.items.successValue
                        ?.lastIndex
                        ?.gt(index) ?: false,
                onClickMove = { direction ->
                    item?.let { viewModel.onMoveItem(index, item.id, direction) }
                    when (direction) {
                        MoveDirection.UP -> focusManager.moveFocus(FocusDirection.Up)
                        MoveDirection.DOWN -> focusManager.moveFocus(FocusDirection.Down)
                    }
                },
                onClickMore = { item?.let { showContextMenu(index, item, false) } },
                modifier = mod,
//                interactionSource = TODO(),
                isPlaying =
                    equalsNotNull(
                        musicState.currentItemId,
                        item?.id,
                    ),
                isQueued = remember(musicState.queueVersion) { item?.id in musicState.queuedIds },
            )
        },
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
fun CollectionFolderViewScope.PlaylistItem(
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
    val dense = viewOptions.type == ViewOptionsType.DENSE_LIST
    val focused by interactionSource.collectIsFocusedAsState()
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth(),
//                .heightIn(min = 40.dp, max = 88.dp),
    ) {
        ListItemWrapper(
            dense = dense,
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
                    remember(item?.data?.runTimeTicks) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column {
                            Text(
                                text = durationText,
                            )
                            if (item?.type != BaseItemKind.AUDIO && !dense) {
                                val style =
                                    LocalTextStyle.current.let { it.copy(fontSize = it.fontSize * .75) }
                                Text(
                                    text = stringResource(R.string.ends_at, endTimeStr),
                                    style = style,
                                )
                            }
                        }
                        if (item?.played == true) {
                            WatchedIcon(padding = 0.dp)
                        }
                    }
                }
            },
            leadingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (sortAndDirection.sort == ItemSortBy.DEFAULT) {
//                        val indexText =
//                            remember(index, collectionSize, sortAndDirection) {
//                                when (sortAndDirection.direction) {
//                                    SortOrder.ASCENDING -> "${index + 1}."
//                                    SortOrder.DESCENDING -> "${collectionSize - index}."
//                                }
//                            }
                        // TODO default descending doesn't work?
                        val indexText = "${index + 1}."
                        Text(
                            text = indexText,
                        )
                    }
                    if (item?.type == BaseItemKind.AUDIO) {
                        MusicQueueMarker(
                            isPlaying = isPlaying,
                            isQueued = isQueued,
                        )
                    }
                    if (item?.favorite == true) FavoriteIndicator()
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

private val playlistDefaultViewOptions =
    ViewOptions(
        spacing = 0,
        showDetails = true,
        type = ViewOptionsType.LIST,
    )
