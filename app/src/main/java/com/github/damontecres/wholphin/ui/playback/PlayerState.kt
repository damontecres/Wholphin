package com.github.damontecres.wholphin.ui.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.Player
import androidx.media3.common.listenTo

/**
 * Remembers the [Player]'s state as it changes. Useful for changing UI if the player is buffering.
 *
 * @see Player.State
 * @see PlayerState
 */
@Composable
fun rememberPlayerState(player: Player): State<PlayerState> {
    val state = remember(player) { mutableStateOf(getPlayerState(player.playbackState)) }
    LaunchedEffect(player) {
        player.listenTo(Player.EVENT_PLAYBACK_STATE_CHANGED) {
            state.value = getPlayerState(player.playbackState)
        }
    }
    return state
}

private fun getPlayerState(
    @Player.State value: Int,
): PlayerState = PlayerState.entries.first { it.value == value }

/**
 * Represents [Player.State] integers as an Enum
 */
enum class PlayerState(
    @param:Player.State val value: Int,
) {
    IDLE(Player.STATE_IDLE),
    BUFFERING(Player.STATE_BUFFERING),
    READY(Player.STATE_READY),
    ENDED(Player.STATE_ENDED),
}
