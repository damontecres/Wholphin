package com.github.damontecres.wholphin.util

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import com.github.damontecres.wholphin.preferences.ExperimentalPreferences
import com.github.damontecres.wholphin.preferences.enabled
import com.github.damontecres.wholphin.util.profile.MediaCodecCapabilitiesTest

/**
 * The one place which decides whether Dolby Vision profile 7 is converted to profile 8.1 on this
 * device, and how a converted track is described.
 *
 * Profile 7 is HDR10 compatible HEVC plus two extra NAL unit types: 62 carries the RPU metadata and
 * 63 the enhancement layer. A decoder which only lists profile 8 takes such a stream but does not
 * switch the display into Dolby Vision for it, so the picture comes out as the HDR10 base layer.
 * Rewriting the RPU into its profile 8.1 form and dropping the enhancement layer produces the
 * single layer stream that decoder does switch for. Nothing is re-encoded.
 *
 * The conversion has two halves which have to agree:
 * - [DolbyVisionProfile7Filter] rewrites the access units on their way into the decoder;
 * - [DolbyVisionProfile7ExtractorsFactory] rewrites the codec string of the track, because
 *   media3 picks the decoder by `Format.codecs` and a decoder which does not list profile 7 would
 *   otherwise never be chosen for it.
 *
 * Both are switched on by [isWanted], as is direct play of profile 7 in the device profile.
 */
object DolbyVisionProfile7Conversion {
    /**
     * True when the user asked for the conversion and this device is one it helps: its Dolby
     * Vision decoder lists profile 8 but not profile 7. A device which decodes profile 7 itself
     * is left alone, since it can use the enhancement layer the conversion would throw away, and
     * a device without a profile 8 decoder has nothing to hand the converted stream to.
     */
    fun isWanted(
        experimental: ExperimentalPreferences,
        mediaTest: MediaCodecCapabilitiesTest,
    ): Boolean =
        experimental.enabled { convertDolbyVisionProfile7 } &&
            !mediaTest.supportsHevcDolbyVisionEL() &&
            mediaTest.supportsHevcDolbyVisionProfile8()

    /** Whether [format] is a Dolby Vision profile 7 video track, in either sample entry form. */
    fun isProfile7(format: Format): Boolean {
        val codecs = format.codecs ?: return false
        return MimeTypes.VIDEO_DOLBY_VISION == format.sampleMimeType &&
            (codecs.startsWith(CODECS_PREFIX_DVHE_07) || codecs.startsWith(CODECS_PREFIX_DVH1_07))
    }

    /**
     * The [format] as the converted stream should be advertised: `dvhe.07.06` becomes
     * `dvhe.08.06`, the level and the sample entry prefix stay. Encrypted tracks are left as they
     * are: their samples reach the decoder through the secure path the filter does not touch, so
     * advertising them as converted would be a lie.
     */
    fun advertiseAsProfile8(format: Format): Format =
        if (isProfile7(format) && format.drmInitData == null) {
            format.buildUpon().setCodecs(toProfile8(format.codecs!!)).build()
        } else {
            format
        }

    /** `dvhe.07.06` → `dvhe.08.06`. The caller checks [isProfile7] first. */
    fun toProfile8(codecs: String): String = codecs.substring(0, 5) + "08" + codecs.substring(7)

    private const val CODECS_PREFIX_DVHE_07 = "dvhe.07"
    private const val CODECS_PREFIX_DVH1_07 = "dvh1.07"
}
