@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.util

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.TrackOutput
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The factory has to reach the video track's format through three layers of media3 forwarding,
 * rewrite it there and nowhere else, and let sample data through untouched.
 */
class DolbyVisionProfile7ExtractorsFactoryTest {
    private val videoTrack = mockk<TrackOutput>(relaxed = true)
    private val audioTrack = mockk<TrackOutput>(relaxed = true)
    private val output =
        mockk<ExtractorOutput>(relaxed = true) {
            every { track(VIDEO_ID, C.TRACK_TYPE_VIDEO) } returns videoTrack
            every { track(AUDIO_ID, C.TRACK_TYPE_AUDIO) } returns audioTrack
        }
    private val extractor = mockk<Extractor>(relaxed = true)
    private val delegate =
        mockk<ExtractorsFactory> {
            every { createExtractors() } returns arrayOf(extractor)
        }

    @Test
    fun advertisesTheVideoTrackAsProfile8() {
        val video = initialisedVideoTrack()

        video.format(dolbyVision("dvhe.07.06"))

        val published = slot<Format>()
        verify { videoTrack.format(capture(published)) }
        assertEquals("dvhe.08.06", published.captured.codecs)
    }

    @Test
    fun leavesOtherVideoFormatsAsTheyAre() {
        val video = initialisedVideoTrack()
        val hevc =
            Format
                .Builder()
                .setSampleMimeType(MimeTypes.VIDEO_H265)
                .setCodecs("hvc1.2.4.L153")
                .build()

        video.format(hevc)

        verify { videoTrack.format(hevc) }
    }

    @Test
    fun leavesTheAudioTrackUnwrapped() {
        val wrapped = wrappedOutput()

        assertSame(audioTrack, wrapped.track(AUDIO_ID, C.TRACK_TYPE_AUDIO))
    }

    @Test
    fun passesSampleDataStraightThrough() {
        val video = initialisedVideoTrack()
        val data = ParsableByteArray(byteArrayOf(1, 2, 3))

        video.sampleData(data, 3)
        video.sampleMetadata(1_000L, C.BUFFER_FLAG_KEY_FRAME, 3, 0, null)

        verify { videoTrack.sampleData(data, 3) }
        verify { videoTrack.sampleMetadata(1_000L, C.BUFFER_FLAG_KEY_FRAME, 3, 0, null) }
    }

    @Test
    fun wrapsEveryExtractorOfTheDelegate() {
        val another = mockk<Extractor>(relaxed = true)
        every { delegate.createExtractors() } returns arrayOf(extractor, another)

        val extractors = DolbyVisionProfile7ExtractorsFactory(delegate).createExtractors()

        assertEquals(2, extractors.size)
        extractors[1].init(output)
        verify { another.init(any()) }
    }

    private fun wrappedOutput(): ExtractorOutput {
        val extractors = DolbyVisionProfile7ExtractorsFactory(delegate).createExtractors()
        extractors.single().init(output)
        val wrapped = slot<ExtractorOutput>()
        verify { extractor.init(capture(wrapped)) }
        return wrapped.captured
    }

    private fun initialisedVideoTrack(): TrackOutput = wrappedOutput().track(VIDEO_ID, C.TRACK_TYPE_VIDEO)

    private companion object {
        const val VIDEO_ID = 1
        const val AUDIO_ID = 2

        fun dolbyVision(codecs: String): Format =
            Format
                .Builder()
                .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                .setCodecs(codecs)
                .build()
    }
}
