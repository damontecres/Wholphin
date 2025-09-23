package com.github.damontecres.stashapp.ui.components.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private var job: Job? = null

    fun onValueChange(progress: Float) {
        job?.cancel()
        job =
            scope.launch(Dispatchers.IO) {
                delay(750L)
                withContext(Dispatchers.Main) {
                    player.seekTo((player.duration * progress).toLong())
                }
            }
    }

    suspend fun observe(): Nothing =
        player.listen { events ->
            if (events.contains(Player.EVENT_AVAILABLE_COMMANDS_CHANGED)) {
                isEnabled = isCommandAvailable(Player.COMMAND_SEEK_FORWARD)
            }
        }
}
