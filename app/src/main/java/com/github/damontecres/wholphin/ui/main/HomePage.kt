package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.BannerCard
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.EpisodeName
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.MoreDialogActions
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItemsForHome
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.playback.isPlayKeyUp
import com.github.damontecres.wholphin.ui.playback.playable
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.tryRequestFocus
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
    val loading by viewModel.loadingState.observeAsState(LoadingState.Loading)
    val refreshing by viewModel.refreshState.observeAsState(LoadingState.Loading)
    val watchingRows by viewModel.watchingRows.observeAsState(listOf())
    val latestRows by viewModel.latestRows.observeAsState(listOf())

    val homeRows = remember(watchingRows, latestRows) { watchingRows + latestRows }

    when (val state = loading) {
        is LoadingState.Error -> {
            ErrorMessage(state)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage()
        }

        LoadingState.Success -> {
            var dialog by remember { mutableStateOf<DialogParams?>(null) }
            var showPlaylistDialog by remember { mutableStateOf<UUID?>(null) }
            val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)
            HomePageContent(
                homeRows = homeRows,
                onClickItem = { position, item ->
                    viewModel.navigationManager.navigateTo(item.destination())
                },
                onLongClickItem = { position, item ->
                    val dialogItems =
                        buildMoreDialogItemsForHome(
                            context = context,
                            item = item,
                            seriesId = item.data.seriesId,
                            playbackPosition = item.playbackPosition,
                            watched = item.played,
                            favorite = item.favorite,
                            actions =
                                MoreDialogActions(
                                    navigateTo = viewModel.navigationManager::navigateTo,
                                    onClickWatch = { itemId, played ->
                                        viewModel.setWatched(itemId, played)
                                    },
                                    onClickFavorite = { itemId, favorite ->
                                        viewModel.setFavorite(itemId, favorite)
                                    },
                                    onClickAddPlaylist = { itemId ->
                                        playlistViewModel.loadPlaylists(MediaType.VIDEO)
                                        showPlaylistDialog = itemId
                                    },
                                ),
                        )
                    dialog =
                        DialogParams(
                            title = item.title ?: "",
                            fromLongClick = true,
                            items = dialogItems,
                        )
                },
                onClickPlay = { _, item ->
                    viewModel.navigationManager.navigateTo(Destination.Playback(item))
                },
                loadingState = refreshing,
                showClock = preferences.appPreferences.interfacePreferences.showClock,
                onUpdateBackdrop = viewModel::updateBackdrop,
                modifier = modifier,
            )
            dialog?.let { params ->
                DialogPopup(
                    params = params,
                    onDismissRequest = { dialog = null },
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

@Composable
fun HomePageContent(
    homeRows: List<HomeRowLoadingState>,
    onClickItem: (RowColumn, BaseItem) -> Unit,
    onLongClickItem: (RowColumn, BaseItem) -> Unit,
    onClickPlay: (RowColumn, BaseItem) -> Unit,
    showClock: Boolean,
    onUpdateBackdrop: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    onFocusPosition: ((RowColumn) -> Unit)? = null,
    loadingState: LoadingState? = null,
) {
    var position by rememberPosition()
    val focusedItem =
        position.let {
            (homeRows.getOrNull(it.row) as? HomeRowLoadingState.Success)?.items?.getOrNull(it.column)
        }

    val listState = rememberLazyListState()
    val rowFocusRequesters = remember(homeRows) { List(homeRows.size) { FocusRequester() } }
    var firstFocused by remember { mutableStateOf(false) }
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
    LaunchedEffect(position) {
        if (position.row >= 0) {
            listState.animateScrollToItem(position.row)
        }
    }
    LaunchedEffect(onUpdateBackdrop, focusedItem) {
        focusedItem?.let { onUpdateBackdrop.invoke(it) }
    }
    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            HomePageHeader(
                item = focusedItem,
                modifier =
                    Modifier
                        .padding(top = 48.dp, bottom = 32.dp, start = 8.dp)
                        .fillMaxHeight(.33f),
            )
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
                    when (val r = row) {
                        is HomeRowLoadingState.Loading,
                        is HomeRowLoadingState.Pending,
                        -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.animateItem(),
                            ) {
                                Text(
                                    text = r.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = stringResource(R.string.loading),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }

                        is HomeRowLoadingState.Error -> {
                            var focused by remember { mutableStateOf(false) }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier =
                                    Modifier
                                        .onFocusChanged {
                                            focused = it.isFocused
                                        }.focusable()
                                        .background(
                                            if (focused) {
                                                // Just so the user can tell it has focus
                                                MaterialTheme.colorScheme.border.copy(alpha = .25f)
                                            } else {
                                                Color.Unspecified
                                            },
                                        ).animateItem(),
                            ) {
                                Text(
                                    text = r.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = r.localizedMessage,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        is HomeRowLoadingState.Success -> {
                            if (row.items.isNotEmpty()) {
                                ItemRow(
                                    title = row.title,
                                    items = row.items,
                                    onClickItem = { index, item ->
                                        onClickItem.invoke(RowColumn(rowIndex, index), item)
                                    },
                                    onLongClickItem = { index, item ->
                                        onLongClickItem.invoke(RowColumn(rowIndex, index), item)
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .focusGroup()
                                            .focusRequester(rowFocusRequesters[rowIndex])
                                            .animateItem(),
                                    cardContent = { index, item, cardModifier, onClick, onLongClick ->
                                        BannerCard(
                                            name = item?.data?.seriesName ?: item?.name,
                                            item = item,
                                            aspectRatio = AspectRatios.TALL,
                                            cornerText = item?.ui?.episdodeUnplayedCornerText,
                                            played = item?.data?.userData?.played ?: false,
                                            favorite = item?.favorite ?: false,
                                            playPercent =
                                                item?.data?.userData?.playedPercentage
                                                    ?: 0.0,
                                            onClick = onClick,
                                            onLongClick = onLongClick,
                                            modifier =
                                                cardModifier
                                                    .onFocusChanged {
                                                        if (it.isFocused) {
                                                            position = RowColumn(rowIndex, index)
//                                                            item?.let(onUpdateBackdrop)
                                                        }
                                                        if (it.isFocused && onFocusPosition != null) {
                                                            val nonEmptyRowBefore =
                                                                homeRows
                                                                    .subList(0, rowIndex)
                                                                    .count {
                                                                        it is HomeRowLoadingState.Success && it.items.isEmpty()
                                                                    }
                                                            onFocusPosition.invoke(
                                                                RowColumn(
                                                                    rowIndex - nonEmptyRowBefore,
                                                                    index,
                                                                ),
                                                            )
                                                        }
                                                    }.onKeyEvent {
                                                        if (isPlayKeyUp(it) && item?.type?.playable == true) {
                                                            Timber.v("Clicked play on ${item.id}")
                                                            onClickPlay.invoke(position, item)
                                                            return@onKeyEvent true
                                                        }
                                                        return@onKeyEvent false
                                                    },
                                            interactionSource = null,
                                            cardHeight = Cards.height2x3,
                                        )
                                    },
                                )
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
    modifier: Modifier = Modifier,
) {
    val isEpisode = item?.type == BaseItemKind.EPISODE
    val dto = item?.data
    HomePageHeader(
        title = item?.title,
        subtitle = if (isEpisode) dto?.name else null,
        overview = dto?.overview,
        overviewTwoLines = isEpisode,
        quickDetails = item?.ui?.quickDetails,
        timeRemaining = item?.timeRemainingOrRuntime,
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
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(.75f),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth(.6f)
                    .fillMaxHeight(),
        ) {
            subtitle?.let {
                EpisodeName(it)
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
