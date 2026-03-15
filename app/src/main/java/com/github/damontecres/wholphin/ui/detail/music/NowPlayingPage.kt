package com.github.damontecres.wholphin.ui.detail.music

import android.Manifest
import android.view.Gravity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.AudioItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.rememberQueue
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.findActivity
import com.github.damontecres.wholphin.ui.playback.BottomDialog
import com.github.damontecres.wholphin.ui.playback.BottomDialogItem
import com.github.damontecres.wholphin.ui.playback.PlaybackKeyHandler
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration.Companion.seconds

@OptIn(UnstableApi::class)
@Composable
fun NowPlayingPage(
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel =
        hiltViewModel<NowPlayingViewModel, NowPlayingViewModel.Factory>(
            creationCallback = { it.create() },
        ),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val player = viewModel.player
    val queue =
        rememberQueue(
            player,
            state.musicServiceState.queueVersion,
            state.musicServiceState.queueSize,
        )
    val current = queue.getOrNull(state.musicServiceState.currentIndex)
    val viz by viewModel.viz.collectAsState()

    val controllerViewState = viewModel.controllerViewState
    val preferences =
        viewModel.userPreferencesService.flow
            .collectAsState(
                UserPreferences(
                    AppPreferences.getDefaultInstance(),
                ),
            ).value.appPreferences
    val musicPrefs = preferences.musicPreferences

    val keyHandler =
        remember(preferences) {
            PlaybackKeyHandler(
                player = player,
                controlsEnabled = true,
                skipWithLeftRight = false,
                seekForward = 30.seconds,
                seekBack = 10.seconds,
//                seekForward = preferences.playbackPreferences.skipForwardMs.milliseconds,
//                seekBack = preferences.playbackPreferences.skipBackMs.milliseconds,
                controllerViewState = controllerViewState,
                updateSkipIndicator = {},
                skipBackOnResume = null,
//                skipBackOnResume = preferences.playbackPreferences.skipBackOnResume,
                onInteraction = viewModel::reportInteraction,
                oneClickPause = preferences.playbackPreferences.oneClickPause,
                onStop = {
                    viewModel.stop()
                },
                onPlaybackDialogTypeClick = { },
                getDurationMs = { player.duration },
            )
        }

    val actions =
        remember {
            MusicQueueDialogActions(
                onNavigate = { viewModel.navigationManager.navigateTo(it) },
                onClickPlay = { index, _ -> viewModel.play(index) },
                onClickPlayNext = { index, _ -> viewModel.playNext(index) },
                onClickRemoveFromQueue = { index, _ -> viewModel.removeFromQueue(index) },
            )
        }

    var showViewOptionsDialog by remember { mutableStateOf(false) }
    var itemMoreDialog by remember { mutableStateOf<DialogParams?>(null) }
    var lyricsHaveFocus by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }

    LaunchedEffect(lyricsHaveFocus) {
        if (lyricsHaveFocus) {
            controllerViewState.hideControls()
        }
    }
    BackHandler(lyricsHaveFocus) {
        focusRequester.tryRequestFocus()
    }

    var showRationaleDialog by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            viewModel.startVisualizer(isGranted, true)
        }

    Box(modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent(keyHandler::onKeyEvent)
                    .focusRequester(focusRequester)
                    .focusable(),
        )
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier.wrapContentWidth(),
            ) {
                val enter =
                    remember {
                        expandHorizontally(expandFrom = Alignment.Start) + fadeIn()
                    }
                val exit =
                    remember {
                        shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
                    }
                androidx.compose.animation.AnimatedVisibility(
                    visible = musicPrefs.showAlbumArt,
                    enter = enter,
                    exit = exit,
                    modifier =
                        Modifier
                            .padding(32.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(.5f),
                    ) {
                        AsyncImage(
                            contentDescription = null,
                            model = current?.imageUrl,
                            modifier =
                                Modifier
                                    .size(320.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                        )
                        current?.title?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        current?.albumTitle?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        current?.artistNames?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = musicPrefs.showVisualizer,
                    enter = enter,
                    exit = exit,
                    modifier =
                        Modifier
                            .padding(horizontal = 32.dp)
                            .align(Alignment.CenterStart),
                ) {
                    val visualizerWidth by animateFloatAsState(if (musicPrefs.showLyrics && state.hasLyrics) .5f else 1f)
                    BarVisualizer(
                        data = viz,
                        modifier =
                            Modifier
                                .fillMaxHeight(.75f)
                                .fillMaxWidth(visualizerWidth)
                                .align(Alignment.CenterStart),
                    )
                }
            }

            AnimatedVisibility(
                visible = musicPrefs.showLyrics && state.hasLyrics,
                enter = expandHorizontally(expandFrom = Alignment.End),
                exit = shrinkHorizontally(shrinkTowards = Alignment.End),
                modifier = Modifier,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .padding(horizontal = 32.dp, vertical = 100.dp)
                            .fillMaxHeight(),
                ) {
                    LyricsContent(
                        lyrics = state.lyrics,
                        currentLyricPosition = state.currentLyricIndex,
                        lyricsHaveFocus = lyricsHaveFocus,
                        onFocusLyrics = { lyricsHaveFocus = it },
                        onClick = {
                            it.start
                                ?.ticks
                                ?.inWholeMilliseconds
                                ?.let { player.seekTo(it) }
                        },
                        modifier =
                            Modifier
                                .fillMaxSize(),
//                                .width(360.dp),
                    )
                }
            }
        }
        val showContextForItem =
            remember {
                { fromLongClick: Boolean, index: Int, song: AudioItem ->
                    itemMoreDialog =
                        DialogParams(
                            title = song.title ?: "",
                            fromLongClick = fromLongClick,
                            items =
                                buildMoreDialogForMusicQueue(
                                    context = context,
                                    actions = actions,
                                    item = song,
                                    index = index,
                                    canRemove = true,
                                ),
                        )
                }
            }

        BackHandler(controllerViewState.controlsVisible) {
            controllerViewState.hideControls()
        }
        AnimatedVisibility(
            visible = controllerViewState.controlsVisible,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier =
                Modifier
                    .align(Alignment.BottomCenter),
        ) {
            NowPlayingOverlay(
                state = state,
                player = player,
                current = current,
                queue = queue,
                controllerViewState = controllerViewState,
                onMoveQueue = { index, direction -> viewModel.moveQueue(index, direction) },
                onClickMore = { showViewOptionsDialog = true },
                onClickSong = { index, _ -> viewModel.play(index) },
                onClickMoreItem = { index, song -> showContextForItem.invoke(false, index, song) },
                onLongClickSong = { index, song -> showContextForItem.invoke(true, index, song) },
                onClickStop = { viewModel.stop() },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .drawBehind {
                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black),
                                        startY = 0f,
                                        endY = size.height,
                                    ),
                            )
                        },
            )
        }
        if (state.musicServiceState.loadingState is LoadingState.Loading) {
            LoadingPage(focusEnabled = false)
        }
    }
    itemMoreDialog?.let { params ->
        DialogPopup(
            showDialog = true,
            title = params.title,
            dialogItems = params.items,
            onDismissRequest = { itemMoreDialog = null },
            dismissOnClick = true,
            waitToLoad = params.fromLongClick,
        )
    }
    if (showViewOptionsDialog) {
        MusicViewOptionsDialog(
            appPreferences = preferences,
            onDismissRequest = { showViewOptionsDialog = false },
            onViewOptionsChange = { viewModel.updatePreferences(it) },
            onEnableVisualizer = {
                val showRationale =
                    context
                        .findActivity()
                        ?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true
                when {
                    !state.visualizerPermissions && showRationale -> {
                        showRationaleDialog = true
                    }

                    !state.visualizerPermissions -> {
                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                    }

                    else -> {
                        viewModel.startVisualizer(true, true)
                    }
                }
            },
        )
    }
    if (showRationaleDialog) {
        RecordAudioRationaleDialog(
            onDismissRequest = { showRationaleDialog = false },
            onClick = {
                showRationaleDialog = false
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
    }
}

@Composable
fun NowPlayingBottomDialog(
    showDebugInfo: Boolean,
    lyricsActive: Boolean,
    songHasLyrics: Boolean,
    onDismissRequest: () -> Unit,
    onClickShowDebug: () -> Unit,
    onClickLyrics: () -> Unit,
) {
    val choices =
        mapOf(
            BottomDialogItem(
                data = 0,
                headline = stringResource(if (showDebugInfo) R.string.hide_debug_info else R.string.show_debug_info),
                supporting = null,
            ) to onClickShowDebug,
            BottomDialogItem(
                data = 0,
                headline = stringResource(if (lyricsActive) R.string.hide_lyrics else R.string.show_lyrics),
                supporting = if (songHasLyrics) stringResource(R.string.song_has_lyrics) else null,
            ) to onClickLyrics,
        )

    BottomDialog(
        choices = choices.keys.toList(),
        onDismissRequest = {
            onDismissRequest.invoke()
        },
        onSelectChoice = { _, choice ->
            choices[choice]?.invoke()
            onDismissRequest.invoke()
        },
        gravity = Gravity.START,
    )
}

@Composable
private fun RecordAudioRationaleDialog(
    onDismissRequest: () -> Unit,
    onClick: () -> Unit,
) {
    BasicDialog(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.visualizer_rationale),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(
                onClick = onClick,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = stringResource(R.string.continue_string),
                )
            }
        }
    }
}
