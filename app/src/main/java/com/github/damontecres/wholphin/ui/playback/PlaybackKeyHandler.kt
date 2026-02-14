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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private val onSeekBarFocusRequest: () -> Unit,
    private val scope: CoroutineScope,
    private val isSeekBarFocusPending: () -> Boolean,
    private val holdToTimelineMs: Long = 2000L,
) {
    private var holdKey: Key? = null
    private var holdTriggered = false
    private var holdJob: Job? = null

    private fun cancelHoldTimer() {
        holdJob?.cancel()
        holdJob = null
    }

    private fun resetHoldState() {
        cancelHoldTimer()
        holdTriggered = false
        holdKey = null
    }

    private fun triggerHold(key: Key) {
        if (holdTriggered) return
        holdTriggered = true
        cancelHoldTimer()

        // Hold should NOT perform a skip. It should only surface controls and move focus to the seek bar.
        // Request seekbar focus immediately - other buttons will be disabled until focus is acquired
        controllerViewState.showControls()
        onSeekBarFocusRequest.invoke()
    }

    fun onKeyEvent(it: KeyEvent): Boolean {
        if (it.type == KeyEventType.KeyUp) onInteraction.invoke()

        // Always clean up hold state on key-up of the held key.
        // If the hold triggered, consume key-up so we don't also run the tap-skip behaviour.
        if (it.type == KeyEventType.KeyUp && holdKey == it.key) {
            val wasHoldTriggered = holdTriggered
            resetHoldState()
            if (wasHoldTriggered) return true
            // If hold did NOT trigger, fall through so a quick tap still performs a skip.
        }

        // While we are trying to focus the seek bar, swallow left/right so they can't
        // navigate focus away from the seekbar before it gets focused
        if (
            controllerViewState.controlsVisible &&
                isSeekBarFocusPending.invoke() &&
                (isSkipBack(it) || isSkipForward(it))
        ) {
            return true
        }

        if (it.type == KeyEventType.KeyDown) {
            if (
                controlsEnabled &&
                    skipWithLeftRight &&
                    (isSkipBack(it) || isSkipForward(it)) &&
                    (!controllerViewState.controlsVisible || isSeekBarFocusPending())
            ) {
                val nativeEvent = it.nativeKeyEvent
                val key = it.key

                // Start hold tracking once for this held key; repeat events should not reset it.
                if (holdKey != key) {
                    resetHoldState()
                    holdKey = key
                    holdJob =
                        scope.launch {
                            delay(holdToTimelineMs)
                            if (!holdTriggered && holdKey == key) {
                                triggerHold(key)
                            }
                        }
                }

                // If the system is already generating repeat events, trigger as soon as we cross the threshold.
                val heldMs = nativeEvent.eventTime - nativeEvent.downTime
                if (nativeEvent.repeatCount > 0 && heldMs >= holdToTimelineMs) {
                    triggerHold(key)
                }

                // Consume left/right key-downs while controls are hidden OR while we're waiting for seekbar focus
                // Once seekbar has focus, let the events through so it can handle scrubbing
                return true
            }
            return false
        }

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
                result = false
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
