package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.preferences.InterfacePreferences
import com.github.damontecres.wholphin.preferences.SubtitlePreferences
import com.github.damontecres.wholphin.preferences.resetSubtitles
import com.github.damontecres.wholphin.ui.preferences.subtitle.shouldEnableSeparateHdrToggle
import org.junit.Assert
import org.junit.Test

class TestSubtitleHdrMigration {
    private fun subtitles(block: SubtitlePreferences.Builder.() -> Unit): SubtitlePreferences =
        SubtitlePreferences
            .newBuilder()
            .apply {
                resetSubtitles()
                block.invoke(this)
            }.build()

    private fun shouldSeparate(
        sdr: SubtitlePreferences,
        hdr: SubtitlePreferences,
    ): Boolean =
        InterfacePreferences
            .newBuilder()
            .apply {
                subtitlesPreferences = sdr
                hdrSubtitlesPreferences = hdr
            }.build()
            .shouldEnableSeparateHdrToggle()

    @Test
    fun `Test separate enabled`() {
        val sdr = subtitles { fontSize = 60 }
        val differentHdr = subtitles { fontSize = 30 }
        val shouldSeparate = shouldSeparate(sdr, differentHdr)
        Assert.assertTrue(shouldSeparate)
    }

    @Test
    fun `Test separate disabled`() {
        val sdr = subtitles { fontSize = 60 }
        val differentHdr = subtitles { fontSize = 60 }
        val shouldSeparate = shouldSeparate(sdr, differentHdr)
        Assert.assertFalse(shouldSeparate)
    }

    @Test
    fun `Test HDR unchanged`() {
        val sdr = subtitles { fontSize = 60 }
        val differentHdr = subtitles { }
        val shouldSeparate = shouldSeparate(sdr, differentHdr)
        Assert.assertFalse(shouldSeparate)
    }
}
