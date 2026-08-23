package com.github.damontecres.wholphin.preferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalPreferenceTest {
    @Test
    fun fireTvHybridDolbyVisionWorkaround_requiresMasterToggleSettingAndKnownDefect() {
        val disabledMaster =
            ExperimentalPreferences
                .newBuilder()
                .setFireTvHybridDolbyVisionWorkaround(true)
                .build()
        val disabledSetting =
            ExperimentalPreferences
                .newBuilder()
                .setEnabled(true)
                .build()
        val enabled =
            ExperimentalPreferences
                .newBuilder()
                .setEnabled(true)
                .setFireTvHybridDolbyVisionWorkaround(true)
                .build()

        assertFalse(disabledMaster.isFireTvHybridDolbyVisionWorkaroundActive(hasKnownDefect = true))
        assertFalse(disabledSetting.isFireTvHybridDolbyVisionWorkaroundActive(hasKnownDefect = true))
        assertFalse(enabled.isFireTvHybridDolbyVisionWorkaroundActive(hasKnownDefect = false))
        assertTrue(enabled.isFireTvHybridDolbyVisionWorkaroundActive(hasKnownDefect = true))
    }

    @Test
    fun experimentalPreferences_legacyFieldFourKeepsAudioOffloadAndDefaultsWorkaroundOff() {
        // Proto wire key 0x20 is field 4 with wire type 0; legacy data has no field 5.
        val preferences = ExperimentalPreferences.parseFrom(byteArrayOf(0x20, 0x01))

        assertTrue(preferences.disableAudioOffload)
        assertFalse(preferences.fireTvHybridDolbyVisionWorkaround)
    }
}
