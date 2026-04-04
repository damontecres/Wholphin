package com.github.damontecres.wholphin.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.ui.formatBitrate
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import kotlin.time.Duration.Companion.seconds

private val HudGreen = Color(0xFF4CAF50)
private val HudOrange = Color(0xFFFF9800)
private val HudCyan = Color(0xFF00BCD4)
private val HudWhite = Color.White
private val HudDimWhite = Color.White.copy(alpha = 0.6f)
private val HudBackground = Color.Black.copy(alpha = 0.55f)

private val HudFontSize = 11.sp
private val HudFont = FontFamily.Monospace

@Composable
fun PlaybackStatsHud(
    currentPlayback: CurrentPlayback?,
    player: Player?,
    modifier: Modifier = Modifier,
) {
    if (currentPlayback == null) return

    val bufferAheadSec by produceState(0L, player) {
        while (isActive) {
            val pos = player?.currentPosition ?: 0L
            val buf = player?.bufferedPosition ?: 0L
            value = ((buf - pos).coerceAtLeast(0L)) / 1000
            delay(1.seconds)
        }
    }

    val transcodeInfo = currentPlayback.transcodeInfo
    val mediaStreams = currentPlayback.mediaSourceInfo.mediaStreams
    val videoStream = mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
    val audioStream = mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }

    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .background(HudBackground)
                .padding(10.dp),
    ) {
        // Engine
        val engineLabel =
            when (currentPlayback.backend) {
                PlayerBackend.EXO_PLAYER -> "ExoPlayer"
                PlayerBackend.MPV -> "MPV"
                else -> currentPlayback.backend.toString()
            }
        HudText(engineLabel, HudCyan)

        // Video info
        val videoLine =
            buildString {
                if (transcodeInfo != null) {
                    transcodeInfo.videoCodec?.uppercase()?.let(::append)
                    appendResolution(transcodeInfo.width, transcodeInfo.height)
                } else {
                    videoStream?.let { stream ->
                        stream.codec?.uppercase()?.let(::append)
                        appendResolution(stream.width, stream.height)
                        stream.bitDepth?.let { append(" ${it}-bit") }
                        stream.videoRange?.let { append(" $it") }
                    }
                }
            }
        if (videoLine.isNotBlank()) {
            HudText(videoLine, HudWhite)
        }

        // Audio info
        val audioLine =
            buildString {
                if (transcodeInfo != null) {
                    transcodeInfo.audioCodec?.uppercase()?.let(::append)
                    transcodeInfo.audioChannels?.let { append(" ${it}ch") }
                } else {
                    audioStream?.let { stream ->
                        stream.codec?.uppercase()?.let(::append)
                        stream.channelLayout?.let { append(" $it") }
                        stream.bitRate?.let { append(" @ ${formatBitrate(it)}") }
                    }
                }
            }
        if (audioLine.isNotBlank()) {
            HudText(audioLine, HudDimWhite)
        }

        // Play method
        val (playMethodLabel, playMethodColor) =
            when (currentPlayback.playMethod) {
                PlayMethod.DIRECT_PLAY -> "DIRECT PLAY" to HudGreen
                PlayMethod.DIRECT_STREAM -> "DIRECT STREAM" to HudGreen
                PlayMethod.TRANSCODE -> "TRANSCODE" to HudOrange
                else -> currentPlayback.playMethod.serialName.uppercase() to HudWhite
            }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudText(playMethodLabel, playMethodColor, FontWeight.Bold)
            currentPlayback.mediaSourceInfo.container?.uppercase()?.let {
                HudText(it, HudDimWhite)
            }
        }

        // Transcode details
        if (currentPlayback.playMethod == PlayMethod.TRANSCODE && transcodeInfo != null) {
            val reasons = transcodeInfo.transcodeReasons
            if (reasons.isNotEmpty()) {
                HudText("Reason: ${reasons.joinToString(", ")}", HudOrange.copy(alpha = 0.9f))
            }
            val directParts =
                buildList {
                    transcodeInfo.isVideoDirect?.let { add("Video: ${if (it) "direct" else "transcode"}") }
                    transcodeInfo.isAudioDirect?.let { add("Audio: ${if (it) "direct" else "transcode"}") }
                }
            if (directParts.isNotEmpty()) {
                HudText(directParts.joinToString(" · "), HudDimWhite)
            }
        }

        // Bitrate
        transcodeInfo?.bitrate?.let {
            HudText("Bitrate: ${formatBitrate(it)}", HudDimWhite)
        }

        // Decoders (ExoPlayer only)
        if (currentPlayback.backend == PlayerBackend.EXO_PLAYER) {
            val decoderLine =
                buildList {
                    currentPlayback.videoDecoder?.let { add("V: $it") }
                    currentPlayback.audioDecoder?.let { add("A: $it") }
                }
            if (decoderLine.isNotEmpty()) {
                HudText(decoderLine.joinToString(" · "), HudDimWhite)
            }
        }

        // Buffer
        if (bufferAheadSec > 0) {
            HudText("Buffer: ${bufferAheadSec}s ahead", HudDimWhite)
        }
    }
}

private fun StringBuilder.appendResolution(
    width: Int?,
    height: Int?,
) {
    if (width != null && height != null) {
        append(" ${width}x${height}")
    }
}

@Composable
private fun HudText(
    text: String,
    color: Color,
    fontWeight: FontWeight = FontWeight.Medium,
) {
    Text(
        text = text,
        color = color,
        fontSize = HudFontSize,
        fontFamily = HudFont,
        fontWeight = fontWeight,
        lineHeight = HudFontSize * 1.3f,
    )
}
