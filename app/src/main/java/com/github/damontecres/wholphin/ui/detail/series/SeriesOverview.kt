@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.ui.detail.series

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.chooseSource
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.chooseStream
import com.github.damontecres.wholphin.ui.components.chooseVersionParams
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.detail.MoreDialogActions
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItems
import com.github.damontecres.wholphin.ui.equalsNotNull
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.seasonEpisode
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration

@Serializable
data class SeasonEpisode(
    val season: Int,
    val episode: Int,
)

@Serializable
data class SeasonEpisodeIds(
    val seasonId: UUID,
    val seasonNumber: Int?,
    val episodeId: UUID?,
    val episodeNumber: Int?,
)

@Serializable
data class SeriesOverviewPosition(
    val seasonTabIndex: Int,
    val episodeRowIndex: Int,
)

@Composable
fun SeriesOverview(
    preferences: UserPreferences,
    destination: Destination.SeriesOverview,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel = hiltViewModel(),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
    initialSeasonEpisode: SeasonEpisodeIds? = null,
) {
    val context = LocalContext.current
    val firstItemFocusRequester = remember { FocusRequester() }
    val episodeRowFocusRequester = remember { FocusRequester() }

    var initialLoadDone by rememberSaveable { mutableStateOf(false) }
    OneTimeLaunchedEffect {
        Timber.v("SeriesDetailParent: itemId=${destination.itemId}, initialSeasonEpisode=$initialSeasonEpisode")
        viewModel.init(
            preferences,
            destination.itemId,
            initialSeasonEpisode,
            false,
        )
        initialLoadDone = true
    }

    val loading by viewModel.loading.observeAsState(LoadingState.Loading)

    val series by viewModel.item.observeAsState(null)
    val seasons by viewModel.seasons.observeAsState(listOf())
    val episodes by viewModel.episodes.observeAsState(EpisodeList.Loading)
    val episodeList = (episodes as? EpisodeList.Success)?.episodes

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
                seasons.indexOfFirstOrNull {
                    equalsNotNull(it.id, initialSeasonEpisode?.seasonId) ||
                        equalsNotNull(it.indexNumber, initialSeasonEpisode?.seasonNumber)
                } ?: 0,
                (episodes as? EpisodeList.Success)?.initialIndex ?: 0,
            ),
        )
    }
    if (initialLoadDone) {
        LaunchedEffect(Unit) {
            seasons.getOrNull(position.seasonTabIndex)?.let {
                viewModel.loadEpisodes(it.id)
            }
        }
    }

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var moreDialog by remember { mutableStateOf<DialogParams?>(null) }
    var chooseVersion by remember { mutableStateOf<DialogParams?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<UUID?>(null) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)

    LaunchedEffect(episodes) {
        episodes?.let { episodes ->
            if (episodes is EpisodeList.Success) {
                if (episodes.episodes.isNotEmpty()) {
                    // TODO focus on first episode when changing seasons?
//            firstItemFocusRequester.requestFocus()
                    episodes.episodes.getOrNull(position.episodeRowIndex)?.let {
                        viewModel.refreshEpisode(it.id, position.episodeRowIndex)
                    }
                }
            }
        }
    }

    LaunchedEffect(position) {
        (episodes as? EpisodeList.Success)
            ?.episodes
            ?.getOrNull(position.episodeRowIndex)
            ?.let {
                viewModel.lookUpChosenTracks(it.id, it)
            }
    }
    val chosenStreams by viewModel.chosenStreams.observeAsState(null)

    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state)

        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage()

        LoadingState.Success -> {
            series?.let { series ->
                LaunchedEffect(Unit) { episodeRowFocusRequester.tryRequestFocus() }
                LifecycleStartEffect(destination.itemId) {
                    viewModel.maybePlayThemeSong(
                        destination.itemId,
                        preferences.appPreferences.interfacePreferences.playThemeSongs,
                    )
                    onStopOrDispose {
                        viewModel.release()
                    }
                }

                fun buildMoreForEpisode(
                    ep: BaseItem,
                    fromLongClick: Boolean,
                ): DialogParams =
                    DialogParams(
                        fromLongClick = fromLongClick,
                        title = series.name + " - " + ep.data.seasonEpisode,
                        items =
                            buildMoreDialogItems(
                                context = context,
                                item = ep,
                                watched = ep.data.userData?.played ?: false,
                                favorite = ep.data.userData?.isFavorite ?: false,
                                seriesId = series.id,
                                sourceId = chosenStreams?.sourceId,
                                actions =
                                    MoreDialogActions(
                                        navigateTo = viewModel::navigateTo,
                                        onClickWatch = { itemId, watched ->
                                            viewModel.setWatched(
                                                itemId,
                                                watched,
                                                position.episodeRowIndex,
                                            )
                                        },
                                        onClickFavorite = { itemId, favorite ->
                                            viewModel.setFavorite(
                                                itemId,
                                                favorite,
                                                position.episodeRowIndex,
                                            )
                                        },
                                        onClickAddPlaylist = {
                                            playlistViewModel.loadPlaylists(MediaType.VIDEO)
                                            showPlaylistDialog = it
                                        },
                                    ),
                                onChooseVersion = {
                                    chooseVersion =
                                        chooseVersionParams(
                                            context,
                                            ep.data.mediaSources!!,
                                        ) { idx ->
                                            val source = ep.data.mediaSources!![idx]
                                            viewModel.savePlayVersion(
                                                ep,
                                                source.id!!.toUUID(),
                                            )
                                        }
                                    moreDialog = null
                                },
                                onChooseTracks = { type ->
                                    chooseSource(
                                        ep.data,
                                        chosenStreams?.itemPlayback,
                                    )?.let { source ->
                                        chooseVersion =
                                            chooseStream(
                                                context = context,
                                                streams = source.mediaStreams.orEmpty(),
                                                type = type,
                                                onClick = { trackIndex ->
                                                    viewModel.saveTrackSelection(
                                                        ep,
                                                        chosenStreams?.itemPlayback,
                                                        trackIndex,
                                                        type,
                                                    )
                                                },
                                            )
                                    }
                                },
                            ),
                    )

                SeriesOverviewContent(
                    preferences = preferences,
                    series = series,
                    seasons = seasons,
                    episodes = episodes,
                    chosenStreams = chosenStreams,
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
                            seasons.getOrNull(it.seasonTabIndex)?.let { season ->
                                viewModel.loadEpisodes(season.id)
                            }
                        }
                        position = it
                    },
                    onClick = {
                        val resumePosition =
                            it.data.userData
                                ?.playbackPositionTicks
                                ?.ticks ?: Duration.ZERO
                        viewModel.navigateTo(
                            Destination.Playback(
                                it.id,
                                resumePosition.inWholeMilliseconds,
                                it,
                            ),
                        )
                    },
                    onLongClick = { ep ->
                        moreDialog = buildMoreForEpisode(ep, true)
                    },
                    playOnClick = { resume ->
                        episodeList?.getOrNull(position.episodeRowIndex)?.let {
                            viewModel.release()
                            viewModel.navigateTo(
                                Destination.Playback(
                                    it.id,
                                    resume.inWholeMilliseconds,
                                    it,
                                ),
                            )
                        }
                    },
                    watchOnClick = {
                        episodeList?.getOrNull(position.episodeRowIndex)?.let {
                            val played = it.data.userData?.played ?: false
                            viewModel.setWatched(it.id, !played, position.episodeRowIndex)
                        }
                    },
                    favoriteOnClick = {
                        episodeList?.getOrNull(position.episodeRowIndex)?.let {
                            val favorite = it.data.userData?.isFavorite ?: false
                            viewModel.setFavorite(it.id, !favorite, position.episodeRowIndex)
                        }
                    },
                    moreOnClick = {
                        episodeList?.getOrNull(position.episodeRowIndex)?.let { ep ->
                            moreDialog = buildMoreForEpisode(ep, false)
                        }
                    },
                    overviewOnClick = {
                        episodeList?.getOrNull(position.episodeRowIndex)?.let {
                            overviewDialog =
                                ItemDetailsDialogInfo(
                                    title = it.name ?: context.getString(R.string.unknown),
                                    overview = it.data.overview,
                                    genres = it.data.genres.orEmpty(),
                                    files = it.data.mediaSources.orEmpty(),
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
            showFilePath =
                viewModel.serverRepository.currentUserDto.value
                    ?.policy
                    ?.isAdministrator == true,
            onDismissRequest = { overviewDialog = null },
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
    chooseVersion?.let { params ->
        DialogPopup(
            showDialog = true,
            title = params.title,
            dialogItems = params.items,
            onDismissRequest = { chooseVersion = null },
            dismissOnClick = true,
            waitToLoad = params.fromLongClick,
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
