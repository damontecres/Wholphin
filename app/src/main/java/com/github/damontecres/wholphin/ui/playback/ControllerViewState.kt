package com.github.damontecres.wholphin.ui.playback

import androidx.annotation.IntRange
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce

/**
 * The visibility state of the playback controls. Can [pulseControls] to show the controls for a specified time.
 *
 * A coroutine must call [observe]
 */
class ControllerViewState internal constructor(
    @param:IntRange(from = 0)
    var hideMilliseconds: Long,
    val controlsEnabled: Boolean,
) {
    private val channel = Channel<Long>(CONFLATED)
    private var _controlsVisible by mutableStateOf(false)
    val controlsVisible get() = _controlsVisible

    fun showControls(milliseconds: Long = hideMilliseconds) {
        if (controlsEnabled) {
            _controlsVisible = true
        }
        pulseControls(milliseconds)
    }

    fun hideControls() {
        _controlsVisible = false
    }

    fun pulseControls(milliseconds: Long = hideMilliseconds) {
        channel.trySend(milliseconds)
    }

    @OptIn(FlowPreview::class)
    suspend fun observe() {
        channel
            .consumeAsFlow()
            .debounce { it }
            .collect {
                _controlsVisible = false
            }
    }
}
