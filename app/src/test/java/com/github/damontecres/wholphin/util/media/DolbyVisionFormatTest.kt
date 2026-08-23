package com.github.damontecres.wholphin.util.media

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DolbyVisionFormatTest {
    @Test
    fun isHevcDolbyVision_acceptsDvheAndDvh1TokensCaseInsensitively() {
        assertTrue(format("dvhe.08.06").isHevcDolbyVision())
        assertTrue(format("DVH1.08.06").isHevcDolbyVision())
        assertTrue(format("hev1.2.4.L153.B0, DVHE.08.06").isHevcDolbyVision())
        assertTrue(format("dvh1").isHevcDolbyVision())
    }

    @Test
    fun isHevcDolbyVision_rejectsNonHevcDolbyVisionCodecs() {
        listOf(
            "dvav.09.06",
            "dva1.09.06",
            "dav1.10.06",
            "hev1.2.4.L153.B0",
            "avc1.640028",
        ).forEach { codecs ->
            assertFalse(codecs, format(codecs).isHevcDolbyVision())
        }
    }

    @Test
    fun isHevcDolbyVision_rejectsNullPartialAndMalformedCodecTokens() {
        listOf<String?>(
            null,
            "",
            "not-dvhe.08.06",
            "dvhe-other",
            "dvhe.",
            "dvhe..08",
            "dvh1.08 06",
            ", ,",
        ).forEach { codecs ->
            assertFalse(codecs, format(codecs).isHevcDolbyVision())
        }
    }

    @Test
    fun isHevcDolbyVision_requiresDolbyVisionMimeType() {
        val format =
            Format
                .Builder()
                .setSampleMimeType(MimeTypes.VIDEO_H265)
                .setCodecs("dvhe.08.06")
                .build()

        assertFalse(format.isHevcDolbyVision())
    }

    private fun format(codecs: String?): Format =
        Format
            .Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setCodecs(codecs)
            .build()
}
