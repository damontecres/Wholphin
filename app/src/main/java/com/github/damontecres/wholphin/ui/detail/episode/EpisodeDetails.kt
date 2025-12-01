package com.github.damontecres.wholphin.ui.detail.episode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.chooseSource
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.DetailsBackdropImage
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButtons
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.Optional
import com.github.damontecres.wholphin.ui.components.chooseStream
import com.github.damontecres.wholphin.ui.components.chooseVersionParams
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.detail.MoreDialogActions
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItems
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUID
import java.util.UUID
import kotlin.time.Duration

@Composable
fun EpisodeDetails(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: EpisodeViewModel =
        hiltViewModel<EpisodeViewModel, EpisodeViewModel.Factory>(
            creationCallback = { it.create(destination.itemId) },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.init()
        onPauseOrDispose { }
    }
    val item by viewModel.item.observeAsState()
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    val chosenStreams by viewModel.chosenStreams.observeAsState(null)

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var moreDialog by remember { mutableStateOf<DialogParams?>(null) }
    var chooseVersion by remember { mutableStateOf<DialogParams?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)

    val moreActions =
        MoreDialogActions(
            navigateTo = viewModel::navigateTo,
            onClickWatch = { itemId, watched ->
                viewModel.setWatched(itemId, watched)
            },
            onClickFavorite = { itemId, favorite ->
                viewModel.setFavorite(itemId, favorite)
            },
            onClickAddPlaylist = { itemId ->
                playlistViewModel.loadPlaylists(MediaType.VIDEO)
                showPlaylistDialog.makePresent(itemId)
            },
        )

    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state)
        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage()

        LoadingState.Success -> {
            item?.let { ep ->
                LifecycleStartEffect(destination.itemId) {
                    viewModel.maybePlayThemeSong(
                        destination.itemId,
                        preferences.appPreferences.interfacePreferences.playThemeSongs,
                    )
                    onStopOrDispose {
                        viewModel.release()
                    }
                }
                EpisodeDetailsContent(
                    preferences = preferences,
                    ep = ep,
                    chosenStreams = chosenStreams,
                    playOnClick = {
                        viewModel.navigateTo(
                            Destination.Playback(
                                ep.id,
                                it.inWholeMilliseconds,
                                ep,
                            ),
                        )
                    },
                    overviewOnClick = {
                        overviewDialog =
                            ItemDetailsDialogInfo(
                                title = ep.name ?: context.getString(R.string.unknown),
                                overview = ep.data.overview,
                                genres = ep.data.genres.orEmpty(),
                                files = ep.data.mediaSources.orEmpty(),
                            )
                    },
                    moreOnClick = {
                        moreDialog =
                            DialogParams(
                                fromLongClick = false,
                                title = ep.name + " (${ep.data.productionYear ?: ""})",
                                items =
                                    buildMoreDialogItems(
                                        context = context,
                                        item = ep,
                                        watched = ep.data.userData?.played ?: false,
                                        favorite = ep.data.userData?.isFavorite ?: false,
                                        seriesId = ep.data.seriesId,
                                        sourceId = chosenStreams?.sourceId,
                                        actions = moreActions,
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
                    },
                    watchOnClick = {
                        viewModel.setWatched(ep.id, !ep.played)
                    },
                    favoriteOnClick = {
                        viewModel.setFavorite(ep.id, !ep.favorite)
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
    showPlaylistDialog.compose { itemId ->
        PlaylistDialog(
            title = stringResource(R.string.add_to_playlist),
            state = playlistState,
            onDismissRequest = { showPlaylistDialog.makeAbsent() },
            onClick = {
                playlistViewModel.addToPlaylist(it.id, itemId)
                showPlaylistDialog.makeAbsent()
            },
            createEnabled = true,
            onCreatePlaylist = {
                playlistViewModel.createPlaylistAndAddItem(it, itemId)
                showPlaylistDialog.makeAbsent()
            },
            elevation = 3.dp,
        )
    }
}

private const val HEADER_ROW = 0

@Composable
fun EpisodeDetailsContent(
    preferences: UserPreferences,
    ep: BaseItem,
    chosenStreams: ChosenStreams?,
    playOnClick: (Duration) -> Unit,
    overviewOnClick: () -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var position by rememberInt(0)
    val focusRequesters = remember { List(1) { FocusRequester() } }
    val dto = ep.data
    val backdropImageUrl = ep.backdropImageUrl
    val resumePosition = dto.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        focusRequesters.getOrNull(position)?.tryRequestFocus()
    }
    Box(modifier = modifier) {
        DetailsBackdropImage(backdropImageUrl)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(bringIntoViewRequester),
                ) {
                    EpisodeDetailsHeader(
                        preferences = preferences,
                        ep = ep,
                        chosenStreams = chosenStreams,
                        bringIntoViewRequester = bringIntoViewRequester,
                        overviewOnClick = overviewOnClick,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp, bottom = 16.dp),
                    )
                    ExpandablePlayButtons(
                        resumePosition = resumePosition,
                        watched = dto.userData?.played ?: false,
                        favorite = dto.userData?.isFavorite ?: false,
                        playOnClick = {
                            position = HEADER_ROW
                            playOnClick.invoke(it)
                        },
                        moreOnClick = moreOnClick,
                        watchOnClick = watchOnClick,
                        favoriteOnClick = favoriteOnClick,
                        buttonOnFocusChanged = {
                            if (it.isFocused) {
                                position = HEADER_ROW
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                        modifier =
                            Modifier
                                .padding(bottom = 16.dp)
                                .focusRequester(focusRequesters[HEADER_ROW]),
                    )
                }
            }
        }
    }
}
