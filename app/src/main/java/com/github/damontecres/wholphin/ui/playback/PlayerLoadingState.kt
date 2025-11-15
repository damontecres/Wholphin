package com.github.damontecres.wholphin.ui.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi

@UnstableApi
@Composable
fun rememberPlayerLoadingState(player: Player): PlayerLoadingState {
    val state = remember(player) { PlayerLoadingState(player) }
    LaunchedEffect(player) {
        state.observe()
    }
    return state
}

@UnstableApi
class PlayerLoadingState(
    private val player: Player,
) : State<Boolean> {
    override var value by mutableStateOf(player.isLoading)
        private set

    suspend fun observe() {
        value = player.isLoading
        player.listen {
            if (it.contains(Player.EVENT_IS_LOADING_CHANGED)) {
                value = player.isLoading
            }
        }
    }
}
