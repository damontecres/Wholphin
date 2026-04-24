package com.github.damontecres.wholphin.ui.detail.collection

import android.content.Context
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Color
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
import com.github.damontecres.wholphin.ui.components.ConfirmDeleteDialog
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
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
import com.github.damontecres.wholphin.ui.detail.MoreDialogActions
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItemsForHome
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
    var moreDialog by remember { mutableStateOf<DialogParams?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)
    var showDeleteDialog by remember { mutableStateOf<Pair<RowColumn?, BaseItem>?>(null) }
    var showViewOptionsDialog by remember { mutableStateOf(false) }
    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }

    // Actions
    val onClickItem =
        remember {
            { _: RowColumn, item: BaseItem -> viewModel.navigate(item.destination()) }
        }
    val onLongClickItem =
        remember {
            { position: RowColumn, item: BaseItem ->
                val dialogItems =
                    buildMoreDialogItemsForHome(
                        context = context,
                        item = item,
                        seriesId = item.data.seriesId,
                        playbackPosition = item.playbackPosition,
                        watched = item.played,
                        favorite = item.favorite,
                        canDelete = viewModel.canDelete(item, preferences.appPreferences),
                        actions =
                            MoreDialogActions(
                                navigateTo = viewModel::navigate,
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
                                onClickDelete = { item -> showDeleteDialog = Pair(position, item) },
                                onClickAddToQueue = { viewModel.addToQueue(it, 0) },
                            ),
                    )
                moreDialog =
                    DialogParams(
                        fromLongClick = true,
                        title = item.title ?: "",
                        items = dialogItems,
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
        viewModel.navigate(Destination.Playback(item = item))
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
                viewModel.navigate(dest)
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
                deleteOnClick =
                    remember {
                        {
                            state.collection?.let {
                                viewModel.deleteItem(it, null)
                            }
                        }
                    },
                canDelete =
                    remember(state.collection) {
                        state.collection?.let {
                            viewModel.canDelete(it, preferences.appPreferences)
                        } ?: false
                    },
                moreOnClick = {
                    val collection = state.collection!!
                    val items =
                        buildMoreDialogItemsForCollection(
                            context = context,
                            item = collection,
                            favorite = collection.favorite,
                            canDelete = viewModel.canDelete(collection, preferences.appPreferences),
                            onClickPlayAll = onClickPlayAll,
                            actions =
                                MoreDialogActions(
                                    navigateTo = viewModel::navigate,
                                    onClickWatch = { itemId, watched ->
                                        viewModel.setWatched(itemId, watched, null)
                                    },
                                    onClickFavorite = { itemId, favorite ->
                                        viewModel.setFavorite(itemId, favorite, null)
                                    },
                                    onClickAddPlaylist = { itemId ->
                                        playlistViewModel.loadPlaylists(MediaType.VIDEO)
                                        showPlaylistDialog.makePresent(itemId)
                                    },
                                    onSendMediaInfo = viewModel.mediaReportService::sendReportFor,
                                    onClickDelete = { item -> showDeleteDialog = Pair(null, item) },
                                    onClickAddToQueue = { viewModel.addToQueue(it, 0) },
                                ),
                        )
                    moreDialog =
                        DialogParams(
                            fromLongClick = false,
                            title = collection.title ?: "",
                            items = items,
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
    moreDialog?.let { params ->
        DialogPopup(
            showDialog = true,
            title = params.title,
            dialogItems = params.items,
            onDismissRequest = { moreDialog = null },
            dismissOnClick = true,
            waitToLoad = params.fromLongClick,
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
            itemTitle = item.title ?: "",
            onCancel = { showDeleteDialog = null },
            onConfirm = {
                viewModel.deleteItem(item, position)
                showDeleteDialog = null
            },
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
    deleteOnClick: () -> Unit,
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
                            deleteOnClick = deleteOnClick,
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

fun buildMoreDialogItemsForCollection(
    context: Context,
    item: BaseItem,
    favorite: Boolean,
    canDelete: Boolean,
    onClickPlayAll: (shuffle: Boolean) -> Unit,
    actions: MoreDialogActions,
): List<DialogItem> =
    buildList {
        add(
            DialogItem(
                context.getString(R.string.play),
                Icons.Default.PlayArrow,
                iconColor = Color.Green.copy(alpha = .8f),
            ) {
                onClickPlayAll.invoke(false)
            },
        )
        add(
            DialogItem(
                context.getString(R.string.shuffle),
                R.string.fa_shuffle,
            ) {
                onClickPlayAll.invoke(true)
            },
        )

        add(
            DialogItem(
                text = R.string.add_to_playlist,
                iconStringRes = R.string.fa_list_ul,
            ) {
                actions.onClickAddPlaylist.invoke(item.id)
            },
        )
        if (canDelete) {
            add(
                DialogItem(
                    context.getString(R.string.delete),
                    Icons.Default.Delete,
                    iconColor = Color.Red.copy(alpha = .8f),
                ) {
                    actions.onClickDelete.invoke(item)
                },
            )
        }
        add(
            DialogItem(
                text = if (favorite) R.string.remove_favorite else R.string.add_favorite,
                iconStringRes = R.string.fa_heart,
                iconColor = if (favorite) Color.Red else Color.Unspecified,
            ) {
                actions.onClickFavorite.invoke(item.id, !favorite)
            },
        )
    }
