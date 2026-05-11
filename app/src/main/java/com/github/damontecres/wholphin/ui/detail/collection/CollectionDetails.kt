package com.github.damontecres.wholphin.ui.detail.collection

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.HiddenFocusBox
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.Optional
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.main.HomePageHeader
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import org.jellyfin.sdk.model.api.MediaType
import timber.log.Timber
import java.util.UUID

@Composable
fun CollectionDetails(
    preferences: UserPreferences,
    itemId: UUID,
    modifier: Modifier = Modifier,
    viewModel: CollectionViewModel =
        hiltViewModel<CollectionViewModel, CollectionViewModel.Factory>(
            creationCallback = { it.create(itemId) },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    // Dialogs
    var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)
    var showViewOptionsDialog by remember { mutableStateOf(false) }
    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }

    fun contextActionsFor(position: RowColumn?) =
        ContextMenuActions(
            navigateTo = viewModel::navigateTo,
            onClickWatch = { itemId, watched ->
                viewModel.setWatched(itemId, watched, position)
            },
            onClickFavorite = { itemId, favorite ->
                viewModel.setFavorite(itemId, favorite, position)
            },
            onClickAddPlaylist = { itemId ->
                playlistViewModel.loadPlaylists(MediaType.VIDEO)
                showPlaylistDialog.makePresent(itemId)
            },
            onSendMediaInfo = viewModel.mediaReportService::sendReportFor,
            onDeleteItem = { viewModel.deleteItem(it, position) },
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

    // Actions
    val onClickItem =
        remember {
            { _: RowColumn, item: BaseItem -> viewModel.navigateTo(item.destination()) }
        }
    val onLongClickItem =
        remember {
            { position: RowColumn, item: BaseItem ->
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
                        actions = contextActionsFor(position),
                    )
            }
        }
    val onSortChange =
        remember {
            { sort: SortAndDirection -> viewModel.changeSort(sort) }
        }
    val onFilterChange =
        remember {
            { filter: GetItemsFilter -> viewModel.changeFilter(filter) }
        }
    val onClickPlay = { _: RowColumn, item: BaseItem ->
        viewModel.navigateTo(Destination.Playback(item = item))
    }
    val onClickPlayAll =
        remember {
            { shuffle: Boolean ->
                val dest =
                    Destination.PlaybackList(
                        itemId = itemId,
                        startIndex = 0,
                        shuffle = shuffle,
                        recursive = true,
                        sortAndDirection = state.sortAndDirection,
                        filter = state.itemFilter,
                    )
                viewModel.navigateTo(dest)
            }
        }
    val onClickViewOptions = remember { { showViewOptionsDialog = true } }

    when (val s = state.loadingState) {
        is LoadingState.Error -> {
            ErrorMessage(s, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            CollectionDetailsContent(
                preferences = preferences,
                state = state,
                onClickItem = onClickItem,
                onLongClickItem = onLongClickItem,
                onSortChange = onSortChange,
                onClickPlay = onClickPlay,
                onClickPlayAll = onClickPlayAll,
                onChangeBackdrop = viewModel::updateBackdrop,
                onFilterChange = onFilterChange,
                getPossibleFilterValues = viewModel::getPossibleFilterValues,
                letterPosition = viewModel::letterPosition,
                onClickViewOptions = onClickViewOptions,
                modifier = modifier,
                overviewOnClick = {
                    val collection = state.collection!!
                    overviewDialog = ItemDetailsDialogInfo(collection)
                },
                favoriteOnClick =
                    remember {
                        {
                            state.collection?.let {
                                viewModel.setFavorite(it.id, !it.favorite, null)
                            }
                        }
                    },
                onConfirmDelete =
                    remember {
                        {
                            state.collection?.let {
                                viewModel.deleteItem(it, null)
                            }
                        }
                    },
                canDelete =
                    remember(
                        state.collection,
                        preferences.appPreferences.interfacePreferences.enableMediaManagement,
                    ) {
                        state.collection?.let {
                            viewModel.canDelete(it, preferences.appPreferences)
                        } ?: false
                    },
                moreOnClick = {
                    showContextMenu =
                        ContextMenu.ForBaseItem(
                            fromLongClick = false,
                            item = state.collection!!,
                            chosenStreams = null,
                            showGoTo = false,
                            showStreamChoices = false,
                            canDelete =
                                viewModel.canDelete(
                                    state.collection!!,
                                    preferences.appPreferences,
                                ),
                            canRemoveContinueWatching = false,
                            canRemoveNextUp = false,
                            actions = contextActionsFor(null),
                        )
                },
            )
        }
    }
    if (showViewOptionsDialog) {
        CollectionViewOptionsDialog(
            viewOptions = state.viewOptions,
            onDismissRequest = { showViewOptionsDialog = false },
            onViewOptionsChange = viewModel::changeViewOptions,
        )
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

@Composable
fun CollectionDetailsContent(
    preferences: UserPreferences,
    state: CollectionState,
    onClickItem: (RowColumn, BaseItem) -> Unit,
    onLongClickItem: (RowColumn, BaseItem) -> Unit,
    onSortChange: (SortAndDirection) -> Unit,
    onClickPlay: (RowColumn, BaseItem) -> Unit,
    onClickPlayAll: (Boolean) -> Unit,
    onChangeBackdrop: (BaseItem) -> Unit,
    onFilterChange: (GetItemsFilter) -> Unit,
    getPossibleFilterValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
    letterPosition: suspend (Char) -> Int,
    onClickViewOptions: () -> Unit,
    overviewOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    onConfirmDelete: () -> Unit,
    canDelete: Boolean,
    moreOnClick: () -> Unit,
    modifier: Modifier,
) {
    var itemsContentHasFocus by rememberSaveable { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val focusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }

    var focusedItem by remember { mutableStateOf<BaseItem?>(state.collection) }
    LaunchedEffect(focusedItem) {
        focusedItem?.let { onChangeBackdrop.invoke(it) }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        SharedTransitionLayout {
            AnimatedContent(
                targetState = itemsContentHasFocus,
                label = "header_transition",
            ) { targetState ->
                if (targetState) {
                    // Show item header
                    LaunchedEffect(Unit) {
                        contentFocusRequester.tryRequestFocus()
                    }
                    Column(
                        Modifier.sharedBounds(
                            rememberSharedContentState(key = "header"),
                            animatedVisibilityScope = this@AnimatedContent,
                            enter = slideInVertically { it / 2 } + fadeIn(),
                            exit = slideOutVertically { it / 2 } + fadeOut(),
                        ),
                    ) {
                        // This box exists so that there is something focusable above the item content
                        // allowing focus to move up to restore the collection's header
                        HiddenFocusBox {
                            itemsContentHasFocus = false
                        }
                        if (state.viewOptions.cardViewOptions.showDetails) {
                            HomePageHeader(
                                item = focusedItem,
                                showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                                modifier =
                                    Modifier
                                        .padding(
                                            top = HeaderUtils.topPadding,
                                            bottom = 8.dp,
                                        ).height(HeaderUtils.height),
                            )
                        }
                    }
                } else {
                    // Show collection header
                    LaunchedEffect(Unit) {
                        focusRequester.tryRequestFocus()
                        focusedItem = state.collection
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier =
                            Modifier
                                .sharedBounds(
                                    rememberSharedContentState(key = "header"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                    enter = slideInVertically { -it / 2 } + fadeIn(),
                                    exit = slideOutVertically { -it / 2 } + fadeOut(),
                                ).padding(bottom = 16.dp)
                                .fillMaxWidth()
                                .onFocusChanged {
                                    if (it.hasFocus) {
                                        onChangeBackdrop.invoke(state.collection!!)
                                    }
                                },
                    ) {
                        CollectionDetailsHeader(
                            collection = state.collection!!,
                            showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                            logoImageUrl = state.logoImageUrl,
                            overviewOnClick = overviewOnClick,
                            bringIntoViewRequester = bringIntoViewRequester,
                            modifier =
                                Modifier
                                    .padding(
                                        top = HeaderUtils.topPadding,
                                        bottom = HeaderUtils.bottomPadding,
                                    ).height(HeaderUtils.height),
                        )
                        CollectionButtons(
                            state = state,
                            onSortChange = onSortChange,
                            onClickPlayAll = onClickPlayAll,
                            onFilterChange = onFilterChange,
                            getPossibleFilterValues = getPossibleFilterValues,
                            onClickViewOptions = onClickViewOptions,
                            favoriteOnClick = favoriteOnClick,
                            onConfirmDelete = onConfirmDelete,
                            canDelete = canDelete,
                            moreOnClick = moreOnClick,
                            modifier =
                                Modifier
                                    .focusRequester(focusRequester)
                                    .fillMaxWidth(),
                        )
                    }
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onFocusChanged {
                        if (it.hasFocus) itemsContentHasFocus = true
                    }.focusProperties {
                        up = focusRequester
                    }.focusRequester(contentFocusRequester),
        ) {
            if (state.viewOptions.separateTypes) {
                CollectionRows(
                    preferences = preferences,
                    state = state,
                    onClickItem = onClickItem,
                    onLongClickItem = onLongClickItem,
                    onClickPlay = onClickPlay,
                    modifier = Modifier.fillMaxSize(),
                    onFocusPosition = { position ->
                        Timber.v("onFocusPosition=%s", position)
                        focusedItem =
                            position.let {
                                val key =
                                    state.separateItems.keys
                                        .toList()
                                        .getOrNull(it.row)
                                (state.separateItems[key] as? HomeRowLoadingState.Success)?.items?.getOrNull(
                                    it.column,
                                )
                            }
                    },
                )
            } else {
                CollectionMixedGrid(
                    preferences = preferences,
                    state = state,
                    onClickItem = onClickItem,
                    onLongClickItem = onLongClickItem,
                    onClickPlay = onClickPlay,
                    letterPosition = letterPosition,
                    modifier = Modifier.fillMaxSize(),
                    onFocusPosition = {
                        Timber.v("onFocusPosition=%s", it)
                        focusedItem = state.items.getOrNull(it.column)
                    },
                )
            }
        }
    }
}
