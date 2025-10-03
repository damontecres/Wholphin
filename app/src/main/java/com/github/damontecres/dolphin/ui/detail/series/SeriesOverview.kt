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
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.dolphin.ui.components.ErrorMessage
import com.github.damontecres.dolphin.ui.components.LoadingPage
import com.github.damontecres.dolphin.ui.data.ItemDetailsDialog
import com.github.damontecres.dolphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.dolphin.ui.detail.ItemListAndMapping
import com.github.damontecres.dolphin.ui.detail.SeriesViewModel
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.ui.tryRequestFocus
import com.github.damontecres.dolphin.util.LoadingState
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

@Serializable
data class SeriesOverviewPosition(
    val seasonTabIndex: Int,
    val episodeRowIndex: Int,
)

@Composable
fun SeriesOverview(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel = hiltViewModel(),
    initialSeasonEpisode: SeasonEpisode? = null,
) {
    val firstItemFocusRequester = remember { FocusRequester() }
    val episodeRowFocusRequester = remember { FocusRequester() }

    OneTimeLaunchedEffect {
        Timber.v("SeriesDetailParent: itemId=${destination.itemId}, initialSeasonEpisode=$initialSeasonEpisode")
        viewModel.init(
            destination.itemId,
            destination.item,
            initialSeasonEpisode?.season,
            initialSeasonEpisode?.episode,
        )
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)

    val series by viewModel.item.observeAsState(null)
    val seasons by viewModel.seasons.observeAsState(ItemListAndMapping.empty())
    val episodes by viewModel.episodes.observeAsState(ItemListAndMapping.empty())

    // TODO need to translate season/episode into tab/row index in case of missing seasons/episodes
    var position by rememberSaveable(
        destination,
        loading,
        stateSaver =
            Saver(
                save = { listOf(it.seasonTabIndex, it.episodeRowIndex) },
                restore = { SeriesOverviewPosition(it[0], it[1]) },
            ),
    ) {
        mutableStateOf(
            SeriesOverviewPosition(
                seasons.numberToIndex[initialSeasonEpisode?.season ?: 0] ?: 0,
                episodes.numberToIndex[initialSeasonEpisode?.episode ?: 0] ?: 0,
            ),
        )
    }

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }

    LaunchedEffect(episodes.items) {
        if (episodes.items.isNotEmpty()) {
            // TODO focus on first episode when changing seasons
//            firstItemFocusRequester.requestFocus()
            episodes.items.getOrNull(position.episodeRowIndex)?.let {
                viewModel.refreshEpisode(it.id, position.episodeRowIndex)
            }
        }
    }

    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state)

        LoadingState.Loading -> LoadingPage()

        LoadingState.Success -> {
            series?.let { series ->
                LaunchedEffect(Unit) { episodeRowFocusRequester.tryRequestFocus() }
                SeriesOverviewContent(
                    series = series,
                    seasons = seasons.items,
                    episodes = episodes.items,
                    position = position,
                    backdropImageUrl =
                        remember {
                            viewModel.imageUrl(
                                series.id,
                                ImageType.BACKDROP,
                            )
                        },
                    firstItemFocusRequester = firstItemFocusRequester,
                    episodeRowFocusRequester = episodeRowFocusRequester,
                    onFocus = {
                        if (it.seasonTabIndex != position.seasonTabIndex) {
                            seasons.indexToNumber[it.seasonTabIndex]?.let { seasonNumber ->
                                viewModel.loadEpisodes(seasonNumber)
                            }
                        }
                        position = it
                    },
                    onClick = {
                        viewModel.release()
                        val resumePosition =
                            it.data.userData
                                ?.playbackPositionTicks
                                ?.ticks ?: Duration.ZERO
                        navigationManager.navigateTo(
                            Destination.Playback(
                                it.id,
                                resumePosition.inWholeMilliseconds,
                                it,
                            ),
                        )
                    },
                    onLongClick = {
                        // TODO
                    },
                    playOnClick = { resume ->
                        episodes.items.getOrNull(position.episodeRowIndex)?.let {
                            viewModel.release()
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
                        episodes.items.getOrNull(position.episodeRowIndex)?.let {
                            val played = it.data.userData?.played ?: false
                            viewModel.setWatched(it.id, !played, position.episodeRowIndex)
                        }
                    },
                    moreOnClick = {
                        // TODO show more actions
                    },
                    overviewOnClick = {
                        episodes.items.getOrNull(position.episodeRowIndex)?.let {
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
    }

    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            onDismissRequest = { overviewDialog = null },
            modifier = Modifier,
        )
    }
}
