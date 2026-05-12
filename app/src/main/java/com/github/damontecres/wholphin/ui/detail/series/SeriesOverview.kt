@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.map
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.RequestOrRestoreFocus
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import org.jellyfin.sdk.model.serializer.toUUID
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
    initialSeasonEpisode: SeasonEpisodeIds?,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel =
        hiltViewModel<SeriesViewModel, SeriesViewModel.Factory>(
            creationCallback = {
                it.create(destination.itemId, initialSeasonEpisode, SeriesPageType.OVERVIEW)
            },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firstItemFocusRequester = remember { FocusRequester() }
    val episodeRowFocusRequester = remember { FocusRequester() }
    val castCrewRowFocusRequester = remember { FocusRequester() }
    val guestStarRowFocusRequester = remember { FocusRequester() }
    val extrasRowFocusRequester = remember { FocusRequester() }

    val loading by viewModel.loading.observeAsState(LoadingState.Loading)

    val series by viewModel.item.observeAsState(null)
    val seasons by viewModel.seasons.observeAsState(emptyList())
    val episodes by viewModel.episodes.observeAsState(EpisodeList.Loading)
    val seasonExtras by viewModel.extras.observeAsState(emptyList())
    val peopleInEpisode by viewModel.peopleInEpisode.map { it.people }.observeAsState(emptyList())
    val episodeList = (episodes as? EpisodeList.Success)?.episodes

    val position by viewModel.position.collectAsState(SeriesOverviewPosition(0, 0))
    val currentPosition by rememberUpdatedState(position)
    LaunchedEffect(Unit) {
        if (seasons.isNotEmpty()) {
            seasons.getOrNull(position.seasonTabIndex)?.let {
                viewModel.loadEpisodes(it.id)
            }
        }
    }

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<UUID?>(null) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)

    var rowFocused by rememberInt()

    val contextActions =
        remember {
            ContextMenuActions(
                navigateTo = viewModel::navigateTo,
                onClickWatch = { itemId, watched ->
                    viewModel.setWatched(itemId, watched, currentPosition.episodeRowIndex)
                },
                onClickFavorite = { itemId, favorite ->
                    viewModel.setFavorite(itemId, favorite, currentPosition.episodeRowIndex)
                },
                onClickAddPlaylist = { itemId ->
                    playlistViewModel.loadPlaylists(MediaType.VIDEO)
                    showPlaylistDialog = itemId
                },
                onSendMediaInfo = viewModel.mediaReportService::sendReportFor,
                onDeleteItem = viewModel::deleteItem,
                onChooseVersion = { item, source ->
                    viewModel.savePlayVersion(
                        item,
                        source.id!!.toUUID(),
                    )
                },
                onChooseTracks = { result ->
                    viewModel.saveTrackSelection(
                        result.item,
                        result.itemPlayback,
                        result.trackIndex,
                        result.streamType,
                    )
                },
                onShowOverview = { overviewDialog = ItemDetailsDialogInfo(it) },
                onClearChosenStreams = {
                    val focusedEpisode =
                        (episodes as? EpisodeList.Success)
                            ?.episodes
                            ?.getOrNull(currentPosition.episodeRowIndex)
                    if (focusedEpisode != null) {
                        viewModel.clearChosenStreams(focusedEpisode, it)
                    }
                },
            )
        }

    LaunchedEffect(position, episodes) {
        val focusedEpisode =
            (episodes as? EpisodeList.Success)
                ?.episodes
                ?.getOrNull(position.episodeRowIndex)

        focusedEpisode?.let {
            viewModel.lookUpChosenTracks(it.id, it)
            viewModel.lookupPeopleInEpisode(it)
        }
    }
    val chosenStreams by viewModel.chosenStreams.observeAsState(null)

    val preferredSubtitleLanguage =
        viewModel.serverRepository.currentUserDto
            .observeAsState()
            .value
            ?.configuration
            ?.subtitleLanguagePreference

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
            series?.let { series ->

                RequestOrRestoreFocus(
                    when (rowFocused) {
                        EPISODE_ROW -> episodeRowFocusRequester
                        CAST_AND_CREW_ROW -> castCrewRowFocusRequester
                        GUEST_STAR_ROW -> guestStarRowFocusRequester
                        EXTRAS_ROW -> extrasRowFocusRequester
                        else -> episodeRowFocusRequester
                    },
                    "series_overview",
                )
                LifecycleResumeEffect(destination.itemId) {
                    viewModel.onResumePage()

                    onPauseOrDispose {
                        viewModel.release()
                    }
                }

                SeriesOverviewContent(
                    preferences = preferences,
                    series = series,
                    seasons = seasons,
                    episodes = episodes,
                    chosenStreams = chosenStreams,
                    peopleInEpisode = peopleInEpisode,
                    seasonExtras = seasonExtras,
                    position = position,
                    firstItemFocusRequester = firstItemFocusRequester,
                    episodeRowFocusRequester = episodeRowFocusRequester,
                    castCrewRowFocusRequester = castCrewRowFocusRequester,
                    guestStarRowFocusRequester = guestStarRowFocusRequester,
                    extrasRowFocusRequester = extrasRowFocusRequester,
                    onChangeSeason = { index ->
                        if (index != position.seasonTabIndex) {
                            seasons.getOrNull(index)?.let { season ->
                                viewModel.loadEpisodes(season.id)
                                viewModel.position.update {
                                    SeriesOverviewPosition(index, 0)
                                }
                            }
                        }
                    },
                    onFocusEpisode = { episodeIndex ->
                        viewModel.position.update {
                            it.copy(episodeRowIndex = episodeIndex)
                        }
                    },
                    onClick = {
                        rowFocused = EPISODE_ROW
                        val resumePosition =
                            it.data.userData
                                ?.playbackPositionTicks
                                ?.ticks ?: Duration.ZERO
                        viewModel.navigateTo(
                            Destination.Playback(
                                it.id,
                                resumePosition.inWholeMilliseconds,
                            ),
                        )
                    },
                    onLongClick = { ep ->
                        showContextMenu =
                            ContextMenu.ForBaseItem(
                                fromLongClick = true,
                                item = ep,
                                chosenStreams = chosenStreams,
                                showGoTo = false,
                                showStreamChoices = true,
                                canDelete = viewModel.canDelete(ep, preferences.appPreferences),
                                canRemoveContinueWatching = false,
                                canRemoveNextUp = false,
                                actions = contextActions,
                            )
                    },
                    playOnClick = { resume ->
                        rowFocused = EPISODE_ROW
                        episodeList?.getOrNull(position.episodeRowIndex)?.let {
                            viewModel.release()
                            viewModel.navigateTo(
                                Destination.Playback(
                                    it.id,
                                    resume.inWholeMilliseconds,
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
                            showContextMenu =
                                ContextMenu.ForBaseItem(
                                    fromLongClick = false,
                                    item = ep,
                                    chosenStreams = chosenStreams,
                                    showGoTo = false,
                                    showStreamChoices = true,
                                    canDelete = viewModel.canDelete(ep, preferences.appPreferences),
                                    canRemoveContinueWatching = false,
                                    canRemoveNextUp = false,
                                    actions = contextActions,
                                )
                        }
                    },
                    overviewOnClick = {
                        episodeList?.getOrNull(position.episodeRowIndex)?.let {
                            overviewDialog = ItemDetailsDialogInfo(it)
                        }
                    },
                    personOnClick = {
                        rowFocused =
                            if (it.type == PersonKind.GUEST_STAR) GUEST_STAR_ROW else CAST_AND_CREW_ROW
                        viewModel.navigateTo(
                            Destination.MediaItem(
                                it.id,
                                BaseItemKind.PERSON,
                            ),
                        )
                    },
                    onClickExtra = { _, extra ->
                        rowFocused = EXTRAS_ROW
                        viewModel.navigateTo(extra.destination)
                    },
                    canDelete = { viewModel.canDelete(it, preferences.appPreferences) },
                    onConfirmDelete = viewModel::deleteItem,
                    modifier = modifier,
                )
            }
        }
    }
    showContextMenu?.let { contextMenu ->
        ContextMenuDialog(
            onDismissRequest = { showContextMenu = null },
            getMediaSource = viewModel.streamChoiceService::chooseSource,
            contextMenu = contextMenu,
            preferredSubtitleLanguage = preferredSubtitleLanguage,
        )
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

private const val EPISODE_ROW = 0
private const val CAST_AND_CREW_ROW = EPISODE_ROW + 1
private const val GUEST_STAR_ROW = CAST_AND_CREW_ROW + 1
private const val EXTRAS_ROW = GUEST_STAR_ROW + 1
