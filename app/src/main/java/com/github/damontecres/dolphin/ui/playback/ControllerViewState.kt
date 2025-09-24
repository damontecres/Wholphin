package com.github.damontecres.dolphin.ui.playback

import androidx.annotation.IntRange
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce

class ControllerViewState internal constructor(
    @param:IntRange(from = 0)
    private val hideMilliseconds: Int,
    val controlsEnabled: Boolean,
) {
    private val channel = Channel<Int>(CONFLATED)
    private var _controlsVisible by mutableStateOf(false)
    val controlsVisible get() = _controlsVisible

    fun showControls(milliseconds: Int = hideMilliseconds) {
        if (controlsEnabled) {
            _controlsVisible = true
        }
        pulseControls(milliseconds)
    }

    fun hideControls() {
        _controlsVisible = false
    }

    fun pulseControls(milliseconds: Int = hideMilliseconds) {
        channel.trySend(milliseconds)
    }

    @OptIn(FlowPreview::class)
    suspend fun observe() {
        channel
            .consumeAsFlow()
            .debounce { it.toLong() }
            .collect {
                _controlsVisible = false
            }
    }
}
