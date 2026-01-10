package com.github.damontecres.wholphin.ui.util

import android.content.Context
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.util.languageName
import com.github.damontecres.wholphin.util.profile.Codec
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRange
import org.jellyfin.sdk.model.api.VideoRangeType

/**
 * Collection of utility functions for formatting the display of media streams
 */
object StreamFormatting {
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

    fun formatVideoRange(
        context: Context,
        videoRange: VideoRange?,
        type: VideoRangeType?,
        doviTitle: String?,
    ): String? =
        when (videoRange) {
            VideoRange.UNKNOWN,
            VideoRange.SDR, null,
            -> {
                null
            }

            VideoRange.HDR -> {
                if (doviTitle.isNotNullOrBlank()) {
                    context.getString(R.string.dolby_vision)
                } else {
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
            }
        }

    fun formatAudioCodec(
        context: Context,
        codec: String?,
        profile: String?,
    ): String? =
        when {
            profile?.contains("Dolby Atmos", true) == true -> {
                context.getString(R.string.dolby_atmos)
            }

            profile?.contains("DTS:X", true) == true -> {
                context.getString(R.string.dts_x)
            }

            profile?.contains("DTS:HD", true) == true -> {
                context.getString(R.string.dts_hd)
            }

            else -> {
                when (codec?.lowercase()) {
                    Codec.Audio.TRUEHD -> context.getString(R.string.truehd)

                    Codec.Audio.AC3 -> context.getString(R.string.dolby_digital)

                    Codec.Audio.EAC3 -> context.getString(R.string.dolby_digital_plus)

                    Codec.Audio.DCA -> context.getString(R.string.dts)

                    Codec.Audio.OGG,
                    Codec.Audio.OPUS,
                    Codec.Audio.VORBIS,
                    -> codec.replaceFirstChar { it.uppercase() }

                    null -> null

                    else -> codec.uppercase()
                }
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

    fun mediaStreamDisplayTitle(
        context: Context,
        stream: MediaStream,
        includeFlags: Boolean,
    ): String {
        val name =
            buildList {
                add(languageName(stream.language))
                if (stream.type == MediaStreamType.AUDIO) {
                    add(formatAudioCodec(context, stream.codec, stream.profile))
                    add(stream.channelLayout)
                } else if (stream.type == MediaStreamType.SUBTITLE) {
                    "SDH".takeIf { stream.isHearingImpaired }?.let(::add)
                    add(formatSubtitleCodec(stream.codec))
                }
            }.joinToString(" ")
        if (includeFlags) {
            val flags =
                buildList {
                    if (stream.isDefault) add(stream.localizedDefault)
                    if (stream.isForced) add(stream.localizedForced)
                    if (stream.isExternal) add(stream.localizedExternal)
                }.joinToString(", ")
            if (flags.isNotEmpty()) {
                return "$name ($flags)"
            }
        }
        return name
    }
}
