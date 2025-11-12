package com.github.damontecres.wholphin.ui.main

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.BannerCard
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.DotSeparatedRow
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.data.RowColumnSaver
import com.github.damontecres.wholphin.ui.detail.MoreDialogActions
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItemsForHome
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.timeRemaining
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.formatDateTime
import com.github.damontecres.wholphin.util.seasonEpisode
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.extensions.ticks
import java.util.UUID

@Composable
fun HomePage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var firstLoad by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        viewModel.init(preferences).join()
        firstLoad = false
    }
    val loading by viewModel.loadingState.observeAsState(LoadingState.Loading)
    val refreshing by viewModel.refreshState.observeAsState(LoadingState.Loading)
    val watchingRows by viewModel.watchingRows.observeAsState(listOf())
    val latestRows by viewModel.latestRows.observeAsState(listOf())
    LaunchedEffect(loading) {
        val state = loading
        if (!firstLoad && state is LoadingState.Error) {
            // After the first load, refreshes occur in the background and an ErrorMessage won't show
            // So send a Toast on errors instead
            Toast
                .makeText(
                    context,
                    "Home refresh error: ${state.localizedMessage}",
                    Toast.LENGTH_LONG,
                ).show()
        }
    }

    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state)

        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage()

        LoadingState.Success -> {
            var dialog by remember { mutableStateOf<DialogParams?>(null) }
            var showPlaylistDialog by remember { mutableStateOf<UUID?>(null) }
            val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)
            HomePageContent(
                watchingRows + latestRows,
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
                loadingState = refreshing,
                showClock = preferences.appPreferences.interfacePreferences.showClock,
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
    showClock: Boolean,
    modifier: Modifier = Modifier,
    onFocusPosition: ((RowColumn) -> Unit)? = null,
    loadingState: LoadingState? = null,
) {
    val scope = rememberCoroutineScope()
    var position by rememberSaveable(stateSaver = RowColumnSaver) {
        mutableStateOf(RowColumn(0, 0))
    }
    var focusedItem =
        position.let {
            (homeRows.getOrNull(it.row) as? HomeRowLoadingState.Success)?.items?.getOrNull(it.column)
        }

    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val positionFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        positionFocusRequester.tryRequestFocus()
        // Hacky, but mostly works
        delay(50)
        listState.animateScrollToItem(position.row)
    }
    LaunchedEffect(position) {
        listState.animateScrollToItem(position.row)
    }
    Box(modifier = modifier) {
        if (focusedItem?.backdropImageUrl.isNotNullOrBlank()) {
            val gradientColor = MaterialTheme.colorScheme.background
            AsyncImage(
                model = focusedItem?.backdropImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopEnd,
                modifier =
                    Modifier
                        .fillMaxHeight(.7f)
                        .fillMaxWidth(.7f)
                        .alpha(.75f)
                        .align(Alignment.TopEnd)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, gradientColor),
                                    startY = size.height * .33f,
                                ),
                            )
                            drawRect(
                                Brush.horizontalGradient(
                                    colors = listOf(gradientColor, Color.Transparent),
                                    startX = 0f,
                                    endX = size.width * .5f,
                                ),
                            )
                        },
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            HomePageHeader(
                item = focusedItem,
                modifier =
                    Modifier
                        .fillMaxWidth(.6f)
                        .fillMaxHeight(.33f)
                        .padding(16.dp),
            )
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 0.dp,
                        bottom = Cards.height2x3,
                    ),
                modifier = Modifier,
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
                                    cardOnFocus = { isFocused, index ->
                                        if (isFocused) {
                                            focusedItem = row.items.getOrNull(index)
                                            position = RowColumn(rowIndex, index)
                                        }
                                    },
                                    onLongClickItem = { index, item ->
                                        onLongClickItem.invoke(RowColumn(rowIndex, index), item)
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .animateItem(),
                                    cardContent = { index, item, cardModifier, onClick, onLongClick ->
                                        // TODO better aspect ration handling?
                                        BannerCard(
                                            name = item?.data?.seriesName ?: item?.name,
                                            imageUrl = item?.imageUrl,
                                            aspectRatio = (2f / 3f),
                                            cornerText =
                                                item?.data?.indexNumber?.let { "E$it" }
                                                    ?: item?.data?.childCount?.let { if (it > 0) it.toString() else null },
                                            played = item?.data?.userData?.played ?: false,
                                            favorite = item?.favorite ?: false,
                                            playPercent =
                                                item?.data?.userData?.playedPercentage
                                                    ?: 0.0,
                                            onClick = onClick,
                                            onLongClick = onLongClick,
                                            modifier =
                                                cardModifier
                                                    .ifElse(
                                                        focusedItem == item,
                                                        Modifier.focusRequester(focusRequester),
                                                    ).ifElse(
                                                        RowColumn(rowIndex, index) == position,
                                                        Modifier.focusRequester(
                                                            positionFocusRequester,
                                                        ),
                                                    ).onFocusChanged {
                                                        if (it.isFocused) {
                                                            onFocusPosition?.invoke(
                                                                RowColumn(
                                                                    rowIndex,
                                                                    index,
                                                                ),
                                                            )
                                                        }
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
            ->
                Box(
                    modifier =
                        Modifier
                            .padding(if (showClock) 40.dp else 20.dp)
                            .size(40.dp)
                            .align(Alignment.TopEnd),
                ) {
                    CircularProgress(Modifier.fillMaxSize())
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
    Box(
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item?.let {
                val dto = item.data
                val isEpisode = item.type == BaseItemKind.EPISODE
                val title = if (isEpisode) dto.seriesName ?: item.name else item.name
                val subtitle = if (isEpisode) dto.name else null
                val overview = dto.overview
                val details =
                    buildList {
                        if (isEpisode) {
                            val se = dto.seasonEpisode
                            if (se != null) {
                                add(se)
                            } else if (dto.parentIndexNumber != null) {
                                // Maybe a daily episode, so just show season, the date is added below
                                add("S${dto.parentIndexNumber}")
                            }
                        }
                        if (isEpisode) {
                            dto.premiereDate?.let { add(formatDateTime(it)) }
                        } else {
                            dto.productionYear?.let { add(it.toString()) }
                        }
                        dto.runTimeTicks?.ticks?.roundMinutes?.let {
                            add(it.toString())
                        }
                        dto.timeRemaining?.roundMinutes?.let {
                            add("$it left")
                        }
                        dto.officialRating?.let(::add)
                    }
                title?.let {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                subtitle?.let {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (details.isNotEmpty()) {
                    DotSeparatedRow(
                        texts = details,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier,
                    )
                }
                val overviewModifier =
                    Modifier
                        .padding(0.dp)
                        .height(48.dp + if (!isEpisode) 12.dp else 0.dp)
                if (overview.isNotNullOrBlank()) {
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (isEpisode) 2 else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = overviewModifier,
                    )
                } else {
                    Spacer(overviewModifier)
                }
            }
        }
    }
}
