package com.github.damontecres.wholphin.test

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert
import org.junit.Test

/**
 * Represents a list of video, audio or, subtitle tracks in a file when direct playing
 *
 * @see Builder
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
                            MediaStreamType.SUBTITLE -> "text/sample"
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

        fun addEmpty(count: Int = 1): Builder {
            repeat(count) {
                tracks.add(TestTrackBuilder(null))
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
                            .mapIndexedNotNull { index, track ->
                                if (track.type == null) {
                                    return@mapIndexedNotNull null
                                }
                                if (track.external) {
                                    externalSubCount++
                                    TestTrack(
                                        "$externalSubCount:e:$index",
                                        index,
                                        track.type,
                                        track.external,
                                    )
                                } else if (hasExternal) {
                                    val idx = index + 1 - externalSubCount
                                    // If there's a sidecar file, the actual file
                                    TestTrack("0:$idx", index, track.type, track.external)
                                } else {
                                    val idx = index + 1
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
            val embeddedCount = tracks.count { !it.external }
            val embeddedSubtitleCount =
                tracks.count { it.type == MediaStreamType.SUBTITLE && !it.external }
            val externalSubCount =
                tracks.count { it.type == MediaStreamType.SUBTITLE && it.external }
//            val firstNonExternal = tracks.indexOfFirst { it.type != MediaStreamType.SUBTITLE }
            val firstExternal =
                tracks.indexOfFirst { it.type == MediaStreamType.SUBTITLE && it.external }
            val externalSubtitleAreFirst = externalSubCount > 0 && firstExternal == 0
            var videoCount = 0
            var audioCount = 0
            var subtitleCount = 0
            val testTracks =
                tracks.mapIndexedNotNull { index, track ->
                    if (track.type == null) {
                        return@mapIndexedNotNull null
                    }
                    val id =
                        if (track.external) {
                            // MPV places external subtitles last
                            val idx = index + if (externalSubtitleAreFirst) embeddedCount else 0
                            subtitleCount++
                            val count =
                                subtitleCount + if (externalSubtitleAreFirst) embeddedSubtitleCount else 0
                            "$idx:e:$count"
                        } else {
                            val idx =
                                if (externalSubtitleAreFirst) index - externalSubCount else index
                            when (track.type) {
                                MediaStreamType.AUDIO -> {
                                    audioCount++
                                    "$idx:$audioCount"
                                }

                                MediaStreamType.VIDEO -> {
                                    videoCount++
                                    "$idx:$videoCount"
                                }

                                MediaStreamType.SUBTITLE -> {
                                    subtitleCount++
                                    val count =
                                        subtitleCount - (if (externalSubtitleAreFirst) externalSubCount else 0)
                                    "$idx:$count"
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

        fun buildMediaSourceInfo(): MediaSourceInfo {
            val streams =
                tracks
                    .filter { it.type != null }
                    .mapIndexed { index, track ->
                        MediaStream(
                            index = index,
                            type = track.type!!,
                            isExternal = track.external,
                            codec = null,
                            language = null,
                            isInterlaced = false,
                            isDefault = false,
                            isForced = false,
                            isHearingImpaired = false,
                            isTextSubtitleStream = false,
                            supportsExternalStream = false,
                        )
                    }
            val source =
                MediaSourceInfo(
                    mediaStreams = streams,
                    protocol = MediaProtocol.HTTP,
                    type = MediaSourceType.DEFAULT,
                    isRemote = false,
                    readAtNativeFramerate = false,
                    ignoreDts = false,
                    ignoreIndex = false,
                    genPtsInput = false,
                    supportsTranscoding = true,
                    supportsDirectStream = true,
                    supportsDirectPlay = true,
                    isInfiniteStream = false,
                    requiresOpening = false,
                    requiresClosing = false,
                    requiresLooping = false,
                    supportsProbing = false,
                    transcodingSubProtocol = MediaStreamProtocol.HLS,
                    hasSegments = false,
                )
            return source
        }
    }

    companion object {
        fun fromMediaSourceInfo(source: MediaSourceInfo): TestTracks.Builder =
            TestTracks.Builder().apply {
                source.mediaStreams.orEmpty().forEach {
                    when (it.type) {
                        MediaStreamType.AUDIO -> addAudio()
                        MediaStreamType.VIDEO -> addVideo()
                        MediaStreamType.SUBTITLE -> if (it.isExternal) addExternalSubtitle() else addSubtitle()
                        else -> throw IllegalArgumentException("Unsupported type ${it.type}")
                    }
                }
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
    val type: MediaStreamType?,
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
 * They are named by the order of the tracks, V=video, A=audio, S=subtitle, E=external subtitle
 */
object TrackExamples {
    val builderVAASSS =
        TestTracks
            .Builder()
            .addVideo()
            .addAudio(2)
            .addSubtitle(3)

    val builderAASSSV =
        TestTracks
            .Builder()
            .addAudio(2)
            .addSubtitle(3)
            .addVideo()

    val builderAAVSSS =
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

    val builderEVAASSS =
        TestTracks
            .Builder()
            .addExternalSubtitle()
            .addVideo()
            .addAudio(2)
            .addSubtitle(3)
}

class TestTracksTests {
    @Test
    fun testMpv() {
        val testTracks =
            TestTracks
                .Builder()
                .addExternalSubtitle()
                .addVideo()
                .addAudio(4)
                .addSubtitle(4)
                .buildForMpv()
                .tracks
        Assert.assertEquals(10, testTracks.size)
        Assert.assertEquals("9:e:5", testTracks[0].id)
        Assert.assertEquals("0:1", testTracks[1].id)
        Assert.assertEquals("1:1", testTracks[2].id)
        Assert.assertEquals("2:2", testTracks[3].id)
        Assert.assertEquals("3:3", testTracks[4].id)
        Assert.assertEquals("4:4", testTracks[5].id)
        Assert.assertEquals("5:1", testTracks[6].id)
        Assert.assertEquals("6:2", testTracks[7].id)
        Assert.assertEquals("7:3", testTracks[8].id)
        Assert.assertEquals("8:4", testTracks[9].id)
    }

    @Test
    fun `test ExoPlayer with external`() {
        val testTracks =
            TestTracks
                .Builder()
                .addExternalSubtitle()
                .addVideo()
                .addAudio(4)
                .addSubtitle(4)
                .buildForExoPlayer()
                .tracks
        Assert.assertEquals(10, testTracks.size)
        Assert.assertEquals("1:e:0", testTracks[0].id)
        Assert.assertEquals("0:1", testTracks[1].id)
        Assert.assertEquals("0:2", testTracks[2].id)
        Assert.assertEquals("0:3", testTracks[3].id)
        Assert.assertEquals("0:4", testTracks[4].id)
        Assert.assertEquals("0:5", testTracks[5].id)
        Assert.assertEquals("0:6", testTracks[6].id)
        Assert.assertEquals("0:7", testTracks[7].id)
        Assert.assertEquals("0:8", testTracks[8].id)
        Assert.assertEquals("0:9", testTracks[9].id)
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
        TrackExamples.builderVAASSS.buildForExoPlayer().tracks.let { exo ->
            Assert.assertEquals(6, exo.size)
            Assert.assertEquals("1", exo[0].id)
            Assert.assertEquals("2", exo[1].id)
            Assert.assertEquals("3", exo[2].id)
            Assert.assertEquals("4", exo[3].id)
            Assert.assertEquals("5", exo[4].id)
            Assert.assertEquals("6", exo[5].id)
        }

        TrackExamples.builderVAASSS.buildForMpv().tracks.let { mpv ->
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
    fun `test VAS with external`() {
        TrackExamples.builderEVAASSS.buildForExoPlayer().tracks.let { exo ->
            Assert.assertEquals(7, exo.size)
            Assert.assertTrue(exo[0].external)
            assertIdType("1:e:0", MediaStreamType.SUBTITLE, exo[0])
            assertIdType("0:1", MediaStreamType.VIDEO, exo[1])
            assertIdType("0:2", MediaStreamType.AUDIO, exo[2])
            assertIdType("0:3", MediaStreamType.AUDIO, exo[3])
            assertIdType("0:4", MediaStreamType.SUBTITLE, exo[4])
            assertIdType("0:5", MediaStreamType.SUBTITLE, exo[5])
            assertIdType("0:6", MediaStreamType.SUBTITLE, exo[6])
        }

        TrackExamples.builderEVAASSS.buildForMpv().tracks.let { mpv ->
            Assert.assertEquals(7, mpv.size)
            Assert.assertTrue(mpv[0].external)
            Assert.assertEquals("6:e:4", mpv[0].id)
            Assert.assertEquals("0:1", mpv[1].id)
            Assert.assertEquals("1:1", mpv[2].id)
            Assert.assertEquals("2:2", mpv[3].id)
            Assert.assertEquals("3:1", mpv[4].id)
            Assert.assertEquals("4:2", mpv[5].id)
            Assert.assertEquals("5:3", mpv[6].id)
        }
    }

    @Test
    fun `test ASV`() {
        TrackExamples.builderAASSSV.buildForExoPlayer().tracks.let { exo ->
            Assert.assertEquals(6, exo.size)
            assertIdType("1", MediaStreamType.AUDIO, exo[0])
            assertIdType("2", MediaStreamType.AUDIO, exo[1])
            assertIdType("3", MediaStreamType.SUBTITLE, exo[2])
            assertIdType("4", MediaStreamType.SUBTITLE, exo[3])
            assertIdType("5", MediaStreamType.SUBTITLE, exo[4])
            assertIdType("6", MediaStreamType.VIDEO, exo[5])
        }

        TrackExamples.builderAASSSV.buildForMpv().tracks.let { mpv ->
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
        TrackExamples.builderAAVSSS.buildForExoPlayer().tracks.let { exo ->
            Assert.assertEquals(6, exo.size)
            assertIdType("1", MediaStreamType.AUDIO, exo[0])
            assertIdType("2", MediaStreamType.AUDIO, exo[1])
            assertIdType("3", MediaStreamType.VIDEO, exo[2])
            assertIdType("4", MediaStreamType.SUBTITLE, exo[3])
            assertIdType("5", MediaStreamType.SUBTITLE, exo[4])
            assertIdType("6", MediaStreamType.SUBTITLE, exo[5])
        }

        TrackExamples.builderAAVSSS.buildForMpv().tracks.let { mpv ->
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

    @Test
    fun `Test external subtitles at end`() {
        val builder =
            TestTracks
                .Builder()
                .addVideo()
                .addAudio(3)
                .addSubtitle(4)
                .addExternalSubtitle()

        builder.buildForExoPlayer().tracks.let { tracks ->
            Assert.assertEquals(9, tracks.size)
            Assert.assertEquals("0:1", tracks[0].id)
            Assert.assertEquals("0:2", tracks[1].id)
            Assert.assertEquals("0:3", tracks[2].id)
            Assert.assertEquals("0:4", tracks[3].id)
            Assert.assertEquals("0:5", tracks[4].id)
            Assert.assertEquals("0:6", tracks[5].id)
            Assert.assertEquals("0:7", tracks[6].id)
            Assert.assertEquals("0:8", tracks[7].id)
            Assert.assertEquals("1:e:8", tracks[8].id)
        }

        builder.buildForMpv().tracks.let { tracks ->
            Assert.assertEquals(9, tracks.size)
            Assert.assertEquals("0:1", tracks[0].id)
            Assert.assertEquals("1:1", tracks[1].id)
            Assert.assertEquals("2:2", tracks[2].id)
            Assert.assertEquals("3:3", tracks[3].id)
            Assert.assertEquals("4:1", tracks[4].id)
            Assert.assertEquals("5:2", tracks[5].id)
            Assert.assertEquals("6:3", tracks[6].id)
            Assert.assertEquals("7:4", tracks[7].id)
            Assert.assertEquals("8:e:5", tracks[8].id)
        }
    }
}
