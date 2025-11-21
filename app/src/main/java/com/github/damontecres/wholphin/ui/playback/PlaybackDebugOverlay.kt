package com.github.damontecres.wholphin.ui.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.ui.byteRateSuffixes
import com.github.damontecres.wholphin.ui.formatBytes
import com.github.damontecres.wholphin.ui.letNotEmpty
import org.jellyfin.sdk.model.api.TranscodingInfo

@Composable
fun PlaybackDebugOverlay(
    currentPlayback: CurrentPlayback?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        ) {
            ProvideTextStyle(MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)) {
                SimpleTable(
                    buildList {
                        add("Backend:" to currentPlayback?.backend?.toString())
                        add("Play method:" to currentPlayback?.playMethod?.serialName)
                        if (currentPlayback?.backend == PlayerBackend.EXO_PLAYER) {
                            add("Video Decoder:" to currentPlayback.videoDecoder)
                            add("Audio Decoder:" to currentPlayback.audioDecoder)
                        }
                    },
                )
                currentPlayback?.transcodeInfo?.let {
                    TranscodeInfo(it, Modifier)
                }
            }
        }
        currentPlayback?.tracks?.letNotEmpty {
            PlaybackTrackInfo(
                trackSupport = it,
            )
        }
    }
}

@Composable
fun TranscodeInfo(
    info: TranscodingInfo,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        SimpleTable(
            listOf(
                "Reason:" to info.transcodeReasons.joinToString(", "),
                "HW Accel:" to info.hardwareAccelerationType?.toString(),
                "Container:" to info.container,
                "Bitrate:" to info.bitrate?.let { formatBytes(it, byteRateSuffixes) },
            ),
        )
        SimpleTable(
            listOf(
                "Video:" to "${info.videoCodec}, ${info.width}x${info.height}",
                "Video Direct:" to info.isVideoDirect.toString(),
                "Audio:" to "${info.audioCodec}, ch=${info.audioChannels}",
                "Audio Direct:" to info.isAudioDirect.toString(),
            ),
        )
    }
}

@Composable
fun SimpleTable(
    rows: List<Pair<String, String?>>,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier,
        ) {
            rows.forEach {
                Text(
                    text = it.first,
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier,
        ) {
            rows.forEach {
                Text(
                    text = it.second.toString(),
                )
            }
        }
    }
}
