package com.github.damontecres.wholphin.test

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert
import org.junit.Test

/**
 * Represents a list of video, audio or, subtitle tracks in a file when direct playing
 *
 * @see TestTracks.Builder
 */
class TestTracks private constructor(
    val tracks: List<TestTrack> = emptyList(),
) {
    /**
     * Convert to media3 [Tracks]
     */
    fun toTracks(): Tracks {
        val groups =
            tracks
                .map {
                    val mimeType =
                        when (it.type) {
                            MediaStreamType.VIDEO -> "video/sample"
                            MediaStreamType.AUDIO -> "audio/default"
                            MediaStreamType.SUBTITLE -> "audio/text"
                            else -> throw UnsupportedOperationException("${it.type}")
                        }
                    Format
                        .Builder()
                        .setId(it.id)
                        .setSampleMimeType(mimeType)
                        .build()
                }.map { TrackGroup(it) }
                .map { Tracks.Group(it, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false)) }
        return Tracks(groups)
    }

    /**
     * Builder for adding tracks
     */
    class Builder {
        private val tracks = mutableListOf<TestTrackBuilder>()

        fun addVideo(): Builder {
            tracks.add(TestTrackBuilder(MediaStreamType.VIDEO))
            return this
        }

        fun addAudio(count: Int = 1): Builder {
            repeat(count) {
                tracks.add(TestTrackBuilder(MediaStreamType.AUDIO))
            }
            return this
        }

        fun addSubtitle(count: Int = 1): Builder {
            repeat(count) {
                tracks.add(TestTrackBuilder(MediaStreamType.SUBTITLE, false))
            }
            return this
        }

        fun addExternalSubtitle(count: Int = 1): Builder {
            repeat(count) {
                tracks.add(TestTrackBuilder(MediaStreamType.SUBTITLE, true))
            }
            return this
        }

        /**
         * Create the [TestTracks] as if being playing by ExoPlayer
         */
        fun buildForExoPlayer(): TestTracks {
            // Format is: [<file index>:][e:]<track index>, "e:" indicates external subtitle
            val hasExternal = tracks.firstOrNull { it.external } != null
            val testTracks =
                buildList {
                    var externalSubCount = 0
                    val t =
                        tracks
                            .mapIndexed { index, track ->
                                val idx = index + 1
                                if (track.external) {
                                    externalSubCount++
                                    TestTrack(
                                        "$externalSubCount:e:1",
                                        index,
                                        track.type,
                                        track.external,
                                    )
                                } else if (hasExternal) {
                                    // If there's a sidecar file, the actual file
                                    TestTrack("0:$idx", index, track.type, track.external)
                                } else {
                                    TestTrack("$idx", index, track.type, track.external)
                                }
                            }
                    addAll(t)
                }
            return TestTracks(testTracks)
        }

        /**
         * Create the [TestTracks] as if being playing by MPV
         */
        fun buildForMpv(): TestTracks {
            var videoCount = 0
            var audioCount = 0
            var subtitleCount = 0
            val testTracks =
                tracks.mapIndexed { index, track ->
                    val id =
                        if (track.external) {
                            subtitleCount++
                            "$index:e:$subtitleCount"
                        } else {
                            when (track.type) {
                                MediaStreamType.AUDIO -> {
                                    audioCount++
                                    "$index:$audioCount"
                                }

                                MediaStreamType.VIDEO -> {
                                    videoCount++
                                    "$index:$videoCount"
                                }

                                MediaStreamType.SUBTITLE -> {
                                    subtitleCount++
                                    "$index:$subtitleCount"
                                }

                                else -> {
                                    throw IllegalArgumentException("Unsupported type " + track.type)
                                }
                            }
                        }
                    TestTrack(id, index, track.type, track.external)
                }
            return TestTracks(testTracks)
        }
    }
}

data class TestTrack(
    val id: String,
    val index: Int,
    val type: MediaStreamType,
    val external: Boolean,
)

class TestTrackBuilder(
    val type: MediaStreamType,
    val external: Boolean = false,
)

fun assertIdType(
    expectedId: String,
    expectedType: MediaStreamType,
    track: TestTrack,
) {
    Assert.assertEquals(expectedId, track.id)
    Assert.assertEquals(expectedType, track.type)
}

/**
 * Varies [TestTracks.Builder] examples
 *
 * They are named by the order of the tracks, V=video, A=audio, S=subtitle
 */
object TrackExamples {
    val builderVAS =
        TestTracks
            .Builder()
            .addVideo()
            .addAudio(2)
            .addSubtitle(3)

    val builderASV =
        TestTracks
            .Builder()
            .addAudio(2)
            .addSubtitle(3)
            .addVideo()

    val builderAVS =
        TestTracks
            .Builder()
            .addAudio(2)
            .addVideo()
            .addSubtitle(3)

    val builderVASASS =
        TestTracks
            .Builder()
            .addVideo()
            .addAudio(1)
            .addSubtitle(1)
            .addAudio(1)
            .addSubtitle(2)
}

class TestTracksTests {
    @Test
    fun testMpv() {
        val testTracks =
            TestTracks
                .Builder()
                .addVideo()
                .addAudio(4)
                .addSubtitle(4)
                .addExternalSubtitle()
                .buildForMpv()
                .tracks
        Assert.assertEquals(10, testTracks.size)
        Assert.assertEquals("0:1", testTracks[0].id)
        Assert.assertEquals("1:1", testTracks[1].id)
        Assert.assertEquals("2:2", testTracks[2].id)
        Assert.assertEquals("3:3", testTracks[3].id)
        Assert.assertEquals("4:4", testTracks[4].id)
        Assert.assertEquals("5:1", testTracks[5].id)
        Assert.assertEquals("6:2", testTracks[6].id)
        Assert.assertEquals("7:3", testTracks[7].id)
        Assert.assertEquals("8:4", testTracks[8].id)
        Assert.assertEquals("9:e:5", testTracks[9].id)
    }

    @Test
    fun `test ExoPlayer with external`() {
        val testTracks =
            TestTracks
                .Builder()
                .addVideo()
                .addAudio(4)
                .addSubtitle(4)
                .addExternalSubtitle()
                .buildForExoPlayer()
                .tracks
        Assert.assertEquals(10, testTracks.size)
        Assert.assertEquals("0:1", testTracks[0].id)
        Assert.assertEquals("0:2", testTracks[1].id)
        Assert.assertEquals("0:3", testTracks[2].id)
        Assert.assertEquals("0:4", testTracks[3].id)
        Assert.assertEquals("0:5", testTracks[4].id)
        Assert.assertEquals("0:6", testTracks[5].id)
        Assert.assertEquals("0:7", testTracks[6].id)
        Assert.assertEquals("0:8", testTracks[7].id)
        Assert.assertEquals("0:9", testTracks[8].id)
        Assert.assertEquals("1:e:1", testTracks[9].id)
    }

    @Test
    fun `test ExoPlayer without external`() {
        val testTracks =
            TestTracks
                .Builder()
                .addVideo()
                .addAudio(4)
                .addSubtitle(4)
                .buildForExoPlayer()
                .tracks
        Assert.assertEquals(9, testTracks.size)
        Assert.assertEquals("1", testTracks[0].id)
        Assert.assertEquals("2", testTracks[1].id)
        Assert.assertEquals("3", testTracks[2].id)
        Assert.assertEquals("4", testTracks[3].id)
        Assert.assertEquals("5", testTracks[4].id)
        Assert.assertEquals("6", testTracks[5].id)
        Assert.assertEquals("7", testTracks[6].id)
        Assert.assertEquals("8", testTracks[7].id)
        Assert.assertEquals("9", testTracks[8].id)
    }

    @Test
    fun `test original-VAS`() {
        TrackExamples.builderVAS.buildForExoPlayer().tracks.let { exo ->
            Assert.assertEquals(6, exo.size)
            Assert.assertEquals("1", exo[0].id)
            Assert.assertEquals("2", exo[1].id)
            Assert.assertEquals("3", exo[2].id)
            Assert.assertEquals("4", exo[3].id)
            Assert.assertEquals("5", exo[4].id)
            Assert.assertEquals("6", exo[5].id)
        }

        TrackExamples.builderVAS.buildForMpv().tracks.let { mpv ->
            Assert.assertEquals(6, mpv.size)
            Assert.assertEquals("0:1", mpv[0].id)
            Assert.assertEquals("1:1", mpv[1].id)
            Assert.assertEquals("2:2", mpv[2].id)
            Assert.assertEquals("3:1", mpv[3].id)
            Assert.assertEquals("4:2", mpv[4].id)
            Assert.assertEquals("5:3", mpv[5].id)
        }
    }

    @Test
    fun `test ASV`() {
        TrackExamples.builderASV.buildForExoPlayer().tracks.let { exo ->
            Assert.assertEquals(6, exo.size)
            assertIdType("1", MediaStreamType.AUDIO, exo[0])
            assertIdType("2", MediaStreamType.AUDIO, exo[1])
            assertIdType("3", MediaStreamType.SUBTITLE, exo[2])
            assertIdType("4", MediaStreamType.SUBTITLE, exo[3])
            assertIdType("5", MediaStreamType.SUBTITLE, exo[4])
            assertIdType("6", MediaStreamType.VIDEO, exo[5])
        }

        TrackExamples.builderASV.buildForMpv().tracks.let { mpv ->
            Assert.assertEquals(6, mpv.size)
            assertIdType("0:1", MediaStreamType.AUDIO, mpv[0])
            assertIdType("1:2", MediaStreamType.AUDIO, mpv[1])
            assertIdType("2:1", MediaStreamType.SUBTITLE, mpv[2])
            assertIdType("3:2", MediaStreamType.SUBTITLE, mpv[3])
            assertIdType("4:3", MediaStreamType.SUBTITLE, mpv[4])
            assertIdType("5:1", MediaStreamType.VIDEO, mpv[5])
        }
    }

    @Test
    fun `test AVS`() {
        TrackExamples.builderAVS.buildForExoPlayer().tracks.let { exo ->
            Assert.assertEquals(6, exo.size)
            assertIdType("1", MediaStreamType.AUDIO, exo[0])
            assertIdType("2", MediaStreamType.AUDIO, exo[1])
            assertIdType("3", MediaStreamType.VIDEO, exo[2])
            assertIdType("4", MediaStreamType.SUBTITLE, exo[3])
            assertIdType("5", MediaStreamType.SUBTITLE, exo[4])
            assertIdType("6", MediaStreamType.SUBTITLE, exo[5])
        }

        TrackExamples.builderAVS.buildForMpv().tracks.let { mpv ->
            Assert.assertEquals(6, mpv.size)
            assertIdType("0:1", MediaStreamType.AUDIO, mpv[0])
            assertIdType("1:2", MediaStreamType.AUDIO, mpv[1])
            assertIdType("2:1", MediaStreamType.VIDEO, mpv[2])
            assertIdType("3:1", MediaStreamType.SUBTITLE, mpv[3])
            assertIdType("4:2", MediaStreamType.SUBTITLE, mpv[4])
            assertIdType("5:3", MediaStreamType.SUBTITLE, mpv[5])
        }
    }

    @Test
    fun `test VASASS`() {
        TrackExamples.builderVASASS.buildForExoPlayer().tracks.let { exo ->
            Assert.assertEquals(6, exo.size)
            assertIdType("1", MediaStreamType.VIDEO, exo[0])
            assertIdType("2", MediaStreamType.AUDIO, exo[1])
            assertIdType("3", MediaStreamType.SUBTITLE, exo[2])
            assertIdType("4", MediaStreamType.AUDIO, exo[3])
            assertIdType("5", MediaStreamType.SUBTITLE, exo[4])
            assertIdType("6", MediaStreamType.SUBTITLE, exo[5])
        }

        TrackExamples.builderVASASS.buildForMpv().tracks.let { mpv ->
            Assert.assertEquals(6, mpv.size)
            assertIdType("0:1", MediaStreamType.VIDEO, mpv[0])
            assertIdType("1:1", MediaStreamType.AUDIO, mpv[1])
            assertIdType("2:1", MediaStreamType.SUBTITLE, mpv[2])
            assertIdType("3:2", MediaStreamType.AUDIO, mpv[3])
            assertIdType("4:2", MediaStreamType.SUBTITLE, mpv[4])
            assertIdType("5:3", MediaStreamType.SUBTITLE, mpv[5])
        }
    }
}
