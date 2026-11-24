@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.util

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class EqualBitrateVariantTrackSelectionFactoryTest {
    private fun videoFormat(
        id: String,
        bitrate: Int,
        width: Int = 3840,
        height: Int = 1600,
    ) = Format
        .Builder()
        .setId(id)
        .setSampleMimeType(MimeTypes.VIDEO_H265)
        .setPeakBitrate(bitrate)
        .setWidth(width)
        .setHeight(height)
        .setFrameRate(23.976f)
        .build()

    @Test
    fun dropsEqualBitrateFallbackVariants() {
        // HDR remux followed by the SDR transcode fallback, as listed by the server
        val group = TrackGroup(videoFormat("0", 16_655_950), videoFormat("1", 16_655_950))
        val result = ExoTrackSelection.Definition(group, 0, 1).withoutEqualBitrateVideoVariants()
        assertArrayEquals(intArrayOf(0), result.tracks)
    }

    @Test
    fun keepsFirstVariantRegardlessOfTrackOrder() {
        val group = TrackGroup(videoFormat("0", 16_655_950), videoFormat("1", 16_655_950))
        val result = ExoTrackSelection.Definition(group, 1, 0).withoutEqualBitrateVideoVariants()
        assertArrayEquals(intArrayOf(0), result.tracks)
    }

    @Test
    fun keepsAdaptiveBitrateVariants() {
        val group =
            TrackGroup(
                videoFormat("0", 16_655_950),
                videoFormat("1", 16_655_950),
                videoFormat("2", 8_000_000, width = 1920, height = 800),
                videoFormat("3", 4_000_000, width = 1920, height = 800),
            )
        val result = ExoTrackSelection.Definition(group, 0, 1, 2, 3).withoutEqualBitrateVideoVariants()
        assertArrayEquals(intArrayOf(0, 2, 3), result.tracks)
    }

    @Test
    fun keepsVariantsWithDifferentResolution() {
        val group =
            TrackGroup(
                videoFormat("0", 8_000_000),
                videoFormat("1", 8_000_000, width = 1920, height = 800),
            )
        val definition = ExoTrackSelection.Definition(group, 0, 1)
        assertSame(definition, definition.withoutEqualBitrateVideoVariants())
    }

    @Test
    fun keepsVariantsWithUnknownBitrate() {
        val group = TrackGroup(videoFormat("0", Format.NO_VALUE), videoFormat("1", Format.NO_VALUE))
        val definition = ExoTrackSelection.Definition(group, 0, 1)
        assertSame(definition, definition.withoutEqualBitrateVideoVariants())
    }

    @Test
    fun ignoresNonVideoGroups() {
        val audio =
            Format
                .Builder()
                .setSampleMimeType(MimeTypes.AUDIO_E_AC3)
                .setPeakBitrate(768_000)
                .build()
        val group = TrackGroup(audio, audio.buildUpon().setId("1").build())
        val definition = ExoTrackSelection.Definition(group, 0, 1)
        assertSame(definition, definition.withoutEqualBitrateVideoVariants())
    }
}
