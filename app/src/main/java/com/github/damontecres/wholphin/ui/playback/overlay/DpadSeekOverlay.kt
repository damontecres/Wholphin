package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import org.jellyfin.sdk.model.api.TrickplayInfo

/**
 * The compact overlay shown during D-Pad seeking in trickplay mode.
 */
@Composable
fun DpadSeekOverlay(
    player: Player,
    seekPositionMs: Long,
    trickplayInfo: TrickplayInfo?,
    trickplayUrlFor: (Int) -> String?,
    modifier: Modifier = Modifier,
) {
    val durationMs = player.duration.coerceAtLeast(0L)
    val seekProgressPercent =
        if (durationMs > 0) {
            (seekPositionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
    val bufferedProgress =
        if (durationMs > 0) {
            (player.bufferedPosition.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            TrickplayPreview(
                seekProgressMs = seekPositionMs,
                trickplayInfo = trickplayInfo,
                trickplayUrlFor = trickplayUrlFor,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .offsetByPercent(xPercentage = seekProgressPercent)
                        .padding(bottom = 8.dp),
            )
        }
        StaticSeekBarImpl(
            progress = seekProgressPercent,
            bufferedProgress = bufferedProgress,
            durationMs = durationMs,
            modifier = Modifier.fillMaxWidth(),
        )
        SeekTimecodes(
            positionMs = seekPositionMs,
            durationMs = durationMs,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
