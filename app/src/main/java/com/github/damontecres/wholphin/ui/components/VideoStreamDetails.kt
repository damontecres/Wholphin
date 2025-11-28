package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.data.model.chooseSource
import com.github.damontecres.wholphin.data.model.chooseStream
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.playback.audioStreamCount
import com.github.damontecres.wholphin.ui.playback.embeddedSubtitleCount
import com.github.damontecres.wholphin.ui.playback.externalSubtitlesCount
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.util.languageName
import com.github.damontecres.wholphin.util.profile.Codec
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRange
import org.jellyfin.sdk.model.api.VideoRangeType

@Composable
fun VideoStreamDetails(
    preferences: UserPreferences,
    dto: BaseItemDto,
    itemPlayback: ItemPlayback?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        val source = remember(dto, itemPlayback) { chooseSource(dto, itemPlayback) }

        val videoStream =
            remember(dto, itemPlayback) {
                chooseStream(
                    dto,
                    itemPlayback,
                    MediaStreamType.VIDEO,
                    preferences,
                )
            }
        val video =
            remember(videoStream) {
                videoStream
                    ?.let {
                        val width = it.width
                        val height = it.height
                        val resName =
                            if (width != null && height != null) {
                                resolutionString(
                                    width,
                                    height,
                                    videoStream.isInterlaced,
                                )
                            } else {
                                null
                            }
                        val range = formatVideoRange(context, it.videoRange, it.videoRangeType)
                        listOfNotNull(
                            resName.concatWithSpace(range),
                            it.codec?.uppercase(),
                        )
                    }.orEmpty()
            }
        video.forEach {
            StreamLabel(it)
        }

        val audioStream =
            remember(dto, itemPlayback) {
                chooseStream(
                    dto,
                    itemPlayback,
                    MediaStreamType.AUDIO,
                    preferences,
                )
            }
        val audioCount = remember(source) { source?.audioStreamCount ?: 0 }
        val audio =
            if (audioCount == 0 || audioStream == null) {
                stringResource(R.string.none)
            } else {
                listOfNotNull(
                    languageName(audioStream.language),
                    formatAudioCodec(context, audioStream.codec, audioStream.profile),
                    audioStream.channelLayout,
                ).joinToString(" ")
            }
        StreamLabel(
            text = audio,
            count = audioCount,
            icon = R.string.fa_volume_high,
            modifier = Modifier.widthIn(max = 200.dp),
        )

        val subtitleStream =
            remember(dto, itemPlayback) {
                chooseStream(
                    dto,
                    itemPlayback,
                    MediaStreamType.SUBTITLE,
                    preferences,
                )
            }
        val subtitleCount =
            remember(source) {
                (source?.embeddedSubtitleCount ?: 0) + (source?.externalSubtitlesCount ?: 0)
            }
        val subtitle =
            if (itemPlayback?.subtitleIndex == TrackIndex.DISABLED) {
                stringResource(R.string.disabled)
            } else if (subtitleCount == 0 || subtitleStream == null) {
                null
            } else {
                listOfNotNull(
                    languageName(subtitleStream.language),
                    "SDH".takeIf { subtitleStream.isHearingImpaired },
                    formatSubtitleCodec(subtitleStream.codec),
                ).joinToString(" ")
            }
        subtitle?.let {
            StreamLabel(
                text = it,
                count = subtitleCount,
                icon = R.string.fa_closed_captioning,
                modifier = Modifier.widthIn(max = 160.dp),
            )
        }
    }
}

fun interlaced(interlaced: Boolean) = if (interlaced) "i" else "p"

// Adapted from https://github.com/jellyfin/jellyfin/blob/aa4ddd139a7c01889a99561fc314121ba198dd70/MediaBrowser.Model/Entities/MediaStream.cs#L714
fun resolutionString(
    width: Int,
    height: Int,
    interlaced: Boolean,
): String =
    if (height > width) {
        // Vertical video
        resolutionString(height, width, interlaced)
    } else {
        when {
            width <= 256 && height <= 144 -> "144" + interlaced(interlaced)
            width <= 426 && height <= 240 -> "240" + interlaced(interlaced)
            width <= 640 && height <= 360 -> "360" + interlaced(interlaced)
            width <= 682 && height <= 384 -> "384" + interlaced(interlaced)
            width <= 720 && height <= 404 -> "404" + interlaced(interlaced)
            width <= 854 && height <= 480 -> "480" + interlaced(interlaced)
            width <= 960 && height <= 544 -> "540" + interlaced(interlaced)
            width <= 1024 && height <= 576 -> "576" + interlaced(interlaced)
            width <= 1280 && height <= 962 -> "720" + interlaced(interlaced)
            width <= 2560 && height <= 1440 -> "1080" + interlaced(interlaced)
            width <= 4096 && height <= 3072 -> "4K"
            width <= 8192 && height <= 6144 -> "8K"
            else -> height.toString() + interlaced(interlaced)
        }
    }

@Composable
fun StreamLabel(
    text: String,
    modifier: Modifier = Modifier,
    @StringRes icon: Int? = null,
    count: Int = 0,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier =
            modifier
                .background(
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .5f),
//                    MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                ).padding(vertical = 4.dp, horizontal = 6.dp),
    ) {
        ProvideTextStyle(
            TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        ) {
            if (icon != null) {
                Text(
                    text = stringResource(icon),
                    fontFamily = FontAwesome,
                    modifier = Modifier,
                )
            }
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier,
            )
            if (count > 1) {
                Text(
                    text = "(+${count - 1})",
                    maxLines = 1,
                    modifier = Modifier,
                )
            }
        }
    }
}

@PreviewTvSpec
@Composable
private fun StreamLabelPreview() {
    WholphinTheme(appThemeColors = AppThemeColors.PURPLE) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(8.dp),
        ) {
            StreamLabel("1080p")
            StreamLabel("HDR")
            StreamLabel("H264")
            StreamLabel("AC3 5.1", icon = R.string.fa_volume_high, count = 2)
        }
    }
}

fun formatVideoRange(
    context: Context,
    videoRange: VideoRange?,
    type: VideoRangeType?,
): String? =
    when (videoRange) {
        VideoRange.UNKNOWN,
        VideoRange.SDR, null,
        -> null

        VideoRange.HDR ->
            when (type) {
                VideoRangeType.UNKNOWN,
                VideoRangeType.SDR,
                null,
                -> null

                VideoRangeType.HDR10 -> "HDR10"
                VideoRangeType.HDR10_PLUS -> "HDR10+"
                VideoRangeType.HLG -> "HLG"

                VideoRangeType.DOVI,
                VideoRangeType.DOVI_WITH_HDR10,
                VideoRangeType.DOVI_WITH_HLG,
                VideoRangeType.DOVI_WITH_SDR,
                -> context.getString(R.string.dolby_vision)
            }
    }

fun formatAudioCodec(
    context: Context,
    codec: String?,
    profile: String?,
): String? =
    when {
        profile?.contains("Dolby Atmos", true) == true -> context.getString(R.string.dolby_atmos)
        profile?.contains("DTS:X", true) == true -> "DTS:X"
        profile?.contains("DTS:HD", true) == true -> "DTS:HD"
        else ->
            when (codec?.lowercase()) {
                Codec.Audio.TRUEHD -> "TrueHD"
                Codec.Audio.OGG,
                Codec.Audio.OPUS,
                Codec.Audio.VORBIS,
                -> codec.replaceFirstChar { it.uppercase() }

                null -> null
                else -> codec.uppercase()
            }
    }

fun formatSubtitleCodec(codec: String?): String? =
    when (codec?.lowercase()) {
        Codec.Subtitle.DVBSUB -> "DVB"
        Codec.Subtitle.DVDSUB -> "DVD"
        Codec.Subtitle.PGSSUB -> "PGS"
        Codec.Subtitle.SUBRIP -> "SRT"
        null -> null
        else -> codec.uppercase()
    }

fun String?.concatWithSpace(str: String?): String? =
    when {
        this != null && str != null -> "$this $str"
        this == null -> str
        else -> this
    }
