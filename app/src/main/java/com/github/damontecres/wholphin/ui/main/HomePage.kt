package com.github.damontecres.wholphin.ui.main

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
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
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.data.RowColumnSaver
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItemsForHome
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.timeRemaining
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.formatDateTime
import com.github.damontecres.wholphin.util.seasonEpisode
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration

data class HomeRow(
    val section: HomeSection,
    val items: List<BaseItem?>,
    val title: String? = null,
)

@Composable
fun HomePage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    var firstLoad by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        viewModel.init(preferences).join()
        firstLoad = false
    }
    val loading by viewModel.loadingState.observeAsState(LoadingState.Loading)
    val homeRows by viewModel.homeRows.observeAsState(listOf())
    when (val state = if (firstLoad) loading else LoadingState.Success) {
        is LoadingState.Error -> ErrorMessage(state)

        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage()

        LoadingState.Success -> {
            var dialog by remember { mutableStateOf<DialogParams?>(null) }
            HomePageContent(
                homeRows,
                onClickItem = {
                    viewModel.navigationManager.navigateTo(it.destination())
                },
                onLongClickItem = {
                    val dialogItems =
                        buildMoreDialogItemsForHome(
                            item = it,
                            seriesId = it.data.seriesId,
                            playbackPosition =
                                it.data.userData
                                    ?.playbackPositionTicks
                                    ?.ticks
                                    ?: Duration.ZERO,
                            watched = it.data.userData?.played ?: false,
                            favorite = it.data.userData?.isFavorite ?: false,
                            navigateTo = viewModel.navigationManager::navigateTo,
                            onClickWatch = { itemId, played ->
                                viewModel.setWatched(itemId, played)
                            },
                            onClickFavorite = { itemId, favorite ->
                                viewModel.setFavorite(itemId, favorite)
                            },
                        )
                    dialog =
                        DialogParams(
                            title = it.title ?: "",
                            fromLongClick = true,
                            items = dialogItems,
                        )
                },
                loadingState = loading,
                modifier = modifier,
            )
            dialog?.let { params ->
                DialogPopup(
                    params = params,
                    onDismissRequest = { dialog = null },
                )
            }
        }
    }
}

@Composable
fun HomePageContent(
    homeRows: List<HomeRow>,
    onClickItem: (BaseItem) -> Unit,
    onLongClickItem: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    onFocusPosition: ((RowColumn) -> Unit)? = null,
    loadingState: LoadingState? = null,
) {
    val scope = rememberCoroutineScope()
    var position by rememberSaveable(stateSaver = RowColumnSaver) {
        mutableStateOf(RowColumn(0, 0))
    }
    var focusedItem = position.let { homeRows.getOrNull(it.row)?.items?.getOrNull(it.column) }

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
            MainPageHeader(
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
                    if (row.items.isNotEmpty()) {
                        ItemRow(
                            title = row.title ?: stringResource(row.section.nameRes),
                            items = row.items,
                            onClickItem = onClickItem,
                            cardOnFocus = { isFocused, index ->
                                if (isFocused) {
                                    focusedItem = row.items.getOrNull(index)
                                    position = RowColumn(rowIndex, index)
                                }
                            },
                            onLongClickItem = onLongClickItem,
                            modifier = Modifier.fillMaxWidth(),
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
                                    playPercent = item?.data?.userData?.playedPercentage ?: 0.0,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    modifier =
                                        cardModifier
                                            .ifElse(
                                                focusedItem == item,
                                                Modifier.focusRequester(focusRequester),
                                            ).ifElse(
                                                RowColumn(rowIndex, index) == position,
                                                Modifier.focusRequester(positionFocusRequester),
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
        when (loadingState) {
            LoadingState.Pending,
            LoadingState.Loading,
            ->
                Box(
                    modifier =
                        Modifier
                            .padding(16.dp)
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
fun MainPageHeader(
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
