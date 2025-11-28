package com.github.damontecres.wholphin.util

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.github.damontecres.wholphin.ui.playback.idAsInt
import timber.log.Timber
import java.util.Locale

/**
 * Represents a track in a media file along with information about whether the device can handle it natively or not
 */
data class TrackSupport(
    val id: String?,
    val type: TrackType,
    val supported: TrackSupportReason,
    val selected: Boolean,
    val labels: List<String>,
    val codecs: String?,
    val format: Format,
) {
    @OptIn(UnstableApi::class)
    fun displayString(context: Context): String =
        if (labels.isNotEmpty()) {
            labels.joinToString(", ")
        } else {
            val type =
                when (codecs) {
                    MimeTypes.TEXT_VTT -> "vtt"
                    MimeTypes.APPLICATION_VOBSUB -> "vobsub"
                    MimeTypes.APPLICATION_SUBRIP -> "srt"
                    MimeTypes.TEXT_SSA -> "ssa"
                    MimeTypes.APPLICATION_PGS -> "pgs"
                    MimeTypes.APPLICATION_DVBSUBS -> "dvd"
                    MimeTypes.APPLICATION_TTML -> "ttml"
                    MimeTypes.TEXT_UNKNOWN -> "unknown"
                    null -> "unknown"
                    else -> {
                        val split = codecs.split("/")
                        if (split.size > 1) split[1] else codecs
                    }
                }
            val language = languageName(format.language)
            "$language ($type)"
        }
}

enum class TrackSupportReason {
    HANDLED,
    EXCEEDS_CAPABILITIES,
    UNSUPPORTED_DRM,
    UNSUPPORTED_SUBTYPE,
    UNSUPPORTED_TYPE,
    UNKNOWN,
    ;

    companion object {
        @OptIn(UnstableApi::class)
        fun fromInt(
            @C.FormatSupport value: Int,
        ): TrackSupportReason =
            when (value) {
                C.FORMAT_HANDLED -> HANDLED
                C.FORMAT_EXCEEDS_CAPABILITIES -> EXCEEDS_CAPABILITIES
                C.FORMAT_UNSUPPORTED_DRM -> UNSUPPORTED_DRM
                C.FORMAT_UNSUPPORTED_SUBTYPE -> UNSUPPORTED_SUBTYPE
                C.FORMAT_UNSUPPORTED_TYPE -> UNSUPPORTED_TYPE
                else -> UNKNOWN
            }
    }
}

enum class TrackType {
    UNKNOWN,
    DEFAULT,
    AUDIO,
    VIDEO,
    TEXT,
    IMAGE,
    METADATA,
    CAMERA_MOTION,
    NONE,
    ;

    companion object {
        @OptIn(UnstableApi::class)
        fun fromInt(value: Int): TrackType =
            when (value) {
                C.TRACK_TYPE_UNKNOWN -> UNKNOWN
                C.TRACK_TYPE_DEFAULT -> DEFAULT
                C.TRACK_TYPE_AUDIO -> AUDIO
                C.TRACK_TYPE_VIDEO -> VIDEO
                C.TRACK_TYPE_TEXT -> TEXT
                C.TRACK_TYPE_IMAGE -> IMAGE
                C.TRACK_TYPE_METADATA -> METADATA
                C.TRACK_TYPE_CAMERA_MOTION -> CAMERA_MOTION
                C.TRACK_TYPE_NONE -> NONE
                else -> UNKNOWN
            }
    }
}

@OptIn(UnstableApi::class)
fun checkForSupport(tracks: Tracks): List<TrackSupport> =
    tracks.groups.flatMap {
        buildList {
            val type = TrackType.fromInt(it.type)
            for (i in 0..<it.length) {
                val format = it.getTrackFormat(i)
                val labels =
                    format.labels
                        .map {
                            if (it.language != null) {
                                "${it.value} (${it.language})"
                            } else {
                                it.value
                            }
                        } +
                        if (type == TrackType.VIDEO) {
                            listOf("res=${format.width}x${format.height}")
                        } else if (type == TrackType.AUDIO) {
                            listOf("channels=${format.channelCount}", "lang=${format.language}")
                        } else if (type == TrackType.TEXT) {
                            listOf("lang=${format.language}")
                        } else {
                            listOf()
                        }
                val reason = TrackSupportReason.fromInt(it.getTrackSupport(i))
                add(
                    TrackSupport(
                        format.id + " (${format.idAsInt})",
                        type,
                        reason,
                        it.isSelected,
                        labels,
                        format.codecs,
                        format,
                    ),
                )
            }
        }
    }

fun languageName(code: String?): String =
    if (code == "und") {
        "Unknown"
    } else if (code != null) {
        try {
            Locale(code).displayLanguage
        } catch (ex: Exception) {
            Timber.w(ex, "Error in locale for '$code'")
            code.uppercase()
        }
    } else {
        "Unknown"
    }
