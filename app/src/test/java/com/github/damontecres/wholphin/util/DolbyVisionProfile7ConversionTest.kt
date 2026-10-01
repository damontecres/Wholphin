package com.github.damontecres.wholphin.util

import androidx.media3.common.DrmInitData
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DolbyVisionProfile7ConversionTest {
    @Test
    fun recognisesProfile7InBothSampleEntryForms() {
        assertTrue(DolbyVisionProfile7Conversion.isProfile7(dolbyVision("dvhe.07.06")))
        assertTrue(DolbyVisionProfile7Conversion.isProfile7(dolbyVision("dvh1.07.06")))
    }

    @Test
    fun doesNotMistakeOtherProfilesOrCodecsForProfile7() {
        assertFalse(DolbyVisionProfile7Conversion.isProfile7(dolbyVision("dvhe.08.06")))
        assertFalse(DolbyVisionProfile7Conversion.isProfile7(dolbyVision("dvhe.05.06")))
        assertFalse(DolbyVisionProfile7Conversion.isProfile7(dolbyVision(null)))
        assertFalse(
            DolbyVisionProfile7Conversion.isProfile7(
                Format
                    .Builder()
                    .setSampleMimeType(MimeTypes.VIDEO_H265)
                    .setCodecs("dvhe.07.06")
                    .build(),
            ),
        )
    }

    @Test
    fun rewritesOnlyTheProfileOfTheCodecString() {
        assertEquals("dvhe.08.06", DolbyVisionProfile7Conversion.toProfile8("dvhe.07.06"))
        assertEquals("dvh1.08.09", DolbyVisionProfile7Conversion.toProfile8("dvh1.07.09"))
    }

    @Test
    fun advertisesAProfile7TrackAsProfile8AndKeepsTheRestOfTheFormat() {
        val format = dolbyVision("dvhe.07.06")

        val advertised = DolbyVisionProfile7Conversion.advertiseAsProfile8(format)

        assertEquals("dvhe.08.06", advertised.codecs)
        assertEquals(MimeTypes.VIDEO_DOLBY_VISION, advertised.sampleMimeType)
        assertEquals(format.width, advertised.width)
        assertEquals(format.height, advertised.height)
        assertEquals(format.initializationData, advertised.initializationData)
    }

    @Test
    fun leavesTracksWhichAreNotProfile7AsTheSameInstance() {
        val profile8 = dolbyVision("dvhe.08.06")
        val hevc = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).build()

        assertSame(profile8, DolbyVisionProfile7Conversion.advertiseAsProfile8(profile8))
        assertSame(hevc, DolbyVisionProfile7Conversion.advertiseAsProfile8(hevc))
    }

    @Test
    fun leavesAnEncryptedProfile7TrackAlone() {
        // Encrypted samples take the secure path, which the bitstream filter does not touch
        val encrypted =
            dolbyVision("dvhe.07.06")
                .buildUpon()
                .setDrmInitData(DrmInitData(DrmInitData.SchemeData(java.util.UUID.randomUUID(), MimeTypes.VIDEO_MP4, byteArrayOf(1))))
                .build()

        assertSame(encrypted, DolbyVisionProfile7Conversion.advertiseAsProfile8(encrypted))
    }

    private companion object {
        fun dolbyVision(codecs: String?): Format =
            Format
                .Builder()
                .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                .setCodecs(codecs)
                .setWidth(3840)
                .setHeight(2160)
                .setInitializationData(listOf(byteArrayOf(0x01, 0x02)))
                .build()
    }
}
