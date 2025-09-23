package com.github.damontecres.dolphin.ui.playback

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key

fun isDirectionalDpad(event: KeyEvent): Boolean =
    event.key == Key.DirectionUp ||
        event.key == Key.DirectionDown ||
        event.key == Key.DirectionLeft ||
        event.key == Key.DirectionRight ||
        event.key == Key.DirectionUpRight ||
        event.key == Key.DirectionUpLeft ||
        event.key == Key.DirectionDownRight ||
        event.key == Key.DirectionDownLeft

fun isDpad(event: KeyEvent): Boolean = event.key == Key.DirectionCenter || isDirectionalDpad(event)

fun isEnterKey(event: KeyEvent) = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter

fun isMedia(event: KeyEvent): Boolean =
    event.key == Key.MediaPlay ||
        event.key == Key.MediaPause ||
        event.key == Key.MediaPlayPause ||
        event.key == Key.MediaFastForward ||
        event.key == Key.MediaSkipForward ||
        event.key == Key.MediaRewind ||
        event.key == Key.MediaSkipBackward ||
        event.key == Key.MediaNext ||
        event.key == Key.MediaPrevious

fun isBackwardButton(event: KeyEvent): Boolean =
    event.key == Key.PageUp ||
        event.key == Key.ChannelUp ||
        event.key == Key.MediaPrevious ||
        event.key == Key.MediaRewind ||
        event.key == Key.MediaSkipBackward

fun isForwardButton(event: KeyEvent): Boolean =
    event.key == Key.PageDown ||
        event.key == Key.ChannelDown ||
        event.key == Key.MediaNext ||
        event.key == Key.MediaFastForward ||
        event.key == Key.MediaSkipForward
