package com.github.damontecres.wholphin.test

import androidx.media3.common.TrackSelectionParameters
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.ui.playback.TrackSelectionResult
import com.github.damontecres.wholphin.ui.playback.TrackSelectionUtils
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
        val trackSelectionParameters = TrackSelectionParameters.Builder().build()
        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = testTacks.toTracks(),
                playerBackend = playerBackend,
                supportsDirectPlay = true,
                audioIndex = audioIndex,
                subtitleIndex = subtitleIndex,
                source = testTacks.toMediaSourceInfo(),
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
}
