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
 * @see PlaybackState
 */
@Composable
fun rememberPlaybackState(player: Player): State<PlaybackState> {
    val state = remember(player) { mutableStateOf(getPlaybackState(player.playbackState)) }
    LaunchedEffect(player) {
        player.listenTo(Player.EVENT_PLAYBACK_STATE_CHANGED) {
            state.value = getPlaybackState(player.playbackState)
        }
    }
    return state
}

private fun getPlaybackState(
    @Player.State value: Int,
): PlaybackState = PlaybackState.entries.first { it.value == value }

/**
 * Represents [Player.State] integers as an Enum
 */
enum class PlaybackState(
    @param:Player.State val value: Int,
) {
    IDLE(Player.STATE_IDLE),
    BUFFERING(Player.STATE_BUFFERING),
    READY(Player.STATE_READY),
    ENDED(Player.STATE_ENDED),
}
