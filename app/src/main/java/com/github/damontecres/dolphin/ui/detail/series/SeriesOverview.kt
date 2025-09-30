package com.github.damontecres.dolphin.ui.detail.series

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.dolphin.ui.data.ItemDetailsDialog
import com.github.damontecres.dolphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.dolphin.ui.detail.SeriesViewModel
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import kotlin.time.Duration

@Serializable
data class SeasonEpisode(
    val season: Int,
    val episode: Int,
)

@Composable
fun SeriesOverview(
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

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }

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
            SeriesOverviewContent(
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
                    // TODO
                },
                playOnClick = { resume ->
                    episodes.getOrNull(seasonEpisode.episode)?.let {
                        navigationManager.navigateTo(
                            Destination.Playback(
                                it.id,
                                resume.inWholeMilliseconds,
                                it,
                            ),
                        )
                    }
                },
                watchOnClick = {
                    // TODO toggle watched state
                },
                moreOnClick = {
                    // TODO show more actions
                },
                overviewOnClick = {
                    episodes.getOrNull(seasonEpisode.episode)?.let {
                        overviewDialog =
                            ItemDetailsDialogInfo(
                                title = it.name ?: "Unknown",
                                overview = it.data.overview,
                                files =
                                    it.data.mediaSources
                                        ?.mapNotNull { it.path }
                                        .orEmpty(),
                            )
                    }
                },
                modifier = modifier,
            )
        }
    }

    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            onDismissRequest = { overviewDialog = null },
            modifier = Modifier,
        )
    }
}
