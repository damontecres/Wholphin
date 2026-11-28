package com.github.damontecres.wholphin.util.profile

import android.content.Context
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import io.mockk.mockk
import org.junit.After
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.MediaCodecInfoBuilder
import org.robolectric.shadows.ShadowMediaCodecList

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class TestMediaCodecCapabilitiesTest {
    @After
    fun tearDown() {
        ShadowMediaCodecList.reset()
    }

    private fun registerDecoder(
        name: String,
        mime: String,
        profile: Int,
        level: Int,
        hardwareAccelerated: Boolean,
    ) {
        val profileLevel =
            CodecProfileLevel().apply {
                this.profile = profile
                this.level = level
            }
        val capabilities =
            MediaCodecInfoBuilder.CodecCapabilitiesBuilder
                .newBuilder()
                .setMediaFormat(MediaFormat.createVideoFormat(mime, 3840, 2160))
                .setIsEncoder(false)
                .setColorFormats(intArrayOf(CodecCapabilities.COLOR_FormatYUV420Flexible))
                .setProfileLevels(arrayOf(profileLevel))
                .build()
        val codecInfo =
            MediaCodecInfoBuilder
                .newBuilder()
                .setName(name)
                .setIsEncoder(false)
                .setIsHardwareAccelerated(hardwareAccelerated)
                .setIsSoftwareOnly(!hardwareAccelerated)
                .setCapabilities(capabilities)
                .build()
        ShadowMediaCodecList.addCodec(codecInfo)
    }

    @Test
    fun `AVC prefers hardware decoder level over higher software decoder level`() {
        registerDecoder(
            name = "c2.amlogic.avc.decoder",
            mime = MediaFormat.MIMETYPE_VIDEO_AVC,
            profile = CodecProfileLevel.AVCProfileMain,
            level = CodecProfileLevel.AVCLevel51,
            hardwareAccelerated = true,
        )
        registerDecoder(
            name = "c2.android.avc.decoder",
            mime = MediaFormat.MIMETYPE_VIDEO_AVC,
            profile = CodecProfileLevel.AVCProfileMain,
            level = CodecProfileLevel.AVCLevel52,
            hardwareAccelerated = false,
        )

        val capabilitiesTest = MediaCodecCapabilitiesTest(mockk<Context>(relaxed = true))
        Assert.assertEquals(51, capabilitiesTest.getAVCMainLevel())
    }

    @Test
    fun `HEVC prefers hardware decoder level over higher software decoder level`() {
        registerDecoder(
            name = "c2.amlogic.hevc.decoder",
            mime = MediaFormat.MIMETYPE_VIDEO_HEVC,
            profile = CodecProfileLevel.HEVCProfileMain,
            level = CodecProfileLevel.HEVCMainTierLevel51,
            hardwareAccelerated = true,
        )
        registerDecoder(
            name = "c2.android.hevc.decoder",
            mime = MediaFormat.MIMETYPE_VIDEO_HEVC,
            profile = CodecProfileLevel.HEVCProfileMain,
            level = CodecProfileLevel.HEVCMainTierLevel52,
            hardwareAccelerated = false,
        )

        val capabilitiesTest = MediaCodecCapabilitiesTest(mockk<Context>(relaxed = true))
        Assert.assertEquals(153, capabilitiesTest.getHevcMainLevel())
    }

    @Test
    fun `AVC falls back to software decoder level when no hardware decoder exists`() {
        registerDecoder(
            name = "c2.android.avc.decoder",
            mime = MediaFormat.MIMETYPE_VIDEO_AVC,
            profile = CodecProfileLevel.AVCProfileMain,
            level = CodecProfileLevel.AVCLevel52,
            hardwareAccelerated = false,
        )

        val capabilitiesTest = MediaCodecCapabilitiesTest(mockk<Context>(relaxed = true))
        Assert.assertEquals(52, capabilitiesTest.getAVCMainLevel())
    }

    @Test
    fun `HEVC falls back to software decoder level when no hardware decoder exists`() {
        registerDecoder(
            name = "c2.android.hevc.decoder",
            mime = MediaFormat.MIMETYPE_VIDEO_HEVC,
            profile = CodecProfileLevel.HEVCProfileMain,
            level = CodecProfileLevel.HEVCMainTierLevel51,
            hardwareAccelerated = false,
        )

        val capabilitiesTest = MediaCodecCapabilitiesTest(mockk<Context>(relaxed = true))
        Assert.assertEquals(153, capabilitiesTest.getHevcMainLevel())
    }

    @Test
    @Config(manifest = Config.NONE, sdk = [28])
    fun `AVC level query works on API 28 without hardware acceleration API`() {
        registerDecoder(
            name = "OMX.amlogic.avc.decoder.awesome",
            mime = MediaFormat.MIMETYPE_VIDEO_AVC,
            profile = CodecProfileLevel.AVCProfileMain,
            level = CodecProfileLevel.AVCLevel51,
            hardwareAccelerated = true,
        )
        registerDecoder(
            name = "OMX.google.h264.decoder",
            mime = MediaFormat.MIMETYPE_VIDEO_AVC,
            profile = CodecProfileLevel.AVCProfileMain,
            level = CodecProfileLevel.AVCLevel52,
            hardwareAccelerated = false,
        )

        val capabilitiesTest = MediaCodecCapabilitiesTest(mockk<Context>(relaxed = true))
        Assert.assertEquals(52, capabilitiesTest.getAVCMainLevel())
    }
}
