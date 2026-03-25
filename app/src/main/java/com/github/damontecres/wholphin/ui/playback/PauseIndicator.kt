package com.github.damontecres.wholphin.ui.playback

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.observeState
import com.github.damontecres.wholphin.R
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Show an animated "pause" image whenever the player is paused
 */
@Composable
fun PauseIndicator(
    player: Player,
    modifier: Modifier = Modifier,
    duration: Duration = 300.milliseconds,
) {
    val state = rememberPauseState(player)
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state.isPaused) {
        if (state.isPaused) visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter =
            scaleIn(
                animationSpec =
                    tween(
                        durationMillis = duration.inWholeMilliseconds.toInt(),
                    ),
            ),
        exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)),
        modifier = modifier,
    ) {
        LaunchedEffect(Unit) {
            delay(duration)
            delay(50)
            visible = false
        }
        Image(
            modifier = Modifier.size(64.dp, 64.dp),
            painter = painterResource(id = R.drawable.baseline_pause_24),
            contentDescription = null,
        )
    }
}

/**
 * Remember when a player is paused
 */
@Composable
fun rememberPauseState(player: Player): PauseState {
    val state = remember(player) { PauseState(player) }
    LaunchedEffect(player) { state.observe() }
    return state
}

class PauseState(
    private val player: Player,
) {
    var isPaused by mutableStateOf(false)
        private set

    @OptIn(UnstableApi::class)
    internal suspend fun observe() {
        player
            .observeState(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
            ) {
                // Timber.v("isPaused=$isPaused, playWhenReady=${player.playWhenReady}, playbackState=${player.playbackState}")
                isPaused = !isPaused && // Not already paused, don't want to trigger more than once
                    !player.playWhenReady && // Player is actually paused
                    // Player could play if it was not paused, ie it is not stopped
                    player.playbackState.let { it == Player.STATE_READY || it == Player.STATE_BUFFERING }
            }.observe()
    }
}
