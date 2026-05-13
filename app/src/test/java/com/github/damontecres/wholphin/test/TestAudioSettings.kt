package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.util.mpv.mpvDeviceProfile
import com.github.damontecres.wholphin.util.profile.supportedAudioCodecs
import org.junit.Assert
import org.junit.Test

class TestAudioSettings {
    @Test
    fun `spdif enabling sets ac3 and disables downmix`() {
        val prefs = AppPreferences.getDefaultInstance()

        val result = AppPreference.SpdifArcSurroundAudio.setter.invoke(prefs, true)

        val spdifValue = AppPreference.SpdifArcSurroundAudio.getter.invoke(result)
        val ac3Value = AppPreference.Ac3Supported.getter.invoke(result)
        val downmixValue = AppPreference.DownMixStereo.getter.invoke(result)

        Assert.assertTrue("Spdif should be ON", spdifValue)
        Assert.assertTrue("AC3 should be auto-enabled", ac3Value)
        Assert.assertFalse("Downmix should be auto-disabled", downmixValue)
    }

    @Test
    fun `spdif disabling only affects spdif`() {
        val basePrefs =
            AppPreference.SpdifArcSurroundAudio.setter.invoke(
                AppPreferences.getDefaultInstance(),
                true,
            )

        val result = AppPreference.SpdifArcSurroundAudio.setter.invoke(basePrefs, false)

        val spdifValue = AppPreference.SpdifArcSurroundAudio.getter.invoke(result)
        val ac3Value = AppPreference.Ac3Supported.getter.invoke(result)
        val downmixValue = AppPreference.DownMixStereo.getter.invoke(result)

        Assert.assertFalse("Spdif should be OFF", spdifValue)
        Assert.assertTrue("AC3 should remain enabled", ac3Value)
        Assert.assertFalse("Downmix should remain disabled", downmixValue)
    }

    @Test
    fun `ac3 disabling auto-disables spdif`() {
        val basePrefs =
            AppPreference.SpdifArcSurroundAudio.setter.invoke(
                AppPreferences.getDefaultInstance(),
                true,
            )

        val result = AppPreference.Ac3Supported.setter.invoke(basePrefs, false)

        val spdifValue = AppPreference.SpdifArcSurroundAudio.getter.invoke(result)
        val ac3Value = AppPreference.Ac3Supported.getter.invoke(result)

        Assert.assertFalse("Spdif should auto-disable when AC3 is turned off", spdifValue)
        Assert.assertFalse("AC3 should be OFF", ac3Value)
    }

    @Test
    fun `downmix enabling auto-disables spdif`() {
        val basePrefs =
            AppPreference.SpdifArcSurroundAudio.setter.invoke(
                AppPreferences.getDefaultInstance(),
                true,
            )

        val result = AppPreference.DownMixStereo.setter.invoke(basePrefs, true)

        val spdifValue = AppPreference.SpdifArcSurroundAudio.getter.invoke(result)
        val downmixValue = AppPreference.DownMixStereo.getter.invoke(result)

        Assert.assertFalse("Spdif should auto-disable when downmix is turned on", spdifValue)
        Assert.assertTrue("Downmix should be ON", downmixValue)
    }

    @Test
    fun `enabling ac3 does not alter spdif or downmix`() {
        val basePrefs =
            AppPreference.Ac3Supported.setter.invoke(
                AppPreferences.getDefaultInstance(),
                false,
            )

        val result = AppPreference.Ac3Supported.setter.invoke(basePrefs, true)

        val spdifValue = AppPreference.SpdifArcSurroundAudio.getter.invoke(result)
        val ac3Value = AppPreference.Ac3Supported.getter.invoke(result)
        val downmixValue = AppPreference.DownMixStereo.getter.invoke(result)

        Assert.assertFalse("Spdif should remain OFF", spdifValue)
        Assert.assertTrue("AC3 should be ON", ac3Value)
        Assert.assertFalse("Downmix should remain OFF", downmixValue)
    }

    @Test
    fun `disabling downmix does not alter spdif or ac3`() {
        val basePrefs =
            AppPreference.DownMixStereo.setter.invoke(
                AppPreference.SpdifArcSurroundAudio.setter.invoke(
                    AppPreferences.getDefaultInstance(),
                    true,
                ),
                true,
            )

        val spdifValueInitial = AppPreference.SpdifArcSurroundAudio.getter.invoke(basePrefs)
        Assert.assertFalse("Spdif should turn OFF", spdifValueInitial)

        val result = AppPreference.DownMixStereo.setter.invoke(basePrefs, false)

        val spdifValue = AppPreference.SpdifArcSurroundAudio.getter.invoke(result)
        val ac3Value = AppPreference.Ac3Supported.getter.invoke(result)
        val downmixValue = AppPreference.DownMixStereo.getter.invoke(result)

        Assert.assertFalse("Spdif should remain OFF", spdifValue)
        Assert.assertTrue("AC3 should remain ON", ac3Value)
        Assert.assertFalse("Downmix should be OFF", downmixValue)
    }
}
