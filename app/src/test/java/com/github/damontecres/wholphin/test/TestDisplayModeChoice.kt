package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.services.DisplayMode
import com.github.damontecres.wholphin.services.RefreshRateService
import org.junit.Assert
import org.junit.Test

class TestDisplayModeChoice {
    companion object {
        val HD_60 = DisplayMode(0, 1920, 1080, 60f)
        val HD_30 = DisplayMode(1, 1920, 1080, 30f)
        val HD_24 = DisplayMode(2, 1920, 1080, 24f)

        val UHD_60 = DisplayMode(3, 3840, 2160, 60f)
        val UHD_30 = DisplayMode(4, 3840, 2160, 30f)
        val UHD_24 = DisplayMode(5, 3840, 2160, 24f)

        val ALL_MODES = listOf(UHD_24, UHD_30, UHD_60, HD_24, HD_30, HD_60)
    }

    @Test
    fun test1() {
        val streamWidth = 1920
        val streamHeight = 1080
        val streamRealFrameRate = 60f
        val result =
            RefreshRateService.findDisplayMode(
                displayModes = ALL_MODES,
                streamWidth = streamWidth,
                streamHeight = streamHeight,
                streamRealFrameRate = streamRealFrameRate,
                refreshRateSwitch = true,
                resolutionSwitch = false,
            )
        Assert.assertEquals(3, result?.modeId)
    }

    @Test
    fun test2() {
        val streamWidth = 1920
        val streamHeight = 1080
        val streamRealFrameRate = 60f
        val result =
            RefreshRateService.findDisplayMode(
                displayModes = ALL_MODES,
                streamWidth = streamWidth,
                streamHeight = streamHeight,
                streamRealFrameRate = streamRealFrameRate,
                refreshRateSwitch = true,
                resolutionSwitch = true,
            )
        Assert.assertEquals(0, result?.modeId)
    }

    @Test
    fun test3() {
        val streamWidth = 1920
        val streamHeight = 1080
        val streamRealFrameRate = 30f
        val result =
            RefreshRateService.findDisplayMode(
                displayModes = ALL_MODES,
                streamWidth = streamWidth,
                streamHeight = streamHeight,
                streamRealFrameRate = streamRealFrameRate,
                refreshRateSwitch = true,
                resolutionSwitch = false,
            )
        Assert.assertEquals(4, result?.modeId)
    }

    @Test
    fun testFraction() {
        val streamWidth = 1920
        val streamHeight = 1080
        val streamRealFrameRate = 30f

        val displayModes =
            listOf(
                DisplayMode(0, 1920, 1080, 59.940f),
                DisplayMode(1, 1920, 1080, 60f),
//                DisplayMode(2, 1920, 1080, 29.970f),
            )

        val result =
            RefreshRateService.findDisplayMode(
                displayModes = displayModes,
                streamWidth = streamWidth,
                streamHeight = streamHeight,
                streamRealFrameRate = 29.970f,
                refreshRateSwitch = true,
                resolutionSwitch = false,
            )
        Assert.assertEquals(0, result?.modeId)

        val result2 =
            RefreshRateService.findDisplayMode(
                displayModes = displayModes,
                streamWidth = streamWidth,
                streamHeight = streamHeight,
                streamRealFrameRate = 24f,
                refreshRateSwitch = true,
                resolutionSwitch = false,
            )
        Assert.assertEquals(1, result2?.modeId)
    }
}
