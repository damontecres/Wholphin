package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.test.TestTracks
import com.github.damontecres.wholphin.test.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeMediaStreamTest {
    @Test
    fun `Test getRelativeIndex`() {
        val source =
            TestTracks
                .Builder()
                .addSubtitle(2)
                .addAudio(2)
                .addVideo()
                .buildMediaSourceInfo()
        val streams = source.mediaStreams!!
        RelativeMediaStream.getRelativeIndex(0, streams).let { relativeIndex ->
            assertNotNull(relativeIndex)
            assertEquals(0, relativeIndex)
        }
        RelativeMediaStream.getRelativeIndex(1, streams).let { relativeIndex ->
            assertNotNull(relativeIndex)
            assertEquals(1, relativeIndex)
        }
        RelativeMediaStream.getRelativeIndex(2, streams).let { relativeIndex ->
            assertNotNull(relativeIndex)
            assertEquals(0, relativeIndex)
        }
        RelativeMediaStream.getRelativeIndex(3, streams).let { relativeIndex ->
            assertNotNull(relativeIndex)
            assertEquals(1, relativeIndex)
        }
    }

    @Test
    fun `Test getRelativeIndex for stream`() {
        val source =
            TestTracks
                .Builder()
                .addSubtitle(2)
                .addAudio(2)
                .addVideo()
                .buildMediaSourceInfo()
        val streams = source.mediaStreams!!
        RelativeMediaStream.getRelativeIndex(streams[1], streams).let { relativeIndex ->
            assertNotNull(relativeIndex)
            assertEquals(1, relativeIndex)
        }
    }

    @Test
    fun `Test asRelative basic`() {
        val source =
            TestTracks
                .Builder()
                .addSubtitle(2)
                .addAudio(2)
                .addVideo()
                .buildMediaSourceInfo()
        val streams = source.mediaStreams!!
        assertEquals(5, streams.size)

        val relative = streams.asRelative()
        // Subtitle
        assertEquals(0, relative[0].relativeIndex)
        assertEquals(0, relative[0].stream.index)
        assertEquals(1, relative[1].relativeIndex)
        assertEquals(1, relative[1].stream.index)

        // Audio
        assertEquals(0, relative[2].relativeIndex)
        assertEquals(2, relative[2].stream.index)
        assertEquals(1, relative[3].relativeIndex)
        assertEquals(3, relative[3].stream.index)

        // Video
        assertEquals(0, relative[4].relativeIndex)
        assertEquals(4, relative[4].stream.index)
    }

    @Test
    fun `Test asRelative with external`() {
        val source =
            TestTracks
                .Builder()
                .addExternalSubtitle(2)
                .addSubtitle(2)
                .addAudio(2)
                .buildMediaSourceInfo()
        val streams = source.mediaStreams!!
        assertEquals(6, streams.size)

        val relative = streams.asRelative()
        // External Subtitle
        assertEquals(0, relative[0].relativeIndex)
        assertEquals(0, relative[0].stream.index)
        assertEquals(1, relative[1].relativeIndex)
        assertEquals(1, relative[1].stream.index)

        // Subtitle
        assertEquals(0, relative[2].relativeIndex)
        assertEquals(2, relative[2].stream.index)
        assertEquals(1, relative[3].relativeIndex)
        assertEquals(3, relative[3].stream.index)

        // Audio
        assertEquals(0, relative[4].relativeIndex)
        assertEquals(4, relative[4].stream.index)
        assertEquals(1, relative[5].relativeIndex)
        assertEquals(5, relative[5].stream.index)
    }

    @Test
    fun `Test asRelative with external mixed`() {
        val source =
            TestTracks
                .Builder()
                .addExternalSubtitle(1)
                .addSubtitle(1)
                .addAudio(1)
                .addExternalSubtitle(1)
                .addAudio(1)
                .addSubtitle(1)
                .buildMediaSourceInfo()
        val streams = source.mediaStreams!!
        assertEquals(6, streams.size)

        val relative = streams.asRelative()
        // External Subtitle
        assertEquals(0, relative[0].relativeIndex)
        assertEquals(0, relative[0].stream.index)
        assertEquals(1, relative[3].relativeIndex)
        assertEquals(3, relative[3].stream.index)

        // Subtitle
        assertEquals(0, relative[1].relativeIndex)
        assertEquals(1, relative[1].stream.index)
        assertEquals(1, relative[5].relativeIndex)
        assertEquals(5, relative[5].stream.index)

        // Audio
        assertEquals(0, relative[2].relativeIndex)
        assertEquals(2, relative[2].stream.index)
        assertEquals(1, relative[4].relativeIndex)
        assertEquals(4, relative[4].stream.index)
    }
}
