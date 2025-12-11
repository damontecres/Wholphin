package com.github.damontecres.wholphin.ui.data

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
) {
    val context = LocalContext.current
    // Extract stringResource calls outside of ScrollableDialog's non-composable lambda
    val pathLabel = stringResource(R.string.path)
    val fileSizeLabel = stringResource(R.string.file_size)
    val videoLabel = stringResource(R.string.video)
    val audioLabel = stringResource(R.string.audio)
    val subtitleLabel = stringResource(R.string.subtitle)
    
    ScrollableDialog(
        onDismissRequest = onDismissRequest,
        width = 720.dp,
        maxHeight = 480.dp,
        itemSpacing = 4.dp,
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
        
        // Show detailed media information for the selected source (first one if multiple)
        info.files.firstOrNull()?.let { source ->
            if (source.mediaStreams?.isNotEmpty() == true) {
                item {
                    Spacer(Modifier.height(8.dp))
                }
                
                // General file information
                item {
                    val containerLabel = stringResource(R.string.container)
                    MediaInfoSection(
                        title = stringResource(R.string.general),
                        items = buildList {
                            source.container?.let { add(containerLabel to it) }
                            if (showFilePath) {
                                source.path?.let { add(pathLabel to it) }
                            }
                            source.size?.let {
                                add(fileSizeLabel to formatBytes(it))
                            }
                        },
                    )
                }

                // Video streams
                source.mediaStreams?.filter { it.type == MediaStreamType.VIDEO }?.let { videoStreams ->
                    items(videoStreams) { stream ->
                        MediaInfoSection(
                            title = videoLabel,
                            items = buildVideoStreamInfo(context, stream),
                        )
                    }
                }

                // Audio streams - display multiple per row
                source.mediaStreams?.filter { it.type == MediaStreamType.AUDIO }?.let { audioStreams ->
                    items(audioStreams.chunked(3)) { streamGroup ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            streamGroup.forEach { stream ->
                                MediaInfoSection(
                                    title = audioLabel,
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
                }

                // Subtitle streams - display multiple per row
                source.mediaStreams?.filter { it.type == MediaStreamType.SUBTITLE }?.let { subtitleStreams ->
                    items(subtitleStreams.chunked(3)) { streamGroup ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            streamGroup.forEach { stream ->
                                MediaInfoSection(
                                    title = subtitleLabel,
                                    items = buildSubtitleStreamInfo(context, stream),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // Fill remaining space if less than 3 items
                            repeat(3 - streamGroup.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
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
    val titleLabel = context.getString(R.string.title)
    val codecLabel = context.getString(R.string.codec)
    val avcLabel = context.getString(R.string.avc)
    val profileLabel = context.getString(R.string.profile)
    val levelLabel = context.getString(R.string.level)
    val resolutionLabel = context.getString(R.string.resolution)
    val aspectRatioLabel = context.getString(R.string.aspect_ratio)
    val anamorphicLabel = context.getString(R.string.anamorphic)
    val interlacedLabel = context.getString(R.string.interlaced)
    val framerateLabel = context.getString(R.string.framerate)
    val bitrateLabel = context.getString(R.string.bitrate)
    val bitDepthLabel = context.getString(R.string.bit_depth)
    val videoRangeLabel = context.getString(R.string.video_range)
    val videoRangeTypeLabel = context.getString(R.string.video_range_type)
    val colorSpaceLabel = context.getString(R.string.color_space)
    val colorTransferLabel = context.getString(R.string.color_transfer)
    val colorPrimariesLabel = context.getString(R.string.color_primaries)
    val pixelFormatLabel = context.getString(R.string.pixel_format)
    val refFramesLabel = context.getString(R.string.ref_frames)
    val nalLabel = context.getString(R.string.nal)
    val yesStr = context.getString(R.string.yes)
    val noStr = context.getString(R.string.no)
    val sdrStr = context.getString(R.string.sdr)
    val hdrStr = context.getString(R.string.hdr)
    val hdr10Str = context.getString(R.string.hdr10)
    val hdr10PlusStr = context.getString(R.string.hdr10_plus)
    val hlgStr = context.getString(R.string.hlg)
    val bitUnit = context.getString(R.string.bit_unit)
    
    stream.title?.let { add(titleLabel to it) }
    stream.codec?.let { add(codecLabel to it.uppercase()) }
    stream.isAvc?.let { add(avcLabel to if (it) yesStr else noStr) }
    stream.profile?.let { add(profileLabel to it) }
    stream.level?.let { add(levelLabel to it.toString()) }
    if (stream.width != null && stream.height != null) {
        add(resolutionLabel to "${stream.width}x${stream.height}")
    }
    if (stream.width != null && stream.height != null) {
        val aspectRatio = calculateAspectRatio(stream.width!!, stream.height!!)
        add(aspectRatioLabel to aspectRatio)
    }
    stream.isAnamorphic?.let { add(anamorphicLabel to if (it) yesStr else noStr) }
    stream.isInterlaced?.let { add(interlacedLabel to if (it) yesStr else noStr) }
    stream.averageFrameRate?.let {
        add(framerateLabel to String.format(Locale.getDefault(), "%.6f", it))
    }
    stream.bitRate?.let { add(bitrateLabel to formatBytes(it, byteRateSuffixes)) }
    stream.bitDepth?.let { add(bitDepthLabel to "$it $bitUnit") }
    stream.videoRange?.let { 
        val rangeStr = when (it) {
            VideoRange.SDR -> sdrStr
            VideoRange.HDR -> hdrStr
            VideoRange.UNKNOWN -> null
            else -> null
        }
        rangeStr?.let { add(videoRangeLabel to it) }
    }
    stream.videoRangeType?.let {
        val rangeTypeStr = when (it) {
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
    stream.nalLengthSize?.let { add(nalLabel to it.toString()) }
}

private fun buildAudioStreamInfo(
    context: Context,
    stream: MediaStream,
): List<Pair<String, String>> = buildList {
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
): List<Pair<String, String>> = buildList {
    val titleLabel = context.getString(R.string.title)
    val languageLabel = context.getString(R.string.language)
    val codecLabel = context.getString(R.string.codec)
    val avcLabel = context.getString(R.string.avc)
    val defaultLabel = context.getString(R.string.default_track)
    val forcedLabel = context.getString(R.string.forced_track)
    val externalLabel = context.getString(R.string.external_track)
    val yesStr = context.getString(R.string.yes)
    val noStr = context.getString(R.string.no)
    
    stream.title?.let { add(titleLabel to it) }
    stream.language?.let { add(languageLabel to languageName(it)) }
    stream.codec?.let { 
        val formattedCodec = formatSubtitleCodec(it) ?: it.uppercase()
        add(codecLabel to formattedCodec)
    }
    stream.isAvc?.let { add(avcLabel to if (it) yesStr else noStr) }
    stream.isDefault?.let { add(defaultLabel to if (it) yesStr else noStr) }
    stream.isForced?.let { add(forcedLabel to if (it) yesStr else noStr) }
    stream.isExternal?.let { add(externalLabel to if (it) yesStr else noStr) }
}

private fun calculateAspectRatio(width: Int, height: Int): String {
    val gcd = gcd(width, height)
    val w = width / gcd
    val h = height / gcd
    return "$w:$h"
}

private fun gcd(a: Int, b: Int): Int {
    return if (b == 0) a else gcd(b, a % b)
}
