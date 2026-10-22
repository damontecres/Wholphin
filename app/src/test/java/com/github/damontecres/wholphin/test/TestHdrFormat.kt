package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.HdrOverrideMode
import com.github.damontecres.wholphin.preferences.PlaybackOverrides
import com.github.damontecres.wholphin.preferences.getHdrRangeTypesFor
import com.github.damontecres.wholphin.preferences.updatePlaybackOverrides
import com.github.damontecres.wholphin.util.profile.WholphinVideoRangeType
import org.junit.Assert
import org.junit.Test

class TestHdrFormat {
    private fun appPreferences(block: PlaybackOverrides.Builder.() -> Unit): AppPreferences =
        AppPreferences.getDefaultInstance().updatePlaybackOverrides(block)

    @Test
    fun `Test all auto returns empty sets`() {
        val prefs = appPreferences { }
        Assert.assertEquals(emptySet<String>(), prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_ENABLE, jellyfinTenEleven = true))
        Assert.assertEquals(emptySet<String>(), prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_DISABLE, jellyfinTenEleven = true))
    }

    @Test
    fun `Test HDR10 override`() {
        val prefs = appPreferences { hdr10Override = HdrOverrideMode.HDR_OVERRIDE_ENABLE }
        Assert.assertEquals(
            setOf(WholphinVideoRangeType.HDR10.serialName),
            prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_ENABLE, jellyfinTenEleven = true),
        )
        Assert.assertEquals(emptySet<String>(), prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_DISABLE, jellyfinTenEleven = true))
    }

    @Test
    fun `Test Dolby Vision Profile 5 override`() {
        val prefs = appPreferences { doviProfile5Override = HdrOverrideMode.HDR_OVERRIDE_DISABLE }
        Assert.assertEquals(
            setOf(WholphinVideoRangeType.DOVI.serialName),
            prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_DISABLE, jellyfinTenEleven = true),
        )
    }

    @Test
    fun `Test Dolby Vision Profile 7 override requires Jellyfin 10_11`() {
        val prefs = appPreferences { doviProfile7Override = HdrOverrideMode.HDR_OVERRIDE_ENABLE }
        Assert.assertEquals(
            emptySet<String>(),
            prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_ENABLE, jellyfinTenEleven = false),
        )
        Assert.assertEquals(
            setOf(WholphinVideoRangeType.DOVI_WITH_EL.serialName, WholphinVideoRangeType.DOVI_WITH_EL_HDR10_PLUS.serialName),
            prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_ENABLE, jellyfinTenEleven = true),
        )
    }

    @Test
    fun `Test Dolby Vision Profile 8 override`() {
        val prefs = appPreferences { doviProfile8Override = HdrOverrideMode.HDR_OVERRIDE_DISABLE }
        Assert.assertEquals(
            setOf(WholphinVideoRangeType.DOVI_WITH_HDR10.serialName),
            prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_DISABLE, jellyfinTenEleven = false),
        )
        Assert.assertEquals(
            setOf(WholphinVideoRangeType.DOVI_WITH_HDR10.serialName, WholphinVideoRangeType.DOVI_WITH_HDR10_PLUS.serialName),
            prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_DISABLE, jellyfinTenEleven = true),
        )
    }

    @Test
    fun `Test multiple formats set to the same mode are unioned`() {
        val prefs =
            appPreferences {
                hdr10Override = HdrOverrideMode.HDR_OVERRIDE_ENABLE
                hdr10PlusOverride = HdrOverrideMode.HDR_OVERRIDE_ENABLE
                doviProfile5Override = HdrOverrideMode.HDR_OVERRIDE_DISABLE
            }
        Assert.assertEquals(
            setOf(WholphinVideoRangeType.HDR10.serialName, WholphinVideoRangeType.HDR10_PLUS.serialName),
            prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_ENABLE, jellyfinTenEleven = true),
        )
        Assert.assertEquals(
            setOf(WholphinVideoRangeType.DOVI.serialName),
            prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_DISABLE, jellyfinTenEleven = true),
        )
    }
}
