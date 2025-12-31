package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.ui.byteRateSuffixes
import com.github.damontecres.wholphin.ui.formatBytes
import com.github.damontecres.wholphin.ui.letNotEmpty
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jellyfin.sdk.model.api.TranscodingInfo
import timber.log.Timber
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Composable
fun PlaybackDebugOverlay(
    currentPlayback: CurrentPlayback?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val display =
        remember(context) {
            try {
                val displayManager =
                    context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
                displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            } catch (ex: Exception) {
                Timber.e(ex)
                null
            }
        }
    val displayMode by produceState<String?>(null) {
        while (isActive) {
            value =
                display?.mode?.let {
                    val rate = String.format(Locale.getDefault(), "%.3f", it.refreshRate)
                    "${it.physicalWidth}x${it.physicalHeight}@${rate}fps, id=${it.modeId}"
                }
            delay(10.seconds)
        }
    }
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
                        add("Display Mode: " to displayMode?.toString())
                    },
                    modifier = Modifier.weight(1f, fill = false),
                )
                currentPlayback?.transcodeInfo?.let {
                    TranscodeInfo(it, Modifier.weight(2f))
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
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        rows.forEach {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = it.first,
                    modifier = Modifier.width(100.dp),
                )
                Text(
                    text = it.second.toString(),
                    modifier = Modifier,
                )
            }
        }
    }
}
