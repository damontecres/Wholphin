package com.github.damontecres.wholphin.ui.data

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.byteRateSuffixes
import com.github.damontecres.wholphin.ui.components.ScrollableDialog
import com.github.damontecres.wholphin.ui.components.formatAudioCodec
import com.github.damontecres.wholphin.ui.components.formatSubtitleCodec
import com.github.damontecres.wholphin.ui.formatBytes
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.util.languageName
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRange
import org.jellyfin.sdk.model.api.VideoRangeType
import org.jellyfin.sdk.model.extensions.ticks
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
) {
    val context = LocalContext.current
    // Extract stringResource calls outside of ScrollableDialog's non-composable lambda
    val pathLabel = stringResource(R.string.path)
    val fileSizeLabel = stringResource(R.string.file_size)
    val videoLabel = stringResource(R.string.video)
    val audioLabel = stringResource(R.string.audio)
    val subtitleLabel = stringResource(R.string.subtitle)
    val bitrateLabel = stringResource(R.string.bitrate)
    val unknown = stringResource(R.string.unknown)
    val runtimeLabel = stringResource(R.string.runtime_sort)

    ScrollableDialog(
        onDismissRequest = onDismissRequest,
        width = 680.dp,
        maxHeight = 440.dp,
        itemSpacing = 8.dp,
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (info.genres.isNotEmpty()) {
                    Text(
                        text = info.genres.joinToString(", "),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                if (info.overview.isNotNullOrBlank()) {
                    Text(
                        text = info.overview,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Show detailed media information for the selected source (first one if multiple)
        info.files.forEachIndexed { index, source ->
            source.mediaStreams?.letNotEmpty { mediaStreams ->
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                }

                // General file information
                item {
                    val containerLabel = stringResource(R.string.container)
                    MediaInfoSection(
                        title =
                            titleIndex(
                                stringResource(R.string.general),
                                index,
                                info.files.size,
                            ),
                        items =
                            buildList {
                                source.container?.let { add(containerLabel to it) }
                                if (showFilePath) {
                                    source.path?.let { add(pathLabel to it) }
                                    add("ID" to (source.id ?: unknown))
                                }
                                source.size?.let {
                                    add(fileSizeLabel to formatBytes(it))
                                }
                                source.bitrate?.let {
                                    add(
                                        bitrateLabel to formatBytes(it, byteRateSuffixes),
                                    )
                                }
                                source.runTimeTicks?.let {
                                    add(runtimeLabel to it.ticks.toString())
                                }
                            },
                    )
                }

                // Video streams
                val videoStreams = mediaStreams.filter { it.type == MediaStreamType.VIDEO }
                itemsIndexed(videoStreams) { index, stream ->
                    MediaInfoSection(
                        title = titleIndex(videoLabel, index, videoStreams.size),
                        items = remember { buildVideoStreamInfo(context, stream) },
                        additional = remember { buildVideoStreamInfoAdditional(context, stream) },
                    )
                }

                // Audio streams - display multiple per row
                val audioStreams = mediaStreams.filter { it.type == MediaStreamType.AUDIO }
                itemsIndexed(audioStreams.chunked(3)) { groupIndex, streamGroup ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        streamGroup.forEachIndexed { index, stream ->
                            MediaInfoSection(
                                title =
                                    titleIndex(
                                        audioLabel,
                                        groupIndex * 3 + index,
                                        audioStreams.size,
                                    ),
                                items = buildAudioStreamInfo(context, stream),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Fill remaining space if less than 3 items
                        repeat(3 - streamGroup.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Subtitle streams - display multiple per row
                val subtitleStreams = mediaStreams.filter { it.type == MediaStreamType.SUBTITLE }
                itemsIndexed(subtitleStreams.chunked(3)) { groupIndex, streamGroup ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        streamGroup.forEachIndexed { index, stream ->
                            MediaInfoSection(
                                title =
                                    titleIndex(
                                        subtitleLabel,
                                        groupIndex * 3 + index,
                                        subtitleStreams.size,
                                    ),
                                items = buildSubtitleStreamInfo(context, stream, showFilePath),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Fill remaining space if less than 3 items
                        repeat(3 - streamGroup.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (index != info.files.lastIndex) {
                    item {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaInfoSection(
    title: String,
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    additional: List<Pair<String, String>> = listOf(),
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.padding(vertical = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                items.forEach { (label, value) ->
                    Row(
                        modifier = Modifier,
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
            if (additional.isNotEmpty()) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    additional.forEach { (label, value) ->
                        Row(
                            modifier = Modifier,
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
        }
    }
}

fun titleIndex(
    title: String,
    index: Int,
    total: Int,
) = if (total > 1) {
    "$title (${index + 1})"
} else {
    title
}

private fun buildVideoStreamInfo(
    context: Context,
    stream: MediaStream,
): List<Pair<String, String>> =
    buildList {
        val yesStr = context.getString(R.string.yes)
        val noStr = context.getString(R.string.no)

        val titleLabel = context.getString(R.string.title)
        val codecLabel = context.getString(R.string.codec)
        val resolutionLabel = context.getString(R.string.resolution)
        val aspectRatioLabel = context.getString(R.string.aspect_ratio)
        val framerateLabel = context.getString(R.string.framerate)
        val bitrateLabel = context.getString(R.string.bitrate)
        val profileLabel = context.getString(R.string.profile)
        val levelLabel = context.getString(R.string.level)
        val interlacedLabel = context.getString(R.string.interlaced)
        val videoRangeLabel = context.getString(R.string.video_range)
        val sdrStr = context.getString(R.string.sdr)
        val hdrStr = context.getString(R.string.hdr)

        stream.title?.let { add(titleLabel to it) }
        stream.codec?.let { add(codecLabel to it.uppercase()) }
        if (stream.width != null && stream.height != null) {
            add(resolutionLabel to "${stream.width}x${stream.height}")
        }
        if (stream.width != null && stream.height != null) {
            val aspectRatio = calculateAspectRatio(stream.width!!, stream.height!!)
            add(aspectRatioLabel to aspectRatio)
        }
        stream.bitRate?.let { add(bitrateLabel to formatBytes(it, byteRateSuffixes)) }
        stream.averageFrameRate?.let {
            add(framerateLabel to String.format(Locale.getDefault(), "%.3f", it))
        }

        stream.videoRange.let {
            val rangeStr =
                when (it) {
                    VideoRange.SDR -> sdrStr
                    VideoRange.HDR -> hdrStr
                    VideoRange.UNKNOWN -> null
                }
            rangeStr?.let { add(videoRangeLabel to it) }
        }
        stream.profile?.let { add(profileLabel to it) }
        stream.level?.let { add(levelLabel to it.toString()) }
        stream.isInterlaced.let { add(interlacedLabel to if (it) yesStr else noStr) }
    }

private fun buildVideoStreamInfoAdditional(
    context: Context,
    stream: MediaStream,
): List<Pair<String, String>> =
    buildList {
        val yesStr = context.getString(R.string.yes)
        val noStr = context.getString(R.string.no)

        val avcLabel = context.getString(R.string.avc)
        val anamorphicLabel = context.getString(R.string.anamorphic)
        val bitDepthLabel = context.getString(R.string.bit_depth)

        val videoRangeTypeLabel = context.getString(R.string.video_range_type)
        val colorSpaceLabel = context.getString(R.string.color_space)
        val colorTransferLabel = context.getString(R.string.color_transfer)
        val colorPrimariesLabel = context.getString(R.string.color_primaries)
        val pixelFormatLabel = context.getString(R.string.pixel_format)
        val refFramesLabel = context.getString(R.string.ref_frames)
        val nalLabel = context.getString(R.string.nal)
        val dolbyVisionLabel = context.getString(R.string.dolby_vision)

        val sdrStr = context.getString(R.string.sdr)
        val hdr10Str = context.getString(R.string.hdr10)
        val hdr10PlusStr = context.getString(R.string.hdr10_plus)
        val hlgStr = context.getString(R.string.hlg)
        val bitUnit = context.getString(R.string.bit_unit)

        stream.isAvc?.let { add(avcLabel to if (it) yesStr else noStr) }
        stream.isAnamorphic?.let { add(anamorphicLabel to if (it) yesStr else noStr) }
        stream.bitDepth?.let { add(bitDepthLabel to "$it $bitUnit") }
        stream.videoRangeType?.let {
            val rangeTypeStr =
                when (it) {
                    VideoRangeType.SDR -> sdrStr

                    VideoRangeType.HDR10 -> hdr10Str

                    VideoRangeType.HDR10_PLUS -> hdr10PlusStr

                    VideoRangeType.HLG -> hlgStr

                    VideoRangeType.DOVI,
                    VideoRangeType.DOVI_WITH_HDR10,
                    VideoRangeType.DOVI_WITH_HLG,
                    VideoRangeType.DOVI_WITH_SDR,
                    -> context.getString(R.string.dolby_vision)

                    VideoRangeType.UNKNOWN -> null

                    else -> null
                }
            rangeTypeStr?.let { add(videoRangeTypeLabel to it) }
        }
        stream.colorSpace?.let { add(colorSpaceLabel to it) }
        stream.colorTransfer?.let { add(colorTransferLabel to it) }
        stream.colorPrimaries?.let { add(colorPrimariesLabel to it) }
        stream.pixelFormat?.let { add(pixelFormatLabel to it) }
        stream.refFrames?.let { add(refFramesLabel to it.toString()) }
        stream.nalLengthSize?.let { add(nalLabel to it) }
        stream.videoDoViTitle?.let { add(dolbyVisionLabel to it) }
    }

private fun buildAudioStreamInfo(
    context: Context,
    stream: MediaStream,
): List<Pair<String, String>> =
    buildList {
        val titleLabel = context.getString(R.string.title)
        val languageLabel = context.getString(R.string.language)
        val codecLabel = context.getString(R.string.codec)
        val layoutLabel = context.getString(R.string.layout)
        val channelsLabel = context.getString(R.string.channels)
        val bitrateLabel = context.getString(R.string.bitrate)
        val sampleRateLabel = context.getString(R.string.sample_rate)
        val defaultLabel = context.getString(R.string.default_track)
        val externalLabel = context.getString(R.string.external_track)
        val yesStr = context.getString(R.string.yes)
        val noStr = context.getString(R.string.no)
        val sampleRateUnit = context.getString(R.string.sample_rate_unit)

        stream.title?.let { add(titleLabel to it) }
        stream.language?.let { add(languageLabel to languageName(it)) }
        stream.codec?.let {
            val formattedCodec = formatAudioCodec(context, it, stream.profile) ?: it.uppercase()
            add(codecLabel to formattedCodec)
        }
        stream.channelLayout?.let { add(layoutLabel to it) }
        stream.channels?.let { add(channelsLabel to it.toString()) }
        stream.bitRate?.let { add(bitrateLabel to formatBytes(it, byteRateSuffixes)) }
        stream.sampleRate?.let { add(sampleRateLabel to "$it $sampleRateUnit") }
        stream.isDefault?.let { add(defaultLabel to if (it) yesStr else noStr) }
    }

private fun buildSubtitleStreamInfo(
    context: Context,
    stream: MediaStream,
    showPath: Boolean,
): List<Pair<String, String>> =
    buildList {
        val titleLabel = context.getString(R.string.title)
        val languageLabel = context.getString(R.string.language)
        val codecLabel = context.getString(R.string.codec)
        val defaultLabel = context.getString(R.string.default_track)
        val forcedLabel = context.getString(R.string.forced_track)
        val externalLabel = context.getString(R.string.external_track)
        val yesStr = context.getString(R.string.yes)
        val noStr = context.getString(R.string.no)
        val pathLabel = context.getString(R.string.path)

        stream.title?.let { add(titleLabel to it) }
        stream.language?.let { add(languageLabel to languageName(it)) }
        stream.codec?.let {
            val formattedCodec = formatSubtitleCodec(it) ?: it.uppercase()
            add(codecLabel to formattedCodec)
        }
        stream.isDefault?.let { add(defaultLabel to if (it) yesStr else noStr) }
        stream.isForced?.let { add(forcedLabel to if (it) yesStr else noStr) }
        stream.isExternal?.let { add(externalLabel to if (it) yesStr else noStr) }
        stream.isHearingImpaired?.let {
            add((stream.localizedHearingImpaired ?: "SDH") to if (it) yesStr else noStr)
        }
        if (showPath) {
            stream.path?.let { add(pathLabel to it) }
        }
    }

private fun calculateAspectRatio(
    width: Int,
    height: Int,
): String {
    val gcd = gcd(width, height)
    val w = width / gcd
    val h = height / gcd
    return "$w:$h"
}

private fun gcd(
    a: Int,
    b: Int,
): Int = if (b == 0) a else gcd(b, a % b)
