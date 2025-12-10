package com.github.damontecres.wholphin.ui.data

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.byteRateSuffixes
import com.github.damontecres.wholphin.ui.components.ScrollableDialog
import com.github.damontecres.wholphin.ui.components.formatAudioCodec
import com.github.damontecres.wholphin.ui.components.formatSubtitleCodec
import com.github.damontecres.wholphin.ui.components.formatVideoRange
import com.github.damontecres.wholphin.ui.formatBytes
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.util.languageName
import org.jellyfin.sdk.model.api.AudioSpatialFormat
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRange
import org.jellyfin.sdk.model.api.VideoRangeType
import java.util.Locale

data class ItemDetailsDialogInfo(
    val title: String,
    val overview: String?,
    val genres: List<String>,
    val files: List<MediaSourceInfo>,
)

@Composable
fun ItemDetailsDialog(
    info: ItemDetailsDialogInfo,
    showFilePath: Boolean,
    onDismissRequest: () -> Unit,
) = ScrollableDialog(
    onDismissRequest = onDismissRequest,
) {
    item {
        Text(
            text = info.title,
            style = MaterialTheme.typography.titleLarge,
        )
    }
    if (info.genres.isNotEmpty()) {
        item {
            Text(
                text = info.genres.joinToString(", "),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
    if (info.overview.isNotNullOrBlank()) {
        item {
            Text(
                text = info.overview,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    if (info.files.isNotEmpty()) {
        item {
            Spacer(Modifier.height(12.dp))
        }
    }
    items(info.files) { file ->
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            MediaSourceInfo(file, showFilePath, Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
fun MediaSourceInfo(
    source: MediaSourceInfo,
    showFilePath: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.name) + ": ${source.name}",
        )
        Text(
            text = "ID: ${source.id}",
        )
        if (showFilePath) {
            Text(
                text = stringResource(R.string.path) + ": ${source.path}",
            )
        }
        source.size?.let { size ->
            Text(
                text = stringResource(R.string.file_size) + ": ${formatBytes(size)}",
            )
        }
        source.bitrate?.let { bitrate ->
            Text(
                text =
                    stringResource(R.string.bitrate) + ": ${
                        formatBytes(
                            bitrate,
                            byteRateSuffixes,
                        )
                    }",
            )
        }
        source.mediaStreams?.letNotEmpty { streams ->
            streams.filter { it.type == MediaStreamType.VIDEO }.forEach { stream ->
                val data =
                    buildList {
                        if (stream.width != null && stream.height != null) {
                            add("${stream.width}x${stream.height}")
                        }
                        stream.averageFrameRate?.let {
                            add(String.format(Locale.getDefault(), "%.3f", it) + " fps")
                        }
                        stream.bitRate?.let { add(formatBytes(it, byteRateSuffixes)) }
                        stream.codec?.let(::add)
                        stream.profile?.let(::add)
                    }
                Text(
                    text = stringResource(R.string.video) + ": " + data.joinToString(" - "),
                )
            }

            streams.filter { it.type == MediaStreamType.AUDIO }.forEachIndexed { index, stream ->
                val data =
                    buildList {
                        stream.language?.let { add(languageName(it)) }
                        stream.codec?.let(::add)
                        stream.channelLayout?.let(::add)
                        stream.bitRate?.let { add(formatBytes(it, byteRateSuffixes)) }
                        if (stream.audioSpatialFormat != AudioSpatialFormat.NONE) add(stream.audioSpatialFormat.serialName)
                        if (stream.isDefault) add(stringResource(R.string.default_track))
                    }
                Text(
                    text = stringResource(R.string.audio) + " ${index + 1}: " + data.joinToString(" - "),
                )
            }

            streams.filter { it.type == MediaStreamType.SUBTITLE }.forEachIndexed { index, stream ->
                val data =
                    buildList {
                        stream.language?.let { add(languageName(it)) }
                        stream.codec?.let(::add)
                        if (stream.isDefault) add(stringResource(R.string.default_track))
                        if (stream.isForced) add(stringResource(R.string.forced_track))
                        if (stream.isExternal) add(stringResource(R.string.external_track))
                    }
                Text(
                    text =
                        stringResource(R.string.subtitle) + " ${index + 1}: " +
                            data.joinToString(" - "),
                )
            }
        }
    }
}

data class MediaInfoDialogData(
    val itemName: String,
    val source: MediaSourceInfo,
    val showFilePath: Boolean,
)

@Composable
fun MediaInfoDialog(
    data: MediaInfoDialogData,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    ScrollableDialog(
        onDismissRequest = onDismissRequest,
        width = 800.dp,
        maxHeight = 600.dp,
        itemSpacing = 4.dp,
    ) {
        item {
        Text(
            text = stringResource(R.string.media_information),
            style = MaterialTheme.typography.titleLarge,
        )
    }
    item {
        Spacer(Modifier.height(4.dp))
    }
    item {
        Text(
            text = data.itemName,
            style = MaterialTheme.typography.titleMedium,
        )
    }
    item {
        Spacer(Modifier.height(8.dp))
    }
    
    // General file information
    item {
        MediaInfoSection(
            title = "General",
            items = buildList {
                data.source.container?.let { add("Container" to it) }
                if (data.showFilePath) {
                    data.source.path?.let { add(stringResource(R.string.path) to it) }
                }
                data.source.size?.let { 
                    add(stringResource(R.string.file_size) to formatBytes(it)) 
                }
            },
        )
    }
    
    // Video streams
    data.source.mediaStreams?.filter { it.type == MediaStreamType.VIDEO }?.let { videoStreams ->
        items(videoStreams) { stream ->
            MediaInfoSection(
                title = stringResource(R.string.video),
                items = buildVideoStreamInfo(context, stream),
            )
        }
    }
    
    // Audio streams
    data.source.mediaStreams?.filter { it.type == MediaStreamType.AUDIO }?.let { audioStreams ->
        items(audioStreams) { stream ->
            MediaInfoSection(
                title = stringResource(R.string.audio),
                items = buildAudioStreamInfo(context, stream),
            )
        }
    }
    
    // Subtitle streams
    data.source.mediaStreams?.filter { it.type == MediaStreamType.SUBTITLE }?.let { subtitleStreams ->
        items(subtitleStreams) { stream ->
            MediaInfoSection(
                title = stringResource(R.string.subtitle),
                items = buildSubtitleStreamInfo(context, stream),
            )
        }
    }
    }
}

@Composable
private fun MediaInfoSection(
    title: String,
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.padding(vertical = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        items.forEach { (label, value) ->
            Row(
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Text(
                    text = "$label: ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun buildVideoStreamInfo(
    context: Context,
    stream: MediaStream,
): List<Pair<String, String>> = buildList {
    stream.title?.let { add("Title" to it) }
    stream.codec?.let { add("Codec" to it.uppercase()) }
    stream.isAvc?.let { add("AVC" to if (it) "Yes" else "No") }
    stream.profile?.let { add("Profile" to it) }
    stream.level?.let { add("Level" to it.toString()) }
    if (stream.width != null && stream.height != null) {
        add("Resolution" to "${stream.width}x${stream.height}")
    }
    if (stream.width != null && stream.height != null) {
        val aspectRatio = calculateAspectRatio(stream.width!!, stream.height!!)
        add("Aspect ratio" to aspectRatio)
    }
    stream.isAnamorphic?.let { add("Anamorphic" to if (it) "Yes" else "No") }
    stream.isInterlaced?.let { add("Interlaced" to if (it) "Yes" else "No") }
    stream.averageFrameRate?.let {
        add("Framerate" to String.format(Locale.getDefault(), "%.6f", it))
    }
    stream.bitRate?.let { add("Bitrate" to formatBytes(it, byteRateSuffixes)) }
    stream.bitDepth?.let { add("Bit depth" to "$it bit") }
    stream.videoRange?.let { 
        val rangeStr = when (it) {
            VideoRange.SDR -> "SDR"
            VideoRange.HDR -> "HDR"
            VideoRange.UNKNOWN -> null
            else -> null
        }
        rangeStr?.let { add("Video range" to it) }
    }
    stream.videoRangeType?.let {
        val rangeTypeStr = when (it) {
            VideoRangeType.SDR -> "SDR"
            VideoRangeType.HDR10 -> "HDR10"
            VideoRangeType.HDR10_PLUS -> "HDR10+"
            VideoRangeType.HLG -> "HLG"
            VideoRangeType.DOVI,
            VideoRangeType.DOVI_WITH_HDR10,
            VideoRangeType.DOVI_WITH_HLG,
            VideoRangeType.DOVI_WITH_SDR,
            -> context.getString(R.string.dolby_vision)
            VideoRangeType.UNKNOWN -> null
            else -> null
        }
        rangeTypeStr?.let { add("Video range type" to it) }
    }
    stream.colorSpace?.let { add("Color space" to it) }
    stream.colorTransfer?.let { add("Color transfer" to it) }
    stream.colorPrimaries?.let { add("Color primaries" to it) }
    stream.pixelFormat?.let { add("Pixel format" to it) }
    stream.refFrames?.let { add("Ref frames" to it.toString()) }
    stream.nalLengthSize?.let { add("NAL" to it.toString()) }
}

private fun buildAudioStreamInfo(
    context: Context,
    stream: MediaStream,
): List<Pair<String, String>> = buildList {
    stream.title?.let { add("Title" to it) }
    stream.language?.let { add("Language" to languageName(it)) }
    stream.codec?.let { 
        val formattedCodec = formatAudioCodec(context, it, stream.profile) ?: it.uppercase()
        add("Codec" to formattedCodec)
    }
    stream.isAvc?.let { add("AVC" to if (it) "Yes" else "No") }
    stream.channelLayout?.let { add("Layout" to it) }
    stream.channels?.let { add("Channels" to "$it ch") }
    stream.bitRate?.let { add("Bitrate" to formatBytes(it, byteRateSuffixes)) }
    stream.sampleRate?.let { add("Sample rate" to "$it Hz") }
    stream.isDefault?.let { add("Default" to if (it) "Yes" else "No") }
    stream.isForced?.let { add("Forced" to if (it) "Yes" else "No") }
    stream.isExternal?.let { add("External" to if (it) "Yes" else "No") }
}

private fun buildSubtitleStreamInfo(
    context: Context,
    stream: MediaStream,
): List<Pair<String, String>> = buildList {
    stream.title?.let { add("Title" to it) }
    stream.language?.let { add("Language" to languageName(it)) }
    stream.codec?.let { 
        val formattedCodec = formatSubtitleCodec(it) ?: it.uppercase()
        add("Codec" to formattedCodec)
    }
    stream.isAvc?.let { add("AVC" to if (it) "Yes" else "No") }
    stream.isDefault?.let { add("Default" to if (it) "Yes" else "No") }
    stream.isForced?.let { add("Forced" to if (it) "Yes" else "No") }
    stream.isExternal?.let { add("External" to if (it) "Yes" else "No") }
}

private fun calculateAspectRatio(width: Int, height: Int): String {
    val gcd = gcd(width, height)
    val w = width / gcd
    val h = height / gcd
    
    // Common aspect ratios
    return when {
        w == 16 && h == 9 -> "16:9"
        w == 4 && h == 3 -> "4:3"
        w == 21 && h == 9 -> "21:9"
        w == 1 && h == 1 -> "1:1"
        else -> "$w:$h"
    }
}

private fun gcd(a: Int, b: Int): Int {
    return if (b == 0) a else gcd(b, a % b)
}
