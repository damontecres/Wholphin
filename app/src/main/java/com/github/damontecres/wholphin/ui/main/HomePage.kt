package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.BannerCard
import com.github.damontecres.wholphin.ui.cards.BannerCardWithTitle
import com.github.damontecres.wholphin.ui.cards.GenreCard
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.StudioCard
import com.github.damontecres.wholphin.ui.cards.ViewMoreCard
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.EpisodeName
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.FocusableItemRow
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import com.github.damontecres.wholphin.ui.components.rememberLogoUrl
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.playback.isPlayKeyUp
import com.github.damontecres.wholphin.ui.playback.playable
import com.github.damontecres.wholphin.ui.playback.scale
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.ScrollToTopBringIntoViewSpec
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration

@Composable
fun HomePage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.init()
    }
    val state by viewModel.state.collectAsState()
    val loading = state.loadingState
    val refreshing = state.refreshState
    val homeRows = state.homeRows

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
            var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
            var showPlaylistDialog by remember { mutableStateOf<UUID?>(null) }
            var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }

            val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)
            var position by rememberPosition()

            val onFocusPosition = remember { { it: RowColumn -> position = it } }
            val currentHomePrefs by rememberUpdatedState(preferences.appPreferences.homePagePreferences)
            val onClickItem =
                remember {
                    { clickedPosition: RowColumn, item: BaseItem ->
                        position = clickedPosition
                        if (currentHomePrefs.clickToPlay &&
                            homeRows.getOrNull(clickedPosition.row)?.isContinueWatchingNextUp == true
                        ) {
                            viewModel.navigationManager.navigateTo(Destination.Playback(item))
                        } else {
                            viewModel.navigationManager.navigateTo(item.destination())
                        }
                    }
                }
            val onLongClickItem =
                remember {
                    { clickedPosition: RowColumn, item: BaseItem ->
                        position = clickedPosition
                        val row =
                            (homeRows.getOrNull(clickedPosition.row) as? HomeRowLoadingState.Success)
                        val canRemoveContinueWatching =
                            row?.rowType is HomeRowConfig.ContinueWatching || row?.rowType is HomeRowConfig.ContinueWatchingCombined
                        val canRemoveNextUp =
                            row?.rowType is HomeRowConfig.NextUp || row?.rowType is HomeRowConfig.ContinueWatchingCombined
                        showContextMenu =
                            ContextMenu.ForBaseItem(
                                fromLongClick = true,
                                item = item,
                                chosenStreams = null,
                                showGoTo = true,
                                showStreamChoices = false,
                                canDelete =
                                    viewModel.canDelete(
                                        item,
                                        preferences.appPreferences,
                                    ),
                                canRemoveContinueWatching = canRemoveContinueWatching,
                                canRemoveNextUp = canRemoveNextUp,
                                actions =
                                    ContextMenuActions(
                                        navigateTo = viewModel.navigationManager::navigateTo,
                                        onClickWatch = viewModel::setWatched,
                                        onClickFavorite = viewModel::setFavorite,
                                        onClickAddPlaylist = { itemId ->
                                            playlistViewModel.loadPlaylists(MediaType.VIDEO)
                                            showPlaylistDialog = itemId
                                        },
                                        onSendMediaInfo = viewModel.mediaReportService::sendReportFor,
                                        onDeleteItem = {
                                            viewModel.deleteItem(position, it)
                                        },
                                        onChooseVersion = { _, _ ->
                                            // Not supported on this page
                                        },
                                        onChooseTracks = {
                                            // Not supported on this page
                                        },
                                        onShowOverview = {
                                            overviewDialog = ItemDetailsDialogInfo(it)
                                        },
                                        onClearChosenStreams = {},
                                        onClickRemoveFromNextUp = viewModel::removeFromNextUp,
                                    ),
                            )
                    }
                }
            val onClickPlay =
                remember {
                    { _: RowColumn, item: BaseItem ->
                        viewModel.navigationManager.navigateTo(Destination.Playback(item))
                    }
                }
            val onClickViewMore =
                remember {
                    { _: RowColumn, row: HomeRowLoadingState.Success ->
                        viewModel.navigationManager.navigateTo(
                            Destination.MoreHomeRow(row.title, row.rowType!!, row.items.size),
                        )
                    }
                }

            HomePageContent(
                homeRows = homeRows,
                position = position,
                onFocusPosition = onFocusPosition,
                onClickItem = onClickItem,
                onLongClickItem = onLongClickItem,
                onClickPlay = onClickPlay,
                loadingState = refreshing,
                showClock = preferences.appPreferences.interfacePreferences.showClock,
                onUpdateBackdrop = viewModel::updateBackdrop,
                showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                onClickViewMore = onClickViewMore,
                modifier = modifier,
            )
            overviewDialog?.let { info ->
                ItemDetailsDialog(
                    info = info,
                    showFilePath = false,
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
            showPlaylistDialog?.let { itemId ->
                PlaylistDialog(
                    title = stringResource(R.string.add_to_playlist),
                    state = playlistState,
                    onDismissRequest = { showPlaylistDialog = null },
                    onClick = {
                        playlistViewModel.addToPlaylist(it.id, itemId)
                        showPlaylistDialog = null
                    },
                    createEnabled = true,
                    onCreatePlaylist = {
                        playlistViewModel.createPlaylistAndAddItem(it, itemId)
                        showPlaylistDialog = null
                    },
                    elevation = 3.dp,
                )
            }
        }
    }
}

val HomeRowLoadingState?.isContinueWatchingNextUp: Boolean
    get() =
        (this as? HomeRowLoadingState.Success).let { row ->
            row?.rowType is HomeRowConfig.ContinueWatching ||
                row?.rowType is HomeRowConfig.NextUp ||
                row?.rowType is HomeRowConfig.ContinueWatchingCombined
        }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePageContent(
    homeRows: List<HomeRowLoadingState>,
    position: RowColumn,
    onFocusPosition: (RowColumn) -> Unit,
    onClickItem: (RowColumn, BaseItem) -> Unit,
    onLongClickItem: (RowColumn, BaseItem) -> Unit,
    onClickPlay: (RowColumn, BaseItem) -> Unit,
    showClock: Boolean,
    onUpdateBackdrop: (BaseItem) -> Unit,
    showLogo: Boolean,
    modifier: Modifier = Modifier,
    loadingState: LoadingState? = null,
    listState: LazyListState = rememberLazyListState(),
    takeFocus: Boolean = true,
    showEmptyRows: Boolean = false,
    headerComposable: @Composable (focusedItem: BaseItem?) -> Unit = { focusedItem ->
        HomePageHeader(
            item = focusedItem,
            showLogo = showLogo,
            modifier = HeaderUtils.modifier,
        )
    },
    showViewMore: Boolean = true,
    onClickViewMore: (RowColumn, HomeRowLoadingState.Success) -> Unit = { _, _ -> },
) {
    val focusedItem =
        remember(homeRows, position) {
            (homeRows.getOrNull(position.row) as? HomeRowLoadingState.Success)?.items?.getOrNull(
                position.column,
            )
        }

    val rowFocusRequesters = remember(homeRows.size) { List(homeRows.size) { FocusRequester() } }
    var firstFocused by remember { mutableStateOf(false) }

    val currentPosition by rememberUpdatedState(position)
    val currentOnFocusPosition by rememberUpdatedState(onFocusPosition)
    val currentOnClickPlay by rememberUpdatedState(onClickPlay)

    if (takeFocus) {
        LaunchedEffect(homeRows) {
            if (!firstFocused && homeRows.isNotEmpty()) {
                if (position.row >= 0) {
                    val index = position.row.coerceIn(0, rowFocusRequesters.lastIndex)
                    rowFocusRequesters.getOrNull(index)?.tryRequestFocus()
                    firstFocused = true
                } else {
                    // Waiting for the first home row to load, then focus on it
                    homeRows
                        .indexOfFirstOrNull { it is HomeRowLoadingState.Success && it.items.isNotEmpty() }
                        ?.let {
                            rowFocusRequesters[it].tryRequestFocus()
                            firstFocused = true
                            delay(50)
                            listState.scrollToItem(it)
                        }
                }
            }
        }
    }
    LaunchedEffect(onUpdateBackdrop, focusedItem) {
        focusedItem?.let { onUpdateBackdrop.invoke(it) }
    }
    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .focusProperties {
                        onEnter = {
                            rowFocusRequesters.getOrNull(currentPosition.row)?.tryRequestFocus()
                        }
                    }.fillMaxSize(),
        ) {
            headerComposable.invoke(focusedItem)

            val density = LocalDensity.current
            val spaceAbovePx =
                remember(density) {
                    with(density) {
                        // The size of the row titles & spacing
                        50.dp.toPx()
                    }
                }
            val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
            CompositionLocalProvider(
                LocalBringIntoViewSpec provides ScrollToTopBringIntoViewSpec(spaceAbovePx),
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding =
                        PaddingValues(
                            bottom = Cards.height2x3,
                        ),
                    modifier =
                        Modifier
                            .focusRestorer(),
                ) {
                    itemsIndexed(homeRows) { rowIndex, row ->
                        CompositionLocalProvider(
                            LocalBringIntoViewSpec provides defaultBringIntoViewSpec,
                        ) {
                            when (val r = row) {
                                is HomeRowLoadingState.Loading,
                                is HomeRowLoadingState.Pending,
                                -> {
                                    FocusableItemRow(
                                        title = r.title,
                                        subtitle = stringResource(R.string.loading),
                                        modifier = Modifier.animateItem(),
                                    )
                                }

                                is HomeRowLoadingState.Error -> {
                                    FocusableItemRow(
                                        title = r.title,
                                        subtitle = r.localizedMessage,
                                        isError = true,
                                        modifier = Modifier.animateItem(),
                                    )
                                }

                                is HomeRowLoadingState.Success -> {
                                    if (row.items.isNotEmpty()) {
                                        val viewOptions = row.viewOptions
                                        ItemRow(
                                            title = row.title,
                                            items = row.items,
                                            onClickItem =
                                                remember(rowIndex, onClickItem) {
                                                    { index, item ->
                                                        onClickItem.invoke(
                                                            RowColumn(
                                                                rowIndex,
                                                                index,
                                                            ),
                                                            item,
                                                        )
                                                    }
                                                },
                                            onLongClickItem =
                                                remember(rowIndex, onLongClickItem) {
                                                    { index, item ->
                                                        onLongClickItem.invoke(
                                                            RowColumn(rowIndex, index),
                                                            item,
                                                        )
                                                    }
                                                },
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .focusGroup()
                                                    .focusRequester(rowFocusRequesters[rowIndex])
                                                    .animateItem(),
                                            horizontalPadding = viewOptions.spacing.dp,
                                            cardContent = { index, item, cardModifier, onClick, onLongClick ->
                                                val onFocus =
                                                    remember(rowIndex, index) {
                                                        { isFocused: Boolean ->
                                                            if (isFocused) {
                                                                currentOnFocusPosition(
                                                                    RowColumn(
                                                                        rowIndex,
                                                                        index,
                                                                    ),
                                                                )
                                                            }
                                                        }
                                                    }
                                                val onKey =
                                                    remember(item) {
                                                        { event: KeyEvent ->
                                                            if (isPlayKeyUp(event) && item?.type?.playable == true) {
                                                                Timber.v("Clicked play on ${item.id}")
                                                                currentOnClickPlay(
                                                                    currentPosition,
                                                                    item,
                                                                )
                                                                true
                                                            } else {
                                                                false
                                                            }
                                                        }
                                                    }
                                                HomePageCardContent(
                                                    index = index,
                                                    item = item,
                                                    onClick = onClick,
                                                    onLongClick = onLongClick,
                                                    viewOptions = viewOptions,
                                                    modifier =
                                                        cardModifier
                                                            .onFocusChanged { onFocus(it.isFocused) }
                                                            .onKeyEvent { onKey(it) },
                                                )
                                            },
                                            showViewMore = showViewMore,
                                            viewMoreCardContent = { mod ->
                                                HomePageViewMoreCard(
                                                    isEpisode = row.items.last()?.type == BaseItemKind.EPISODE,
                                                    onClick = {
                                                        onClickViewMore.invoke(
                                                            RowColumn(
                                                                rowIndex,
                                                                r.items.size,
                                                            ),
                                                            r,
                                                        )
                                                    },
                                                    onLongClick = {},
                                                    viewOptions = viewOptions,
                                                    modifier =
                                                        mod.onFocusChanged {
                                                            if (it.isFocused) {
                                                                currentOnFocusPosition.invoke(
                                                                    RowColumn(
                                                                        rowIndex,
                                                                        r.items.size,
                                                                    ),
                                                                )
                                                            }
                                                        },
                                                )
                                            },
                                        )
                                    } else if (showEmptyRows) {
                                        FocusableItemRow(
                                            title = r.title,
                                            subtitle = stringResource(R.string.no_results),
                                            modifier = Modifier.animateItem(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        when (loadingState) {
            LoadingState.Pending,
            LoadingState.Loading,
            -> {
                Box(
                    modifier =
                        Modifier
                            .padding(if (showClock) 40.dp else 20.dp)
                            .size(40.dp)
                            .align(Alignment.TopEnd),
                ) {
                    CircularProgress(Modifier.fillMaxSize())
                }
            }

            else -> {}
        }
    }
}

@Composable
fun HomePageHeader(
    item: BaseItem?,
    showLogo: Boolean,
    modifier: Modifier = Modifier,
) {
    val isEpisode = item?.type == BaseItemKind.EPISODE
    val dto = item?.data
    HomePageHeader(
        title = item?.title,
        subtitle = if (isEpisode) dto?.name else null,
        overview = dto?.overview,
        overviewTwoLines = isEpisode,
        quickDetails = item?.ui?.quickDetails ?: AnnotatedString(""),
        timeRemaining = item?.timeRemainingOrRuntime,
        showLogo = showLogo,
        logoImageUrl = rememberLogoUrl(item),
        modifier = modifier,
    )
}

@Composable
fun HomePageHeader(
    title: String?,
    subtitle: String?,
    overview: String?,
    overviewTwoLines: Boolean,
    quickDetails: AnnotatedString?,
    timeRemaining: Duration?,
    showLogo: Boolean,
    logoImageUrl: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        TitleOrLogo(
            title = title,
            logoImageUrl = logoImageUrl,
            showLogo = showLogo,
            modifier = Modifier.fillMaxWidth(.75f),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth(.6f),
        ) {
            if (subtitle != null) {
                EpisodeName(subtitle)
            }
            QuickDetails(quickDetails ?: AnnotatedString(""), timeRemaining)
            val overviewModifier =
                Modifier
                    .padding(0.dp)
                    .height(48.dp + if (!overviewTwoLines) 12.dp else 0.dp)
                    .width(400.dp)
            if (overview.isNotNullOrBlank()) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (overviewTwoLines) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = overviewModifier,
                )
            } else {
                Spacer(overviewModifier)
            }
        }
    }
}

@Composable
fun HomePageCardContent(
    index: Int,
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    viewOptions: HomeRowViewOptions,
    modifier: Modifier,
) {
    when (item?.type) {
        BaseItemKind.GENRE -> {
            GenreCard(
                genreId = item.id,
                name = item.name,
                imageUrl = item.imageUrlOverride,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier.height(viewOptions.heightDp.dp),
            )
        }

        BaseItemKind.STUDIO -> {
            StudioCard(
                studioId = item.id,
                name = item.name,
                imageUrl = item.imageUrlOverride,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier.height(viewOptions.heightDp.dp),
            )
        }

        else -> {
            val imageType =
                remember(item, viewOptions) {
                    if (item?.type == BaseItemKind.EPISODE) {
                        viewOptions.episodeImageType.imageType
                    } else {
                        viewOptions.imageType.imageType
                    }
                }
            val ratio =
                remember(item, viewOptions) {
                    if (item?.type == BaseItemKind.EPISODE) {
                        viewOptions.episodeAspectRatio.ratio
                    } else {
                        viewOptions.aspectRatio.ratio
                    }
                }
            val scale =
                remember(item, viewOptions) {
                    if (item?.type == BaseItemKind.EPISODE) {
                        viewOptions.episodeContentScale.scale
                    } else {
                        viewOptions.contentScale.scale
                    }
                }
            if (viewOptions.showTitles) {
                BannerCardWithTitle(
                    title = item?.title,
                    subtitle = item?.subtitle,
                    item = item,
                    aspectRatio = ratio,
                    imageType = imageType,
                    imageContentScale = scale,
                    cornerText = item?.ui?.episodeUnplayedCornerText,
                    played = item?.data?.userData?.played ?: false,
                    favorite = item?.favorite ?: false,
                    playPercent =
                        item?.data?.userData?.playedPercentage
                            ?: 0.0,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    modifier = modifier,
                    cardHeight = viewOptions.heightDp.dp,
                    useSeriesForPrimary = viewOptions.useSeries,
                )
            } else {
                BannerCard(
                    name = item?.data?.seriesName ?: item?.name,
                    item = item,
                    aspectRatio = ratio,
                    imageType = imageType,
                    imageContentScale = scale,
                    cornerText = item?.ui?.episodeUnplayedCornerText,
                    played = item?.data?.userData?.played ?: false,
                    favorite = item?.favorite ?: false,
                    playPercent =
                        item?.data?.userData?.playedPercentage
                            ?: 0.0,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    modifier = modifier,
                    interactionSource = null,
                    cardHeight = viewOptions.heightDp.dp,
                    useSeriesForPrimary = viewOptions.useSeries,
                )
            }
        }
    }
}

@Composable
fun HomePageViewMoreCard(
    isEpisode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    viewOptions: HomeRowViewOptions,
    modifier: Modifier,
) {
    val aspectRatio =
        remember(isEpisode, viewOptions) {
            if (isEpisode) {
                viewOptions.episodeAspectRatio
            } else {
                viewOptions.aspectRatio
            }
        }
    ViewMoreCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        aspectRatio = aspectRatio,
        size = DpSize(height = viewOptions.heightDp.dp, width = Dp.Unspecified),
        showTitle = viewOptions.showTitles,
    )
}
