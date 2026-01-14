package com.github.damontecres.wholphin.ui.playback

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import com.github.damontecres.wholphin.ui.seekBack
import com.github.damontecres.wholphin.ui.seekForward
import kotlin.time.Duration

/**
 * Handles [KeyEvent]s during playback on [PlaybackPage]
 */
class PlaybackKeyHandler(
    private val player: Player,
    private val controlsEnabled: Boolean,
    private val skipWithLeftRight: Boolean,
    private val seekBack: Duration,
    private val seekForward: Duration,
    private val controllerViewState: ControllerViewState,
    private val updateSkipIndicator: (Long) -> Unit,
    private val skipBackOnResume: Duration?,
    private val oneClickPause: Boolean,
    private val onInteraction: () -> Unit,
    private val onStop: () -> Unit,
    private val onPlaybackDialogTypeClick: (PlaybackDialogType) -> Unit,
) {
    fun onKeyEvent(it: KeyEvent): Boolean {
        if (it.type == KeyEventType.KeyUp) onInteraction.invoke()

        var result = true
        if (!controlsEnabled) {
            result = false
        } else if (it.type != KeyEventType.KeyUp) {
            result = false
        } else if (isDirectionalDpad(it) || isEnterKey(it) || isControllerMedia(it)) {
            if (!controllerViewState.controlsVisible) {
                if (skipWithLeftRight && isSkipBack(it)) {
                    updateSkipIndicator(-seekBack.inWholeMilliseconds)
                    player.seekBack(seekBack)
                } else if (skipWithLeftRight && isSkipForward(it)) {
                    player.seekForward(seekForward)
                    updateSkipIndicator(seekForward.inWholeMilliseconds)
                } else if (oneClickPause && isEnterKey(it)) {
                    val wasPlaying = player.isPlaying
                    Util.handlePlayPauseButtonAction(player)
                    if (wasPlaying) {
                        controllerViewState.showControls()
                    } else {
                        skipBackOnResume?.let {
                            player.seekBack(it)
                        }
                    }
                } else {
                    controllerViewState.showControls()
                }
            } else {
                // When controller is visible, its buttons will handle pulsing
            }
        } else if (isMedia(it)) {
            when (it.key) {
                Key.MediaPlay, Key.MediaPause, Key.MediaPlayPause -> {
                    // no-op, MediaSession will handle
                }

                Key.MediaFastForward, Key.MediaSkipForward -> {
                    player.seekForward(seekForward)
                    updateSkipIndicator(seekForward.inWholeMilliseconds)
                }

                Key.MediaRewind, Key.MediaSkipBackward -> {
                    player.seekBack(seekBack)
                    updateSkipIndicator(-seekBack.inWholeMilliseconds)
                }

                Key.MediaNext -> {
                    if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)) player.seekToNext()
                }

                Key.MediaPrevious -> {
                    if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)) player.seekToPrevious()
                }

                Key.Captions -> {
                    onPlaybackDialogTypeClick.invoke(PlaybackDialogType.CAPTIONS)
                }

                Key.MediaAudioTrack -> {
                    onPlaybackDialogTypeClick.invoke(PlaybackDialogType.AUDIO)
                }

                Key.MediaStop -> {
                    onStop.invoke()
                }

                else -> {
                    result = false
                }
            }
        } else if (isEnterKey(it) && !controllerViewState.controlsVisible) {
            controllerViewState.showControls()
        } else if (isBackKey(it) && controllerViewState.controlsVisible) {
            // TODO change this to a BackHandler?
            controllerViewState.hideControls()
        } else {
            controllerViewState.pulseControls()
            result = false
        }
        return result
    }
}
