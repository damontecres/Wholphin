package com.github.damontecres.wholphin.ui.detail.music

import android.view.Gravity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.rememberQueue
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.playback.BottomDialog
import com.github.damontecres.wholphin.ui.playback.BottomDialogItem
import com.github.damontecres.wholphin.ui.playback.PlaybackKeyHandler
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState
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
    val state by viewModel.state.collectAsState()
    val player = viewModel.player
    val queue = rememberQueue(player, state.musicServiceState.queueSize)
    val current = queue.getOrNull(state.musicServiceState.currentIndex)

    val controllerViewState = viewModel.controllerViewState
    val preferences =
        viewModel.userPreferencesService.flow
            .collectAsState(
                UserPreferences(
                    AppPreferences.getDefaultInstance(),
                ),
            ).value.appPreferences

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

    var showMoreDialog by remember { mutableStateOf(false) }
    var lyricsActive by remember { mutableStateOf(true) }
    var showDebugInfo by remember { mutableStateOf(true) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    Box(modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent(keyHandler::onKeyEvent)
                    .focusRequester(focusRequester)
                    .focusable(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                AnimatedVisibility(
                    visible = lyricsActive && state.hasLyrics,
                    enter = expandHorizontally(expandFrom = Alignment.Start),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start),
                    modifier = Modifier,
                ) {
                    LyricsContent(
                        synced = true,
                        lyrics = state.lyrics,
                        currentLyricPosition = state.currentLyricIndex,
                        onClick = {},
                        modifier =
                            Modifier
                                .padding(vertical = 120.dp, horizontal = 32.dp)
                                .fillMaxHeight()
                                .width(320.dp),
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier =
                        Modifier
                            .padding(40.dp)
                            .fillMaxSize()
                            .weight(1f),
                ) {
                    AsyncImage(
                        contentDescription = null,
                        model = current?.imageUrl,
                        modifier = Modifier.fillMaxSize(.7f),
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
                onClickMore = { showMoreDialog = true },
                modifier =
                    Modifier
                        .background(AppColors.TransparentBlack50)
                        .align(Alignment.BottomCenter),
            )
        }
        if (state.musicServiceState.loadingState is LoadingState.Loading) {
            LoadingPage(focusEnabled = false)
        }
    }
    if (showMoreDialog) {
        NowPlayingBottomDialog(
            showDebugInfo = showDebugInfo,
            lyricsActive = lyricsActive,
            songHasLyrics = state.hasLyrics,
            onDismissRequest = { showMoreDialog = false },
            onClickShowDebug = { showDebugInfo = !showDebugInfo },
            onClickLyrics = { lyricsActive = !lyricsActive },
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
