package com.github.damontecres.dolphin.ui.detail.series

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.ui.cards.BannerCard
import com.github.damontecres.dolphin.ui.ifElse
import com.github.damontecres.dolphin.ui.isNotNullOrBlank
import kotlin.time.Duration

@Composable
fun SeriesOverviewContent(
    series: BaseItem,
    seasons: List<BaseItem?>,
    episodes: List<BaseItem?>,
    seasonEpisode: SeasonEpisode,
    backdropImageUrl: String?,
    firstItemFocusRequester: FocusRequester,
    onFocus: (SeasonEpisode) -> Unit,
    onClick: (BaseItem) -> Unit,
    onLongClick: (BaseItem) -> Unit,
    playOnClick: (Duration) -> Unit,
    watchOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    // TODO need to map between season index and tab index in case of missing seasons
    var selectedTabIndex by rememberSaveable(seasonEpisode) { mutableIntStateOf(seasonEpisode.season) }
    val focusRequesters = remember(seasons.size) { List(seasons.size) { FocusRequester() } }
    var resolvedTabIndex by remember { mutableIntStateOf(selectedTabIndex) }
    val tabRowFocusRequester = remember { FocusRequester() }

    val focusedEpisode = episodes.getOrNull(seasonEpisode.episode)

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
                    selectedTabIndex = selectedTabIndex,
                    modifier =
                        Modifier
                            .ifElse(
                                focusRequesters.size > selectedTabIndex,
                                { Modifier.focusRestorer(focusRequesters[selectedTabIndex]) },
                            ).focusRequester(tabRowFocusRequester)
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                ) {
                    seasons.forEachIndexed { index, season ->
                        season?.let { season ->
                            Tab(
                                selected = index == selectedTabIndex,
                                onFocus = {},
                                onClick = {
                                    selectedTabIndex = index
                                    onFocus.invoke(SeasonEpisode(index, 0))
                                },
                                colors =
                                    TabDefaults.pillIndicatorTabColors(
                                        // TODO
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
                key(seasonEpisode.season) {
                    val state = rememberLazyListState()
                    LazyRow(
                        state = state,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(start = 16.dp),
                        modifier = modifier.focusRestorer(firstItemFocusRequester),
                    ) {
                        itemsIndexed(episodes) { index, episode ->
                            val interactionSource = remember { MutableInteractionSource() }
                            if (interactionSource.collectIsFocusedAsState().value) {
                                onFocus.invoke(SeasonEpisode(selectedTabIndex, index))
                            }
                            BannerCard(
                                imageUrl = episode?.imageUrl,
                                cornerText = "E${episode?.data?.indexNumber}",
                                played = episode?.data?.userData?.played ?: false,
                                playPercent = episode?.data?.userData?.playedPercentage ?: 0.0,
                                onClick = { if (episode != null) onClick.invoke(episode) },
                                onLongClick = { if (episode != null) onLongClick.invoke(episode) },
                                modifier =
                                    Modifier.ifElse(
                                        index == 0,
                                        Modifier.focusRequester(firstItemFocusRequester),
                                    ),
                                interactionSource = interactionSource,
                            )
                        }
                    }
                }
            }
            item {
                focusedEpisode?.let { ep ->
                    FocusedEpisodeFooter(
                        ep = ep,
                        playOnClick = playOnClick,
                        moreOnClick = moreOnClick,
                        watchOnClick = watchOnClick,
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
