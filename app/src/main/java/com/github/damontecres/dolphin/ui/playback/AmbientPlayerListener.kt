package com.github.damontecres.dolphin.ui.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.common.Player.Listener
import com.github.damontecres.dolphin.ui.findActivity
import com.github.damontecres.dolphin.ui.keepScreenOn

/**
 * Starts a [Player.Listener] that ensures the screen stays on without a screen saber during playback
 *
 * This will clean up the listener when disposed
 */
@Composable
fun AmbientPlayerListener(player: Player) {
    val context = LocalContext.current
    DisposableEffect(player) {
        val listener =
            object : Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    context.findActivity()?.keepScreenOn(isPlaying)
                }
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            context.findActivity()?.keepScreenOn(false)
        }
    }
}
