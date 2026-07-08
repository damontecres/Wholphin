package com.github.damontecres.wholphin.test

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.ui.playback.TrackSelected
import com.github.damontecres.wholphin.ui.playback.TrackSelectionResult
import com.github.damontecres.wholphin.ui.playback.TrackSelectionUtils
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert
import org.junit.Test

class TestSelectionTrackExamples {
    private fun runTest(
        builder: TestTracks.Builder,
        playerBackend: PlayerBackend,
        audioIndex: Int,
        subtitleIndex: Int,
        onResult: (TrackSelectionResult) -> Unit,
    ) {
        val testTacks =
            when (playerBackend) {
                PlayerBackend.EXO_PLAYER -> builder.buildForExoPlayer()
                PlayerBackend.MPV -> builder.buildForMpv()
                else -> throw IllegalArgumentException("Unsupported backend: $playerBackend")
            }

        // These checks help check for invalid inputs
        val audioTrack = testTacks.tracks.filter { it.index == audioIndex }
        Assert.assertEquals(1, audioTrack.size)
        Assert.assertEquals(MediaStreamType.AUDIO, audioTrack.first().type)
        val subtitleTrack = testTacks.tracks.filter { it.index == subtitleIndex }
        Assert.assertEquals(1, subtitleTrack.size)
        Assert.assertEquals(MediaStreamType.SUBTITLE, subtitleTrack.first().type)

        val trackSelectionParameters = TrackSelectionParameters.Builder().build()
        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = testTacks.toTracks(),
                audioIndex = audioIndex,
                subtitleIndex = subtitleIndex,
                source = builder.buildMediaSourceInfo(),
            ).also(onResult)
    }

    @Test
    fun `test VAS Exo`() {
        runTest(TrackExamples.builderVAASSS, PlayerBackend.EXO_PLAYER, audioIndex = 1, subtitleIndex = 4) { result ->
            Assert.assertTrue(result.bothSelected)
            Assert.assertEquals("2", result.trackSelectionParameters.getAudioOverride()?.id)
            Assert.assertEquals("5", result.trackSelectionParameters.getSubtitleOverride()?.id)
        }
    }

    @Test
    fun `test VAS MPV`() {
        runTest(TrackExamples.builderVAASSS, PlayerBackend.MPV, audioIndex = 1, subtitleIndex = 4) { result ->
            Assert.assertTrue(result.bothSelected)
            Assert.assertEquals("1:1", result.trackSelectionParameters.getAudioOverride()?.id)
            Assert.assertEquals("4:2", result.trackSelectionParameters.getSubtitleOverride()?.id)
        }
    }

    @Test
    fun `test EVAS Exo`() {
        runTest(
            TrackExamples.builderEVAASSS,
            PlayerBackend.EXO_PLAYER,
            audioIndex = 2,
            subtitleIndex = 4,
        ) { result ->
            Assert.assertTrue(result.bothSelected)
            Assert.assertEquals("0:2", result.trackSelectionParameters.getAudioOverride()?.id)
            Assert.assertEquals("0:4", result.trackSelectionParameters.getSubtitleOverride()?.id)
        }

        runTest(
            TrackExamples.builderEVAASSS,
            PlayerBackend.EXO_PLAYER,
            audioIndex = 2,
            subtitleIndex = 0, // external
        ) { result ->
            Assert.assertTrue(result.bothSelected)
            Assert.assertEquals("0:2", result.trackSelectionParameters.getAudioOverride()?.id)
            Assert.assertEquals("1:e:0", result.trackSelectionParameters.getSubtitleOverride()?.id)
        }
    }

    @Test
    fun `test EVAS MPV`() {
        runTest(
            TrackExamples.builderEVAASSS,
            PlayerBackend.MPV,
            audioIndex = 2,
            subtitleIndex = 4,
        ) { result ->
            Assert.assertTrue(result.bothSelected)
            Assert.assertEquals("1:1", result.trackSelectionParameters.getAudioOverride()?.id)
            Assert.assertEquals("3:1", result.trackSelectionParameters.getSubtitleOverride()?.id)
        }

        runTest(
            TrackExamples.builderEVAASSS,
            PlayerBackend.MPV,
            audioIndex = 2,
            subtitleIndex = 0, // external
        ) { result ->
            Assert.assertTrue(result.bothSelected)
            Assert.assertEquals("1:1", result.trackSelectionParameters.getAudioOverride()?.id)
            Assert.assertEquals("6:e:4", result.trackSelectionParameters.getSubtitleOverride()?.id)
        }
    }

    @Test
    fun `test builderVASASS`() {
        runTest(
            TrackExamples.builderVASASS,
            PlayerBackend.EXO_PLAYER,
            audioIndex = 1,
            subtitleIndex = 2,
        ) { result ->
            Assert.assertTrue(result.bothSelected)
            Assert.assertEquals("2", result.trackSelectionParameters.getAudioOverride()?.id)
            Assert.assertEquals("3", result.trackSelectionParameters.getSubtitleOverride()?.id)
        }

        runTest(
            TrackExamples.builderVASASS,
            PlayerBackend.MPV,
            audioIndex = 1,
            subtitleIndex = 2,
        ) { result ->
            Assert.assertTrue(result.bothSelected)
            Assert.assertEquals("1:1", result.trackSelectionParameters.getAudioOverride()?.id)
            Assert.assertEquals("2:1", result.trackSelectionParameters.getSubtitleOverride()?.id)
        }

        runTest(
            TrackExamples.builderVASASS,
            PlayerBackend.EXO_PLAYER,
            audioIndex = 3,
            subtitleIndex = 5,
        ) { result ->
            Assert.assertTrue(result.bothSelected)
            Assert.assertEquals("4", result.trackSelectionParameters.getAudioOverride()?.id)
            Assert.assertEquals("6", result.trackSelectionParameters.getSubtitleOverride()?.id)
        }

        runTest(
            TrackExamples.builderVASASS,
            PlayerBackend.MPV,
            audioIndex = 3,
            subtitleIndex = 5,
        ) { result ->
            Assert.assertTrue(result.bothSelected)
            Assert.assertEquals("3:2", result.trackSelectionParameters.getAudioOverride()?.id)
            Assert.assertEquals("5:3", result.trackSelectionParameters.getSubtitleOverride()?.id)
        }
    }

    @Test
    fun `Test external subtitles at end`() {
        val builder =
            TestTracks
                .Builder()
                .addVideo()
                .addAudio(2)
                .addSubtitle(2)
                .addExternalSubtitle(1)

        runTest(
            builder,
            PlayerBackend.EXO_PLAYER,
            audioIndex = 1,
            subtitleIndex = 5, // external
        ) { result ->
            Assert.assertTrue(result.bothSelected)
            Assert.assertEquals("0:2", result.trackSelectionParameters.getAudioOverride()?.id)
            Assert.assertEquals("1:e:5", result.trackSelectionParameters.getSubtitleOverride()?.id)
        }
    }

    @Test
    fun `test AAVASS in issue 1005`() {
        // https://github.com/damontecres/Wholphin/issues/1005#issuecomment-4085440175
        val builder =
            TestTracks
                .Builder()
                .addAudio(2)
                .addVideo()
                .addAudio()
                .addSubtitle(42)
        runTest(
            builder,
            PlayerBackend.MPV,
            audioIndex = 1,
            subtitleIndex = 5,
        ) { result ->
            Assert.assertTrue(result.bothSelected)
            Assert.assertEquals("1:2", result.trackSelectionParameters.getAudioOverride()?.id)
            Assert.assertEquals("5:2", result.trackSelectionParameters.getSubtitleOverride()?.id)
        }
    }

    @Test
    fun `test unsupported tracks`() {
        val builder =
            TestTracks
                .Builder()
                .addVideo()
                .addAudio()
                .addTrack(1, MediaStreamType.AUDIO, false, C.FORMAT_UNSUPPORTED_TYPE)

        val trackSelectionParameters = TrackSelectionParameters.Builder().build()
        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = builder.buildForExoPlayer().toTracks(),
                audioIndex = 1,
                subtitleIndex = null,
                source = builder.buildMediaSourceInfo(),
            ).let { result ->
                Assert.assertEquals(TrackSelected.SELECTED, result.audio)
                Assert.assertTrue(result.bothSelected)
            }

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = builder.buildForExoPlayer().toTracks(),
                audioIndex = 2,
                subtitleIndex = null,
                source = builder.buildMediaSourceInfo(),
            ).let { result ->
                Assert.assertEquals(TrackSelected.UNSUPPORTED, result.audio)
                Assert.assertFalse(result.bothSelected)
            }

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = builder.buildForExoPlayer().toTracks(),
                audioIndex = 4,
                subtitleIndex = null,
                source = builder.buildMediaSourceInfo(),
            ).let { result ->
                Assert.assertEquals(TrackSelected.NOT_FOUND, result.audio)
                Assert.assertFalse(result.bothSelected)
            }
    }

    @Test
    fun `test exceeds capabilities tracks`() {
        val builder =
            TestTracks
                .Builder()
                .addVideo()
                .addAudio()
                .addTrack(1, MediaStreamType.AUDIO, false, C.FORMAT_EXCEEDS_CAPABILITIES)

        val trackSelectionParameters = TrackSelectionParameters.Builder().build()
        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = builder.buildForExoPlayer().toTracks(),
                audioIndex = 1,
                subtitleIndex = null,
                source = builder.buildMediaSourceInfo(),
            ).let { result ->
                Assert.assertEquals(TrackSelected.SELECTED, result.audio)
                Assert.assertTrue(result.bothSelected)
            }

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = builder.buildForExoPlayer().toTracks(),
                audioIndex = 2,
                subtitleIndex = null,
                source = builder.buildMediaSourceInfo(),
            ).let { result ->
                Assert.assertEquals(TrackSelected.UNSUPPORTED, result.audio)
                Assert.assertFalse(result.bothSelected)
            }

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = builder.buildForExoPlayer().toTracks(),
                audioIndex = 4,
                subtitleIndex = null,
                source = builder.buildMediaSourceInfo(),
            ).let { result ->
                Assert.assertEquals(TrackSelected.NOT_FOUND, result.audio)
                Assert.assertFalse(result.bothSelected)
            }
    }
}
