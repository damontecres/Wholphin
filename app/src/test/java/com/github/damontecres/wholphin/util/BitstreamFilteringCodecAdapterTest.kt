@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.util

import android.media.MediaCodec
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Covers the hand off to the decoder: the adapter has to queue the size the filters report, not
 * the size it was called with, and it has to leave the buffers no filter may touch alone.
 */
class BitstreamFilteringCodecAdapterTest {
    private val delegate = mockk<MediaCodecAdapter>(relaxed = true)
    private val buffer = ByteBuffer.allocateDirect(SIZE)

    @Test
    fun queuesTheSizeTheFilterReports() {
        every { delegate.getInputBuffer(INDEX) } returns buffer
        val adapter = adapterWith(BitstreamFilter { _, _, size -> size - 2 })

        adapter.queueInputBuffer(INDEX, OFFSET, SIZE, PRESENTATION_TIME_US, 0)

        verify { delegate.queueInputBuffer(INDEX, OFFSET, SIZE - 2, PRESENTATION_TIME_US, 0) }
    }

    @Test
    fun queuesTheSizeItWasGivenWhenTheFilterLeavesTheAccessUnitAlone() {
        every { delegate.getInputBuffer(INDEX) } returns buffer
        val adapter = adapterWith(BitstreamFilter { _, _, size -> size })

        adapter.queueInputBuffer(INDEX, OFFSET, SIZE, PRESENTATION_TIME_US, 0)

        verify { delegate.queueInputBuffer(INDEX, OFFSET, SIZE, PRESENTATION_TIME_US, 0) }
    }

    @Test
    fun leavesCodecConfigBuffersAlone() {
        every { delegate.getInputBuffer(INDEX) } returns buffer
        val filter = RecordingFilter()
        val adapter = adapterWith(filter)

        adapter.queueInputBuffer(INDEX, OFFSET, SIZE, PRESENTATION_TIME_US, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)

        assertEquals(0, filter.calls)
        verify {
            delegate.queueInputBuffer(INDEX, OFFSET, SIZE, PRESENTATION_TIME_US, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
        }
    }

    @Test
    fun leavesEmptyBuffersAlone() {
        every { delegate.getInputBuffer(INDEX) } returns buffer
        val filter = RecordingFilter()
        val adapter = adapterWith(filter)

        adapter.queueInputBuffer(INDEX, OFFSET, 0, PRESENTATION_TIME_US, 0)

        assertEquals(0, filter.calls)
        verify { delegate.queueInputBuffer(INDEX, OFFSET, 0, PRESENTATION_TIME_US, 0) }
    }

    @Test
    fun queuesTheSizeItWasGivenWhenThereIsNoInputBuffer() {
        every { delegate.getInputBuffer(INDEX) } returns null
        val filter = RecordingFilter()
        val adapter = adapterWith(filter)

        adapter.queueInputBuffer(INDEX, OFFSET, SIZE, PRESENTATION_TIME_US, 0)

        assertEquals(0, filter.calls)
        verify { delegate.queueInputBuffer(INDEX, OFFSET, SIZE, PRESENTATION_TIME_US, 0) }
    }

    private fun adapterWith(filter: BitstreamFilter) = BitstreamFilteringCodecAdapter(delegate, listOf(filter))

    private class RecordingFilter : BitstreamFilter {
        var calls = 0

        override fun filter(
            data: ByteBuffer,
            offset: Int,
            size: Int,
        ): Int {
            calls++
            return size
        }
    }

    companion object {
        private const val INDEX = 3
        private const val OFFSET = 1
        private const val SIZE = 8
        private const val PRESENTATION_TIME_US = 1234L
    }
}
