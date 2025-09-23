package com.github.damontecres.dolphin.ui.playback

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.tryRequestFocus
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.stashapp.ui.components.playback.SkipIndicator
import com.github.damontecres.stashapp.ui.components.playback.rememberSeekBarState
import org.jellyfin.sdk.model.api.DeviceProfile
import kotlin.time.Duration.Companion.milliseconds

@OptIn(UnstableApi::class)
@Composable
fun PlaybackContent(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    deviceProfile: DeviceProfile,
    destination: Destination.Playback,
    modifier: Modifier = Modifier,
    viewModel: PlaybackViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(destination.itemId) {
        viewModel.init(destination, deviceProfile)
    }
    val player = viewModel.player
    val stream by viewModel.stream.observeAsState(null)

    val title by viewModel.title.observeAsState(null)
    val subtitle by viewModel.subtitle.observeAsState(null)
    val duration by viewModel.duration.observeAsState(null)

    if (stream == null) {
        // TODO loading
    } else {
        stream?.let {
            var contentScale by remember { mutableStateOf(ContentScale.Fit) }
            var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
            LaunchedEffect(playbackSpeed) { player.setPlaybackSpeed(playbackSpeed) }

            val presentationState = rememberPresentationState(player)
            val scaledModifier =
                Modifier.resizeWithContentScale(contentScale, presentationState.videoSizeDp)
            val focusRequester = remember { FocusRequester() }
            val playPauseState = rememberPlayPauseButtonState(player)
            val previousState = rememberPreviousButtonState(player)
            val nextState = rememberNextButtonState(player)
            val seekBarState = rememberSeekBarState(player, scope)

            LaunchedEffect(Unit) {
                focusRequester.tryRequestFocus()
            }
            val controllerViewState =
                remember {
                    ControllerViewState(
                        5_000,
                        true,
                    )
                }.also {
                    LaunchedEffect(it) {
                        it.observe()
                    }
                }
            var skipIndicatorDuration by remember { mutableLongStateOf(0L) }
            LaunchedEffect(controllerViewState.controlsVisible) {
                // If controller shows/hides, immediately cancel the skip indicator
                skipIndicatorDuration = 0L
            }
            var skipPosition by remember { mutableLongStateOf(0L) }
            val updateSkipIndicator = { delta: Long ->
                if (skipIndicatorDuration > 0 && delta < 0 || skipIndicatorDuration < 0 && delta > 0) {
                    skipIndicatorDuration = 0
                }
                skipIndicatorDuration += delta
                skipPosition = player.currentPosition
            }
            val keyHandler =
                PlaybackKeyHandler(
                    player = player,
                    controlsEnabled = true,
                    skipWithLeftRight = true,
                    controllerViewState = controllerViewState,
                    updateSkipIndicator = updateSkipIndicator,
                )

            Box(
                modifier
                    .background(Color.Black)
                    .onKeyEvent(keyHandler::onKeyEvent)
                    .focusRequester(focusRequester)
                    .focusable(),
            ) {
                PlayerSurface(
                    player = player,
                    surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                    modifier = scaledModifier,
                )
                if (presentationState.coverSurface) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(Color.Black),
                    )
                }

                if (!controllerViewState.controlsVisible && skipIndicatorDuration != 0L) {
                    SkipIndicator(
                        durationMs = skipIndicatorDuration,
                        onFinish = {
                            skipIndicatorDuration = 0L
                        },
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 70.dp),
                    )
                    val showSkipProgress = true // TODO get from preferences
                    if (showSkipProgress) {
                        duration?.let {
                            val percent = (skipPosition.milliseconds / it).toFloat()
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .background(MaterialTheme.colorScheme.border)
                                        .clip(RectangleShape)
                                        .height(3.dp)
                                        .fillMaxWidth(percent),
                            ) {
                                // No-op
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    controllerViewState.controlsVisible,
                    Modifier,
                    slideInVertically { it },
                    slideOutVertically { it },
                ) {
                    PlaybackOverlay(
                        modifier =
                            Modifier
                                .padding(WindowInsets.systemBars.asPaddingValues())
                                .fillMaxSize()
                                .background(Color.Transparent),
                        title = title,
                        subtitle = subtitle,
                        captions = listOf(),
                        playerControls = player,
                        controllerViewState = controllerViewState,
                        showPlay = playPauseState.showPlay,
                        previousEnabled = previousState.isEnabled,
                        nextEnabled = nextState.isEnabled,
                        seekEnabled = true,
                        onPlaybackActionClick = {},
                        onSeekBarChange = seekBarState::onValueChange,
                        showDebugInfo = false,
                        scale = contentScale,
                        playbackSpeed = playbackSpeed,
                        moreButtonOptions = MoreButtonOptions(mapOf()),
                        subtitleIndex = null,
                        audioIndex = null,
                        audioOptions = listOf(),
                    )
                }
            }
        }
    }
}
