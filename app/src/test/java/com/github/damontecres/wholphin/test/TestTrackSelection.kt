package com.github.damontecres.wholphin.test

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.ui.playback.TrackSelectionUtils
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.junit.Assert
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.readText

class TestTrackSelection {
    /**
     * Builds the tracks for the `embedded_subs.json` for the given backend
     *
     * Note: This is manual based on observation & code review of the playback for that file
     */
    private fun buildEmbeddedTracks(backend: PlayerBackend): Tracks {
        val formats =
            if (backend == PlayerBackend.MPV) {
                val video =
                    Format
                        .Builder()
                        .setId("0:1")
                        .setSampleMimeType("video/default")
                        .build()
                val audios =
                    (1..3).map {
                        Format
                            .Builder()
                            .setId("$it:$it")
                            .setSampleMimeType("audio/default")
                            .build()
                    }
                val subtitles =
                    (1..3).map {
                        Format
                            .Builder()
                            .setId("${it + 3}:$it")
                            .setSampleMimeType("text/default")
                            .build()
                    }
                (listOf(video) + audios + subtitles)
            } else {
                val video =
                    Format
                        .Builder()
                        .setId("1")
                        .setSampleMimeType("video/default")
                        .build()
                val audios =
                    (2..4).map {
                        Format
                            .Builder()
                            .setId("$it")
                            .setSampleMimeType("audio/default")
                            .build()
                    }
                val subtitles =
                    (5..7).map {
                        Format
                            .Builder()
                            .setId("$it")
                            .setSampleMimeType("text/default")
                            .build()
                    }
                (listOf(video) + audios + subtitles)
            }
        val groups =
            formats
                .map { TrackGroup(it) }
                .map { Tracks.Group(it, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false)) }
        return Tracks(groups)
    }

    /**
     * Builds the tracks for the `no_embedded_subs.json` for the given backend
     *
     * Note: This is manual based on observation & code review of the playback for that file
     */
    private fun buildNoEmbeddedTracks(backend: PlayerBackend): Tracks {
        val formats =
            if (backend == PlayerBackend.MPV) {
                val video =
                    Format
                        .Builder()
                        .setId("0:1")
                        .setSampleMimeType("video/default")
                        .build()
                val audios =
                    (1..3).map {
                        Format
                            .Builder()
                            .setId("$it:$it")
                            .setSampleMimeType("audio/default")
                            .build()
                    }
                val subtitles =
                    (1..3).map {
                        Format
                            .Builder()
                            .setId("${it + 3}:$it")
                            .setSampleMimeType("text/default")
                            .build()
                    } +
                        listOf(
                            Format
                                .Builder()
                                .setId("7:e:4")
                                .setSampleMimeType("text/default")
                                .build(),
                        )
                (listOf(video) + audios + subtitles)
            } else {
                // ExoPlayer
                val video =
                    Format
                        .Builder()
                        .setId("0:1")
                        .setSampleMimeType("video/default")
                        .build()
                val audios =
                    (2..4).map {
                        Format
                            .Builder()
                            .setId("0:$it")
                            .setSampleMimeType("audio/default")
                            .build()
                    }
                val subtitles =
                    (5..7).map {
                        Format
                            .Builder()
                            .setId("0:$it")
                            .setSampleMimeType("text/default")
                            .build()
                    } +
                        listOf(
                            Format
                                .Builder()
                                .setId("1:e:0")
                                .setSampleMimeType("text/default")
                                .build(),
                        )
                (listOf(video) + audios + subtitles)
            }
        val groups =
            formats
                .map { TrackGroup(it) }
                .map { Tracks.Group(it, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false)) }
        return Tracks(groups)
    }

    /**
     * Builds the tracks for the `external_subs.json` for the given backend.
     *
     * Must supply the desired subtitle index because ExoPlayer uses it.
     *
     * Note: This is manual based on observation & code review of the playback for that file
     */
    private fun buildExternalTracks(
        backend: PlayerBackend,
        selectedIndex: Int,
    ): Tracks {
        val formats =
            if (backend == PlayerBackend.MPV) {
                val video =
                    Format
                        .Builder()
                        .setId("1:1")
                        .setSampleMimeType("video/default")
                        .build()
                val audios =
                    listOf(
                        Format
                            .Builder()
                            .setId("0:1")
                            .setSampleMimeType("audio/default")
                            .build(),
                    )
                val subtitles =
                    listOf(
                        Format
                            .Builder()
                            .setId("2:1")
                            .setSampleMimeType("text/default")
                            .build(),
                        Format
                            .Builder()
                            .setId("3:e:2")
                            .setSampleMimeType("text/default")
                            .build(),
                    )
                (listOf(video) + audios + subtitles)
            } else {
                val video =
                    Format
                        .Builder()
                        .setId("0:2")
                        .setSampleMimeType("video/default")
                        .build()
                val audios =
                    listOf(
                        Format
                            .Builder()
                            .setId("0:1")
                            .setSampleMimeType("audio/default")
                            .build(),
                    )
                val subtitles =
                    listOf(
                        Format
                            .Builder()
                            .setId("0:3") // Embedded
                            .setSampleMimeType("text/default")
                            .build(),
                        Format
                            .Builder()
                            .setId("1:e:$selectedIndex") // External
                            .setSampleMimeType("text/default")
                            .build(),
                    )
                (listOf(video) + audios + subtitles)
            }
        val groups =
            formats
                .map { TrackGroup(it) }
                .map { Tracks.Group(it, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false)) }
        return Tracks(groups)
    }

    /**
     * Builds the tracks for the `no_embedded_subs.json` for the given backend but skips a track ID
     *
     * Note: This is manual based on observation & code review of the playback for that file
     */
    private fun buildMissingIdTracks(backend: PlayerBackend): Tracks {
        val formats =
            if (backend == PlayerBackend.MPV) {
                val video =
                    Format
                        .Builder()
                        .setId("0:1")
                        .setSampleMimeType("video/default")
                        .build()
                val audios =
                    (1..3).map {
                        Format
                            .Builder()
                            .setId("$it:$it")
                            .setSampleMimeType("audio/default")
                            .build()
                    }
                val subtitles =
                    (1..3).map {
                        Format
                            .Builder()
                            .setId("${it + 3}:$it")
                            .setSampleMimeType("text/default")
                            .build()
                    }
                (listOf(video) + audios + subtitles)
            } else {
                // ExoPlayer
                val video =
                    Format
                        .Builder()
                        .setId("0:1")
                        .setSampleMimeType("video/default")
                        .build()
                val audios =
                    (3..5).map {
                        Format
                            .Builder()
                            .setId("0:$it")
                            .setSampleMimeType("audio/default")
                            .build()
                    }
                val subtitles =
                    (6..8).map {
                        Format
                            .Builder()
                            .setId("0:$it")
                            .setSampleMimeType("text/default")
                            .build()
                    }
                (listOf(video) + audios + subtitles)
            }
        val groups =
            formats
                .map { TrackGroup(it) }
                .map {
                    Tracks.Group(
                        it,
                        false,
                        intArrayOf(C.FORMAT_HANDLED),
                        booleanArrayOf(false),
                    )
                }
        return Tracks(groups)
    }

    private fun TrackSelectionParameters.getAudioOverride(): Format? {
        this.overrides.forEach { (trackGroup, trackSelectionOverride) ->
            if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
                return trackGroup.getFormat(trackSelectionOverride.trackIndices.first())
            }
        }
        return null
    }

    private fun TrackSelectionParameters.getSubtitleOverride(): Format? {
        this.overrides.forEach { (trackGroup, trackSelectionOverride) ->
            if (trackGroup.type == C.TRACK_TYPE_TEXT) {
                return trackGroup.getFormat(trackSelectionOverride.trackIndices.first())
            }
        }
        return null
    }

    @Test
    fun `test MPV embedded`() {
        val resource = javaClass.classLoader?.getResource("embedded_subs.json")
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        val source = Json.decodeFromString<MediaSourceInfo>(fileContents)
        val tracks = buildEmbeddedTracks(PlayerBackend.MPV)
        Assert.assertEquals(7, source.mediaStreams?.size)

        val trackSelectionParameters = TrackSelectionParameters.Builder().build()

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                playerBackend = PlayerBackend.MPV,
                supportsDirectPlay = true,
                audioIndex = 1,
                subtitleIndex = 4,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("1:1", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals("4:1", result.trackSelectionParameters.getSubtitleOverride()?.id)
            }

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                playerBackend = PlayerBackend.MPV,
                supportsDirectPlay = true,
                audioIndex = 2,
                subtitleIndex = 4,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("2:2", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals("4:1", result.trackSelectionParameters.getSubtitleOverride()?.id)
            }

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                playerBackend = PlayerBackend.MPV,
                supportsDirectPlay = true,
                audioIndex = 1,
                subtitleIndex = TrackIndex.DISABLED,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("1:1", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals(null, result.trackSelectionParameters.getSubtitleOverride()?.id)
            }
    }

    @Test
    fun `test ExoPlayer embedded`() {
        val resource = javaClass.classLoader?.getResource("embedded_subs.json")
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        val source = Json.decodeFromString<MediaSourceInfo>(fileContents)
        val tracks = buildEmbeddedTracks(PlayerBackend.EXO_PLAYER)
        Assert.assertEquals(7, source.mediaStreams?.size)

        val trackSelectionParameters = TrackSelectionParameters.Builder().build()

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                playerBackend = PlayerBackend.EXO_PLAYER,
                supportsDirectPlay = true,
                audioIndex = 1,
                subtitleIndex = 4,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("2", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals("5", result.trackSelectionParameters.getSubtitleOverride()?.id)
            }

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                playerBackend = PlayerBackend.EXO_PLAYER,
                supportsDirectPlay = true,
                audioIndex = 2,
                subtitleIndex = 4,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("3", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals("5", result.trackSelectionParameters.getSubtitleOverride()?.id)
            }

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                playerBackend = PlayerBackend.EXO_PLAYER,
                supportsDirectPlay = true,
                audioIndex = 2,
                subtitleIndex = 6,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("3", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals("7", result.trackSelectionParameters.getSubtitleOverride()?.id)
            }

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                playerBackend = PlayerBackend.EXO_PLAYER,
                supportsDirectPlay = true,
                audioIndex = 1,
                subtitleIndex = TrackIndex.DISABLED,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("2", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals(null, result.trackSelectionParameters.getSubtitleOverride()?.id)
            }
    }

    @Test
    fun `test MPV no embedded`() {
        val resource = javaClass.classLoader?.getResource("no_embedded_subs.json")
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        val source = Json.decodeFromString<MediaSourceInfo>(fileContents)
        val tracks = buildNoEmbeddedTracks(PlayerBackend.MPV)
        Assert.assertEquals(5, source.mediaStreams?.size)

        val trackSelectionParameters = TrackSelectionParameters.Builder().build()

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                playerBackend = PlayerBackend.MPV,
                supportsDirectPlay = true,
                audioIndex = 2,
                subtitleIndex = 0,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("1:1", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals("7:e:4", result.trackSelectionParameters.getSubtitleOverride()?.id)
            }

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                playerBackend = PlayerBackend.MPV,
                supportsDirectPlay = true,
                audioIndex = 3,
                subtitleIndex = 0,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("2:2", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals("7:e:4", result.trackSelectionParameters.getSubtitleOverride()?.id)
            }
    }

    @Test
    fun `test ExoPlayer no embedded`() {
        val resource = javaClass.classLoader?.getResource("no_embedded_subs.json")
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        val source = Json.decodeFromString<MediaSourceInfo>(fileContents)
        val tracks = buildNoEmbeddedTracks(PlayerBackend.EXO_PLAYER)
        Assert.assertEquals(5, source.mediaStreams?.size)

        val trackSelectionParameters = TrackSelectionParameters.Builder().build()

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                playerBackend = PlayerBackend.EXO_PLAYER,
                supportsDirectPlay = true,
                audioIndex = 2,
                subtitleIndex = 0,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("0:2", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals("1:e:0", result.trackSelectionParameters.getSubtitleOverride()?.id)
            }
    }

    @Test
    fun `test MPV external`() {
        val resource = javaClass.classLoader?.getResource("external_subs.json")
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        val source = Json.decodeFromString<MediaSourceInfo>(fileContents)
        val tracks = buildExternalTracks(PlayerBackend.MPV, 0)
        Assert.assertEquals(6, source.mediaStreams?.size)

        val trackSelectionParameters = TrackSelectionParameters.Builder().build()

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                playerBackend = PlayerBackend.MPV,
                supportsDirectPlay = true,
                audioIndex = 3,
                subtitleIndex = 0,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.audioSelected)
                Assert.assertTrue(result.subtitleSelected)
                Assert.assertEquals("0:1", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals("3:e:2", result.trackSelectionParameters.getSubtitleOverride()?.id)
            }

        // Select embedded subtitles
        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                playerBackend = PlayerBackend.MPV,
                supportsDirectPlay = true,
                audioIndex = 3,
                subtitleIndex = 5,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.audioSelected)
                Assert.assertTrue(result.subtitleSelected)
                Assert.assertEquals(
                    "0:1",
                    result.trackSelectionParameters.getAudioOverride()?.id,
                )
                Assert.assertEquals(
                    "2:1",
                    result.trackSelectionParameters.getSubtitleOverride()?.id,
                )
            }
    }

    @Test
    fun `test ExoPlayer external`() {
        val resource = javaClass.classLoader?.getResource("external_subs.json")
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        val source = Json.decodeFromString<MediaSourceInfo>(fileContents)

        buildExternalTracks(PlayerBackend.EXO_PLAYER, 0).also { tracks ->
            Assert.assertEquals(6, source.mediaStreams?.size)

            val trackSelectionParameters = TrackSelectionParameters.Builder().build()

            TrackSelectionUtils
                .createTrackSelections(
                    trackSelectionParams = trackSelectionParameters,
                    tracks = tracks,
                    playerBackend = PlayerBackend.EXO_PLAYER,
                    supportsDirectPlay = true,
                    audioIndex = 3,
                    subtitleIndex = 0,
                    source = source,
                ).also { result ->
                    Assert.assertTrue(result.audioSelected)
                    Assert.assertTrue(result.subtitleSelected)
                    Assert.assertEquals(
                        "0:1",
                        result.trackSelectionParameters.getAudioOverride()?.id,
                    )
                    Assert.assertEquals(
                        "1:e:0",
                        result.trackSelectionParameters.getSubtitleOverride()?.id,
                    )
                }

            // Select embedded subtitles
            TrackSelectionUtils
                .createTrackSelections(
                    trackSelectionParams = trackSelectionParameters,
                    tracks = tracks,
                    playerBackend = PlayerBackend.EXO_PLAYER,
                    supportsDirectPlay = true,
                    audioIndex = 3,
                    subtitleIndex = 5,
                    source = source,
                ).also { result ->
                    Assert.assertTrue(result.audioSelected)
                    Assert.assertTrue(result.subtitleSelected)
                    Assert.assertEquals(
                        "0:1",
                        result.trackSelectionParameters.getAudioOverride()?.id,
                    )
                    Assert.assertEquals(
                        "0:3",
                        result.trackSelectionParameters.getSubtitleOverride()?.id,
                    )
                }
        }

        buildExternalTracks(PlayerBackend.EXO_PLAYER, 2).also { tracks ->
            Assert.assertEquals(6, source.mediaStreams?.size)

            val trackSelectionParameters = TrackSelectionParameters.Builder().build()

            TrackSelectionUtils
                .createTrackSelections(
                    trackSelectionParams = trackSelectionParameters,
                    tracks = tracks,
                    playerBackend = PlayerBackend.EXO_PLAYER,
                    supportsDirectPlay = true,
                    audioIndex = 3,
                    subtitleIndex = 2,
                    source = source,
                ).also { result ->
                    Assert.assertTrue(result.audioSelected)
                    Assert.assertTrue(result.subtitleSelected)
                    Assert.assertEquals(
                        "0:1",
                        result.trackSelectionParameters.getAudioOverride()?.id,
                    )
                    Assert.assertEquals(
                        "1:e:2",
                        result.trackSelectionParameters.getSubtitleOverride()?.id,
                    )
                }
        }
    }

    @Test
    fun `test ExoPlayer missing ids`() {
        val resource = javaClass.classLoader?.getResource("no_embedded_subs.json")
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        val source = Json.decodeFromString<MediaSourceInfo>(fileContents)
        val tracks = buildMissingIdTracks(PlayerBackend.EXO_PLAYER)
        val ids = tracks.groups.flatMap { g -> (0..<g.length).map { g.getTrackFormat(it).id } }
        Assert.assertTrue("0:1" in ids)
        Assert.assertTrue("0:2" !in ids)
        Assert.assertTrue("0:3" in ids)

        Assert.assertEquals(5, source.mediaStreams?.size)

        val trackSelectionParameters = TrackSelectionParameters.Builder().build()

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                playerBackend = PlayerBackend.EXO_PLAYER,
                supportsDirectPlay = true,
                audioIndex = 3,
                subtitleIndex = TrackIndex.DISABLED,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("0:4", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals(null, result.trackSelectionParameters.getSubtitleOverride()?.id)
            }
    }

    @Test
    fun `test MPV missing ids`() {
        val resource = javaClass.classLoader?.getResource("no_embedded_subs.json")
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        val source = Json.decodeFromString<MediaSourceInfo>(fileContents)
        val tracks = buildMissingIdTracks(PlayerBackend.MPV)
        val ids = tracks.groups.flatMap { g -> (0..<g.length).map { g.getTrackFormat(it).id } }
        println(ids)

        Assert.assertEquals(5, source.mediaStreams?.size)

        val trackSelectionParameters = TrackSelectionParameters.Builder().build()

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                playerBackend = PlayerBackend.MPV,
                supportsDirectPlay = true,
                audioIndex = 3,
                subtitleIndex = TrackIndex.DISABLED,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("2:2", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals(null, result.trackSelectionParameters.getSubtitleOverride()?.id)
            }
    }
}
