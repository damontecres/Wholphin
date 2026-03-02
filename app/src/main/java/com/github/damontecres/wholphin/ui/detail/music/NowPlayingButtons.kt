package com.github.damontecres.wholphin.ui.detail.music

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import androidx.media3.ui.compose.state.rememberRepeatButtonState
import androidx.media3.ui.compose.state.rememberShuffleButtonState
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.PlaybackAction
import com.github.damontecres.wholphin.ui.playback.PlaybackButtons
import com.github.damontecres.wholphin.ui.playback.PlaybackFaButton
import com.github.damontecres.wholphin.ui.playback.buttonSpacing
import kotlin.time.Duration.Companion.seconds

@OptIn(UnstableApi::class)
@Composable
fun NowPlayingButtons(
    player: Player,
    controllerViewState: ControllerViewState,
    initialFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val playPauseState = rememberPlayPauseButtonState(player)
    val previousState = rememberPreviousButtonState(player)
    val nextState = rememberNextButtonState(player)
    val shuffleState = rememberShuffleButtonState(player)
    val repeatState = rememberRepeatButtonState(player)
    Box(
        modifier = modifier,
    ) {
        PlaybackButtons(
            player = player,
            initialFocusRequester = initialFocusRequester,
            onControllerInteraction = { controllerViewState.pulseControls() },
            onPlaybackActionClick = {
                when (it) {
                    PlaybackAction.Next -> {
                        nextState.onClick()
                    }

                    PlaybackAction.Previous -> {
                        previousState.onClick()
                    }

                    is PlaybackAction.ToggleCaptions -> {
                        TODO()
                    }

                    else -> {}
                }
            },
            showPlay = playPauseState.showPlay,
            previousEnabled = previousState.isEnabled,
            nextEnabled = nextState.isEnabled,
            seekBack = 10.seconds,
            skipBackOnResume = null,
            seekForward = 30.seconds, // TODO
            modifier = Modifier.align(Alignment.Center),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            PlaybackFaButton(
                iconRes = R.string.fa_shuffle,
                onClick = {
                    shuffleState.onClick()
                },
                onControllerInteraction = { controllerViewState.pulseControls() },
                textColor =
                    if (shuffleState.shuffleOn) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        Color.Unspecified
                    },
            )
            PlaybackFaButton(
                iconRes = R.string.fa_repeat,
                onClick = {
                    repeatState.onClick()
                },
                onControllerInteraction = { controllerViewState.pulseControls() },
                textColor =
                    when (repeatState.repeatModeState) {
                        Player.REPEAT_MODE_ALL -> MaterialTheme.colorScheme.secondary

                        // TODO
                        Player.REPEAT_MODE_ONE -> MaterialTheme.colorScheme.tertiary

                        else -> Color.Unspecified
                    },
            )
        }
    }
}
