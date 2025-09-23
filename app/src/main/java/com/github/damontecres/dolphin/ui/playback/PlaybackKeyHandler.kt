package com.github.damontecres.dolphin.ui.playback

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.media3.common.Player
import androidx.media3.common.util.Util

class PlaybackKeyHandler(
    private val player: Player,
    private val controlsEnabled: Boolean,
    private val skipWithLeftRight: Boolean,
    private val controllerViewState: ControllerViewState,
    private val updateSkipIndicator: (Long) -> Unit,
) {
    fun onKeyEvent(it: KeyEvent): Boolean {
        var result = true
        if (!controlsEnabled) {
            result = false
        } else if (it.type != KeyEventType.KeyUp) {
            result = false
        } else if (isDpad(it)) {
            if (!controllerViewState.controlsVisible) {
                if (skipWithLeftRight && it.key == Key.DirectionLeft) {
                    updateSkipIndicator(-player.seekBackIncrement)
                    player.seekBack()
                } else if (skipWithLeftRight && it.key == Key.DirectionRight) {
                    player.seekForward()
                    updateSkipIndicator(player.seekForwardIncrement)
                } else {
                    controllerViewState.showControls()
                }
            } else {
                // When controller is visible, its buttons will handle pulsing
            }
        } else if (isMedia(it)) {
            when (it.key) {
                Key.MediaPlay -> {
                    Util.handlePlayButtonAction(player)
                }

                Key.MediaPause -> {
                    Util.handlePauseButtonAction(player)
                    controllerViewState.showControls()
                }

                Key.MediaPlayPause -> {
                    Util.handlePlayPauseButtonAction(player)
                    if (!player.isPlaying) {
                        controllerViewState.showControls()
                    }
                }

                Key.MediaFastForward, Key.MediaSkipForward -> {
                    player.seekForward()
                    updateSkipIndicator(player.seekForwardIncrement)
                }

                Key.MediaRewind, Key.MediaSkipBackward -> {
                    player.seekBack()
                    updateSkipIndicator(-player.seekBackIncrement)
                }

                Key.MediaNext -> if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)) player.seekToNext()
                Key.MediaPrevious -> if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)) player.seekToPrevious()
                else -> result = false
            }
        } else if (it.key == Key.Enter && !controllerViewState.controlsVisible) {
            controllerViewState.showControls()
        } else if (it.key == Key.Back && controllerViewState.controlsVisible) {
            // TODO change this to a BackHandler?
            controllerViewState.hideControls()
        } else {
            controllerViewState.pulseControls()
            result = false
        }
        return result
    }
}
