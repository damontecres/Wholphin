package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.aspectRatioFloat
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.cards.BannerCard
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.detail.EpisodeList
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.formatDateTime
import kotlin.time.Duration

@Composable
fun SeriesOverviewContent(
    preferences: UserPreferences,
    series: BaseItem,
    seasons: List<BaseItem?>,
    episodes: EpisodeList,
    chosenStreams: ChosenStreams?,
    position: SeriesOverviewPosition,
    backdropImageUrl: String?,
    firstItemFocusRequester: FocusRequester,
    episodeRowFocusRequester: FocusRequester,
    onFocus: (SeriesOverviewPosition) -> Unit,
    onClick: (BaseItem) -> Unit,
    onLongClick: (BaseItem) -> Unit,
    playOnClick: (Duration) -> Unit,
    watchOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var focusTabIndex by rememberSaveable(position) { mutableIntStateOf(position.seasonTabIndex) }
    var selectedTabIndex by rememberSaveable(position) { mutableIntStateOf(position.seasonTabIndex) }
    val focusRequesters = remember(seasons.size) { List(seasons.size) { FocusRequester() } }
    var resolvedTabIndex by remember { mutableIntStateOf(selectedTabIndex) }
    val tabRowFocusRequester = remember { FocusRequester() }

    val focusedEpisode =
        (episodes as? EpisodeList.Success)?.episodes?.items?.getOrNull(position.episodeRowIndex)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
//                .fillMaxHeight(.33f)
                .height(460.dp)
                .bringIntoViewRequester(bringIntoViewRequester),
    ) {
        if (backdropImageUrl.isNotNullOrBlank()) {
            val gradientColor = MaterialTheme.colorScheme.background
            AsyncImage(
                model = backdropImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd,
                modifier =
                    Modifier
                        .fillMaxHeight(.6f)
                        .alpha(.4f)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, gradientColor),
                                    startY = 500f,
                                ),
                            )
                            drawRect(
                                Brush.horizontalGradient(
                                    colors = listOf(gradientColor, Color.Transparent),
                                    endX = 400f,
                                    startX = 100f,
                                ),
                            )
                        },
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier,
        ) {
            item {
                TabRow(
                    selectedTabIndex = focusTabIndex,
                    modifier =
                        Modifier
                            .ifElse(
                                focusRequesters.size > selectedTabIndex,
                                { Modifier.focusRestorer(focusRequesters[selectedTabIndex]) },
                            ).focusRequester(tabRowFocusRequester)
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .onFocusChanged {
                                if (!it.isFocused) {
                                    focusTabIndex = selectedTabIndex
                                }
                            },
                    indicator =
                        @Composable { tabPositions, doesTabRowHaveFocus ->
                            tabPositions.getOrNull(focusTabIndex)?.let { currentTabPosition ->
//                        TabRowDefaults.PillIndicator(
//                            currentTabPosition = currentTabPosition,
//                            doesTabRowHaveFocus = doesTabRowHaveFocus,
//                        )
                                TabRowDefaults.UnderlinedIndicator(
                                    currentTabPosition = currentTabPosition,
                                    doesTabRowHaveFocus = doesTabRowHaveFocus,
                                    activeColor = MaterialTheme.colorScheme.border,
                                )
                            }
                        },
                ) {
                    seasons.forEachIndexed { index, season ->
                        season?.let { season ->
                            Tab(
                                selected = focusTabIndex == index,
                                onFocus = { focusTabIndex = index },
                                onClick = {
                                    selectedTabIndex = index
                                    onFocus.invoke(SeriesOverviewPosition(index, 0))
                                },
                                colors =
                                    TabDefaults.pillIndicatorTabColors(
                                        focusedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                modifier =
                                    Modifier
                                        .focusRequester(focusRequesters[index]),
                            ) {
                                Text(
                                    text = season.name ?: "Season ${season.data.indexNumber}",
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        }
                    }
                }
            }
            item {
                series.name?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier,
                    )
                }
            }
            item {
                // Episode header
                focusedEpisode?.let { ep ->
                    FocusedEpisodeHeader(
                        ep = ep,
                        overviewOnClick = overviewOnClick,
                        modifier = Modifier.fillMaxWidth(.66f),
                    )
                }
            }
            item {
                key(position.seasonTabIndex) {
                    when (val eps = episodes) {
                        EpisodeList.Loading -> LoadingPage()
                        is EpisodeList.Error -> ErrorMessage(eps.message, eps.exception)
                        is EpisodeList.Success -> {
                            val state = rememberLazyListState()
                            OneTimeLaunchedEffect {
                                if (state.firstVisibleItemIndex != position.episodeRowIndex) {
                                    state.scrollToItem(position.episodeRowIndex)
                                }
                                firstItemFocusRequester.tryRequestFocus()
                            }
                            LazyRow(
                                state = state,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                modifier =
                                    Modifier
                                        .focusRestorer(firstItemFocusRequester)
                                        .focusRequester(episodeRowFocusRequester),
                            ) {
                                itemsIndexed(eps.episodes.items) { episodeIndex, episode ->
                                    val interactionSource = remember { MutableInteractionSource() }
                                    if (interactionSource.collectIsFocusedAsState().value) {
                                        onFocus.invoke(
                                            SeriesOverviewPosition(
                                                selectedTabIndex,
                                                episodeIndex,
                                            ),
                                        )
                                    }
                                    val cornerText =
                                        episode?.data?.indexNumber?.let { "E$it" }
                                            ?: episode?.data?.premiereDate?.let(::formatDateTime)
                                    BannerCard(
                                        name = episode?.name,
                                        imageUrl = episode?.imageUrl,
                                        aspectRatio =
                                            episode
                                                ?.data
                                                ?.primaryImageAspectRatio
                                                ?.toFloat()
                                                ?.coerceAtLeast(
                                                    episode.data.aspectRatioFloat ?: (4f / 3f),
                                                )
                                                ?: (16f / 9), // TODO some episode images don't match the file's aspect ratio
                                        cornerText = cornerText,
                                        played = episode?.data?.userData?.played ?: false,
                                        playPercent =
                                            episode?.data?.userData?.playedPercentage
                                                ?: 0.0,
                                        onClick = { if (episode != null) onClick.invoke(episode) },
                                        onLongClick = {
                                            if (episode != null) {
                                                onLongClick.invoke(
                                                    episode,
                                                )
                                            }
                                        },
                                        modifier =
                                            Modifier.ifElse(
                                                episodeIndex == position.episodeRowIndex,
                                                Modifier.focusRequester(firstItemFocusRequester),
                                            ),
                                        interactionSource = interactionSource,
                                        cardHeight = 120.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                focusedEpisode?.let { ep ->
                    FocusedEpisodeFooter(
                        preferences = preferences,
                        ep = ep,
                        chosenStreams = chosenStreams,
                        playOnClick = playOnClick,
                        moreOnClick = moreOnClick,
                        watchOnClick = {
                            watchOnClick.invoke()
                            episodeRowFocusRequester.tryRequestFocus()
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp),
                    )
                }
            }
        }
    }
}
