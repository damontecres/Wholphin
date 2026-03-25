package com.github.damontecres.wholphin.ui.detail.collection

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.ConfirmDeleteDialog
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.GenreText
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.Optional
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.detail.MoreDialogActions
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItemsForHome
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.main.HomePageHeader
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.MediaType
import java.util.UUID

@Composable
fun CollectionDetails2(
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
    var showDeleteDialog by remember { mutableStateOf<Pair<RowColumn, BaseItem>?>(null) }
    var showViewOptionsDialog by remember { mutableStateOf(false) }

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
    val onChangeBackdrop = remember { { item: BaseItem -> viewModel.updateBackdrop(item) } }
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
                onChangeBackdrop = onChangeBackdrop,
                onFilterChange = onFilterChange,
                getPossibleFilterValues = viewModel::getPossibleFilterValues,
                letterPosition = viewModel::letterPosition,
                onClickViewOptions = onClickViewOptions,
                modifier = modifier,
                overviewOnClick = {},
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
    modifier: Modifier,
) {
    var showButtons by remember { mutableStateOf(true) }
    var itemsContentHasFocus by remember { mutableStateOf(true) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val focusRequester = remember { FocusRequester() }

    var focusedItem by remember { mutableStateOf<BaseItem?>(state.collection) }
    if (state.viewOptions.cardViewOptions.showDetails) {
        LaunchedEffect(focusedItem) {
            focusedItem?.let { onChangeBackdrop.invoke(it) }
        }
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
                    Column(
                        Modifier.sharedBounds(
                            rememberSharedContentState(key = "header"),
                            animatedVisibilityScope = this@AnimatedContent,
                            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
                        ),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(0.dp)
                                    .focusable()
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            focusRequester.tryRequestFocus()
                                        }
                                    },
                        )
                        if (state.viewOptions.cardViewOptions.showDetails) {
                            HomePageHeader(
                                item = focusedItem,
                                modifier =
                                    Modifier
                                        .padding(top = 48.dp, bottom = 32.dp, start = 8.dp)
                                        .fillMaxHeight(.33f)
                                        .fillMaxWidth(),
                            )
                        }
                    }
                } else {
                    // Show collection header
                    LaunchedEffect(Unit) {
                        focusRequester.tryRequestFocus()
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier =
                            Modifier
                                .sharedBounds(
                                    rememberSharedContentState(key = "header"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                                ).padding(bottom = 32.dp)
                                .fillMaxWidth()
                                .onFocusChanged {
                                    if (it.hasFocus) {
                                        onChangeBackdrop.invoke(state.collection!!)
                                    }
                                },
                    ) {
                        CollectionDetailsHeader(
                            collection = state.collection!!,
                            overviewOnClick = overviewOnClick,
                            bringIntoViewRequester = bringIntoViewRequester,
                            modifier =
                                Modifier
                                    .padding(top = 48.dp, start = 8.dp)
                                    .fillMaxHeight(.33f)
                                    .fillMaxWidth(),
                        )
                        CollectionButtons(
                            state = state,
                            onSortChange = onSortChange,
                            onClickPlayAll = onClickPlayAll,
                            onFilterChange = onFilterChange,
                            getPossibleFilterValues = getPossibleFilterValues,
                            onClickViewOptions = onClickViewOptions,
                            modifier = Modifier.focusRequester(focusRequester),
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
                        itemsContentHasFocus = it.hasFocus
                    }.focusProperties {
                        up = focusRequester
                    },
        ) {
            if (state.viewOptions.separateTypes) {
                CollectionRows(
                    preferences = preferences,
                    state = state,
                    onClickItem = onClickItem,
                    onLongClickItem = onLongClickItem,
                    onSortChange = onSortChange,
                    onClickPlay = onClickPlay,
                    onClickPlayAll = onClickPlayAll,
                    onChangeBackdrop = onChangeBackdrop,
                    onFilterChange = onFilterChange,
                    getPossibleFilterValues = getPossibleFilterValues,
                    onClickViewOptions = onClickViewOptions,
                    modifier = Modifier.fillMaxSize(),
                    onFocusPosition = {
                        showButtons = it.row <= 0
                        // TODO get focused Item
                    },
                )
            } else {
                CollectionMixedGrid(
                    preferences = preferences,
                    state = state,
                    onClickItem = onClickItem,
                    onLongClickItem = onLongClickItem,
                    onSortChange = onSortChange,
                    onClickPlay = onClickPlay,
                    onClickPlayAll = onClickPlayAll,
                    onChangeBackdrop = onChangeBackdrop,
                    onFilterChange = onFilterChange,
                    getPossibleFilterValues = getPossibleFilterValues,
                    letterPosition = letterPosition,
                    onClickViewOptions = onClickViewOptions,
                    modifier = Modifier.fillMaxSize(),
                    onFocusPosition = {
                        showButtons = it.column < state.viewOptions.cardViewOptions.columns
                        focusedItem = state.items.getOrNull(it.column)
                    },
                )
            }
        }
    }
}

@Composable
fun CollectionDetailsHeader(
    collection: BaseItem,
    overviewOnClick: () -> Unit,
    bringIntoViewRequester: BringIntoViewRequester,
    modifier: Modifier = Modifier,
) {
    val dto = collection.data
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        // Title
        Text(
            text = collection.name ?: "",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth(.75f)
                    .padding(start = 8.dp),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(.60f),
        ) {
            QuickDetails(
                collection.ui.quickDetails,
                collection.timeRemainingOrRuntime,
                Modifier.padding(start = 8.dp),
            )

            dto.genres?.letNotEmpty {
                GenreText(it, Modifier.padding(start = 8.dp))
            }
            dto.taglines?.firstOrNull()?.let { tagline ->
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            // Description
            dto.overview?.let { overview ->
                OverviewText(
                    overview = overview,
                    maxLines = 3,
                    onClick = overviewOnClick,
                    textBoxHeight = Dp.Unspecified,
                    modifier =
                        Modifier.onFocusChanged {
                            if (it.isFocused) {
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                )
            }
        }
    }
}
