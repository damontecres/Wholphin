package com.github.damontecres.wholphin.ui.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce

@UnstableApi
@Composable
fun rememberSeekBarState(
    player: Player,
    scope: CoroutineScope,
): SeekBarState {
    val seekBarState = remember(player) { SeekBarState(player, scope) }
    LaunchedEffect(player) {
        seekBarState.observe()
    }
    return seekBarState
}

@UnstableApi
class SeekBarState(
    private val player: Player,
    private val scope: CoroutineScope,
) {
    var isEnabled by mutableStateOf(player.isCommandAvailable(Player.COMMAND_SEEK_FORWARD))
        private set

    private val channel = Channel<Long>(CONFLATED)

    fun onValueChange(positionMs: Long) {
        channel.trySend(positionMs)
    }

    @OptIn(FlowPreview::class)
    suspend fun observe() {
        channel
            .consumeAsFlow()
            .debounce { 750L }
            .collect {
                player.seekTo(it)
            }
    }

//    suspend fun observe(): Nothing =
//        player.listen { events ->
//            if (events.contains(Player.EVENT_AVAILABLE_COMMANDS_CHANGED)) {
//                isEnabled = isCommandAvailable(Player.COMMAND_SEEK_FORWARD)
//            }
//        }
}
