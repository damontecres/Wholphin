package com.github.damontecres.dolphin.ui.components.details

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.dolphin.R
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.dolphin.ui.cards.ItemCard
import com.github.damontecres.dolphin.ui.components.DotSeparatedRow
import com.github.damontecres.dolphin.ui.components.ExpandableFaButton
import com.github.damontecres.dolphin.ui.components.ExpandablePlayButton
import com.github.damontecres.dolphin.ui.components.StarRating
import com.github.damontecres.dolphin.ui.components.StarRatingPrecision
import com.github.damontecres.dolphin.ui.components.TitleValueText
import com.github.damontecres.dolphin.ui.detail.SeriesViewModel
import com.github.damontecres.dolphin.ui.ifElse
import com.github.damontecres.dolphin.ui.isNotNullOrBlank
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.ui.playOnClickSound
import com.github.damontecres.dolphin.ui.playSoundOnFocus
import com.github.damontecres.dolphin.ui.tryRequestFocus
import com.github.damontecres.dolphin.util.formatDateTime
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import kotlin.time.Duration

@Serializable
data class SeasonEpisode(
    val season: Int,
    val episode: Int,
)

@Composable
fun SeriesDetailParent(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel = hiltViewModel(),
    initialSeasonEpisode: SeasonEpisode = SeasonEpisode(0, 0),
) {
    val firstItemFocusRequester = remember { FocusRequester() }

    OneTimeLaunchedEffect {
        Timber.v("SeriesDetailParent: itemId=${destination.itemId}, initialSeasonEpisode=$initialSeasonEpisode")
        viewModel.init(destination.itemId, destination.item, initialSeasonEpisode.season)
    }
    val series by viewModel.item.observeAsState(null)
    val seasons by viewModel.seasons.observeAsState(listOf())
    val episodes by viewModel.episodes.observeAsState(listOf())
    var seasonEpisode by rememberSaveable(
        destination,
        stateSaver =
            Saver(
                save = { listOf(it.season, it.episode) },
                restore = { SeasonEpisode(it[0], it[1]) },
            ),
    ) { mutableStateOf(initialSeasonEpisode) }

    LaunchedEffect(episodes) {
        if (episodes.isNotEmpty()) {
            // TODO focus on first episode when changing seasons
//            firstItemFocusRequester.requestFocus()
        }
    }

    if (series == null) {
        // TODO
        Text(text = "Loading...")
    } else {
        series?.let { series ->
            SeriesDetailPage(
                series = series,
                seasons = seasons,
                episodes = episodes,
                seasonEpisode = seasonEpisode,
                backdropImageUrl = remember { viewModel.imageUrl(series.id, ImageType.BACKDROP) },
                firstItemFocusRequester = firstItemFocusRequester,
                onFocus = {
                    if (it.season != seasonEpisode.season) {
                        viewModel.loadEpisodes(seasons[it.season]!!.id)
                    }
                    seasonEpisode = it
                },
                onClick = {
                    val resumePosition =
                        it.data.userData
                            ?.playbackPositionTicks
                            ?.ticks ?: Duration.ZERO
                    navigationManager.navigateTo(Destination.Playback(it.id, resumePosition.inWholeMilliseconds, it))
                },
                onLongClick = {
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
fun SeriesDetailPage(
    series: BaseItem,
    seasons: List<BaseItem?>,
    episodes: List<BaseItem?>,
    seasonEpisode: SeasonEpisode,
    backdropImageUrl: String?,
    firstItemFocusRequester: FocusRequester,
    onFocus: (SeasonEpisode) -> Unit,
    onClick: (BaseItem) -> Unit,
    onLongClick: (BaseItem) -> Unit,
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
                        .fillMaxHeight(.5f)
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
                            .ifElse(focusRequesters.size > selectedTabIndex, { Modifier.focusRestorer(focusRequesters[selectedTabIndex]) })
                            .focusRequester(tabRowFocusRequester)
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
                                modifier =
                                    Modifier
                                        .focusRequester(focusRequesters[index]),
                            ) {
                                Text(
                                    text = season.name ?: "Season ${season.data.indexNumber}",
                                    style = MaterialTheme.typography.titleMedium,
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
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier,
                    )
                }
            }
            item {
                // Episode header
                focusedEpisode?.let { ep ->
                    FocusedEpisodeHeader(
                        ep = ep,
                        overviewOnClick = {
                            // TODO show full overview dialog
                        },
                        modifier = Modifier,
                    )
                }
            }
            item {
                key(seasonEpisode.season) {
                    val state = rememberLazyListState()
                    LazyRow(
                        state = state,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(start = 32.dp),
                        modifier = modifier.focusRestorer(firstItemFocusRequester),
                    ) {
                        itemsIndexed(episodes) { index, episode ->
                            val interactionSource = remember { MutableInteractionSource() }
                            if (interactionSource.collectIsFocusedAsState().value) {
                                onFocus.invoke(SeasonEpisode(selectedTabIndex, index))
                            }
                            ItemCard(
                                item = episode,
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
                        playOnClick = {},
                        moreOnClick = {},
                        watchOnClick = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun FocusedEpisodeHeader(
    ep: BaseItem,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dto = ep.data
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Text(
            text = dto.episodeTitle ?: dto.name ?: "",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val details =
                buildList {
                    if (dto.parentIndexNumber != null && dto.indexNumber != null) {
                        add("S${dto.parentIndexNumber} E${dto.indexNumber}")
                    }
                    dto.premiereDate?.let { add(formatDateTime(it)) }
                    dto.mediaSources?.firstOrNull()?.runTimeTicks?.ticks?.inWholeMinutes?.toString()?.let {
                        add(it)
                    }
                    dto.seriesStudio?.let { add(it) }
                }
            DotSeparatedRow(
                texts = details,
                textStyle = MaterialTheme.typography.titleLarge,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Critic: ${dto.criticRating}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier,
            )
            StarRating(
                rating100 =
                    dto
                        .userData
                        ?.rating
                        ?.times(100)
                        ?.toInt() ?: 0,
                onRatingChange = {},
                enabled = false,
                precision = StarRatingPrecision.HALF,
                playSoundOnFocus = true,
                modifier = Modifier.height(32.dp),
            )
        }
        dto.overview?.let { overview ->
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused = interactionSource.collectIsFocusedAsState().value
            val bgColor =
                if (isFocused) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f)
                } else {
                    Color.Unspecified
                }
            Box(
                modifier =
                    Modifier
                        .background(bgColor, shape = RoundedCornerShape(8.dp))
                        .playSoundOnFocus(true)
                        .clickable(
                            enabled = true,
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                        ) {
                            playOnClickSound(context)
                            overviewOnClick.invoke()
                        },
            ) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
fun FocusedEpisodeFooter(
    ep: BaseItem,
    playOnClick: (Duration) -> Unit,
    moreOnClick: () -> Unit,
    watchOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dto = ep.data
    val resumePosition = dto.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO
    val firstFocus = remember { FocusRequester() }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(8.dp),
            modifier =
                Modifier
                    .focusGroup()
                    .focusProperties {
                        onEnter = {
                            firstFocus.tryRequestFocus()
                        }
                    },
        ) {
            if (resumePosition > Duration.ZERO) {
                item {
//                LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
                    ExpandablePlayButton(
                        R.string.resume,
                        resumePosition,
                        Icons.Default.PlayArrow,
                        playOnClick,
                        Modifier.focusRequester(firstFocus),
                        // .onFocusChanged(buttonOnFocusChanged),
                    )
                }
                item {
                    ExpandablePlayButton(
                        R.string.restart,
                        Duration.ZERO,
                        Icons.Default.Refresh,
                        playOnClick,
                        Modifier,
                        // .onFocusChanged(buttonOnFocusChanged),
                    )
                }
            } else {
                item {
                    ExpandablePlayButton(
                        R.string.play,
                        Duration.ZERO,
                        Icons.Default.PlayArrow,
                        playOnClick,
                        Modifier.focusRequester(firstFocus),
                        // .onFocusChanged(buttonOnFocusChanged)
                    )
                }
            }

            val played = dto.userData?.played ?: false
            // Played button
            item {
                ExpandableFaButton(
                    title = if (played) R.string.mark_unwatched else R.string.mark_watched,
                    iconStringRes = if (played) R.string.fa_eye else R.string.fa_eye_slash,
                    onClick = watchOnClick,
                    modifier = Modifier,
                )
            }

            // More button
            item {
                ExpandablePlayButton(
                    R.string.more,
                    Duration.ZERO,
                    Icons.Default.MoreVert,
                    { moreOnClick.invoke() },
                    Modifier,
                    // .onFocusChanged(buttonOnFocusChanged)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            dto.mediaStreams
                ?.firstOrNull { it.type == MediaStreamType.VIDEO }
                ?.let { stream ->
                    stream.displayTitle?.let {
                        TitleValueText(
                            "Video",
                            it,
                        )
                    }
                }

            dto.mediaStreams
                ?.firstOrNull { it.type == MediaStreamType.AUDIO }
                ?.let { stream ->
                    stream.displayTitle?.let {
                        TitleValueText(
                            "Audio",
                            it,
                        )
                    }
                }

            dto.mediaStreams
                ?.filter { it.type == MediaStreamType.SUBTITLE && it.language.isNotNullOrBlank() }
                ?.mapNotNull { it.language }
                ?.joinToString(", ")
                ?.let {
                    if (it.isNotNullOrBlank()) {
                        TitleValueText(
                            "Subtitles",
                            it,
                            modifier = Modifier.widthIn(max = 64.dp),
                        )
                    }
                }
        }
    }
}
