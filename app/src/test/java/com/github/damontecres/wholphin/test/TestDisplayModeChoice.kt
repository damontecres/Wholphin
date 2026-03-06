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

        val HD720_60 = DisplayMode(6, 1280, 720, 60f)
        val HD720_30 = DisplayMode(7, 1280, 720, 30f)
        val HD720_24 = DisplayMode(8, 1280, 720, 24f)

        val ALL_MODES =
            listOf(
                UHD_24,
                UHD_30,
                UHD_60,
                HD_24,
                HD_30,
                HD_60,
                HD720_60,
                HD720_30,
                HD720_24,
            ).sortedWith(
                compareByDescending<DisplayMode>({ it.physicalWidth * it.physicalHeight })
                    .thenBy { it.refreshRateRounded },
            )
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
                targetFrameRate = streamRealFrameRate,
                refreshRateSwitch = true,
                resolutionSwitch = false,
            )
        Assert.assertEquals(UHD_60.modeId, result?.modeId)
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
                targetFrameRate = streamRealFrameRate,
                refreshRateSwitch = true,
                resolutionSwitch = true,
            )
        Assert.assertEquals(HD_60.modeId, result?.modeId)
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
                targetFrameRate = streamRealFrameRate,
                refreshRateSwitch = true,
                resolutionSwitch = false,
            )
        Assert.assertEquals(UHD_30.modeId, result?.modeId)
    }

    @Test
    fun test4() {
        val streamWidth = 1920
        val streamHeight = 804
        val streamRealFrameRate = 30f
        val result =
            RefreshRateService.findDisplayMode(
                displayModes = ALL_MODES,
                streamWidth = streamWidth,
                streamHeight = streamHeight,
                targetFrameRate = streamRealFrameRate,
                refreshRateSwitch = false,
                resolutionSwitch = true,
            )
        Assert.assertEquals(HD_30.modeId, result?.modeId)
    }

    @Test
    fun testFraction() {
        val streamWidth = 1920
        val streamHeight = 1080
        val streamRealFrameRate = 29.970f

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
                targetFrameRate = streamRealFrameRate,
                refreshRateSwitch = true,
                resolutionSwitch = false,
            )
        Assert.assertEquals(0, result?.modeId)

        val result2 =
            RefreshRateService.findDisplayMode(
                displayModes = displayModes,
                streamWidth = streamWidth,
                streamHeight = streamHeight,
                targetFrameRate = 24f,
                refreshRateSwitch = true,
                resolutionSwitch = false,
            )
        Assert.assertEquals(HD_30.modeId, result2?.modeId)
    }

    private fun test(
        expected: DisplayMode,
        streamWidth: Int,
        streamHeight: Int,
        streamRealFrameRate: Float,
    ) {
        val result =
            RefreshRateService.findDisplayMode(
                displayModes = ALL_MODES,
                streamWidth = streamWidth,
                streamHeight = streamHeight,
                targetFrameRate = streamRealFrameRate,
                refreshRateSwitch = false,
                resolutionSwitch = true,
            )
        Assert.assertEquals(
            "streamWidth=$streamWidth, streamHeight=$streamHeight, streamRealFrameRate=$streamRealFrameRate",
            expected.modeId,
            result?.modeId,
        )
    }

    @Test
    fun `Test choose best resolution`() {
        test(HD720_30, 1280, 720, 30f)
        test(HD720_30, 1280, 548, 30f)
        test(HD720_30, 640, 480, 30f)
        test(HD720_30, 960, 720, 30f)
        test(HD720_24, 960, 720, 24f)
    }

    @Test
    fun `Test 60fps for 24 without resolution switch`() {
        val streamWidth = 1920
        val streamHeight = 1080
        val streamRealFrameRate = 24f

        val displayModes =
            listOf(
                UHD_60,
                HD_60,
                HD_30,
            )

        val result =
            RefreshRateService.findDisplayMode(
                displayModes = displayModes,
                streamWidth = streamWidth,
                streamHeight = streamHeight,
                targetFrameRate = streamRealFrameRate,
                refreshRateSwitch = true,
                resolutionSwitch = false,
            )
        Assert.assertEquals(UHD_60.modeId, result?.modeId)
    }

    @Test
    fun `Test 60fps for 24 with resolution switch`() {
        val streamWidth = 1920
        val streamHeight = 1080
        val streamRealFrameRate = 24f

        val displayModes =
            listOf(
                HD_60,
                HD_30,
            )

        val result =
            RefreshRateService.findDisplayMode(
                displayModes = displayModes,
                streamWidth = streamWidth,
                streamHeight = streamHeight,
                targetFrameRate = streamRealFrameRate,
                refreshRateSwitch = true,
                resolutionSwitch = true,
            )
        Assert.assertEquals(HD_60.modeId, result?.modeId)
    }

    @Test
    fun `Test prefer refresh rate over resolution`() {
        val streamWidth = 1280
        val streamHeight = 720
        val streamRealFrameRate = 24f

        // 720@60 is an acceptable refresh rate, but want to prioritize exact refresh rate
        val displayModes =
            listOf(
                HD_24,
                HD720_60,
            )

        val result =
            RefreshRateService.findDisplayMode(
                displayModes = displayModes,
                streamWidth = streamWidth,
                streamHeight = streamHeight,
                targetFrameRate = streamRealFrameRate,
                refreshRateSwitch = true,
                resolutionSwitch = true,
            )
        Assert.assertEquals(HD_24.modeId, result?.modeId)
    }

    @Test
    fun `Test prefer refresh rate over resolution2`() {
        val streamWidth = 1280
        val streamHeight = 720
        val streamRealFrameRate = 30f

        // 720@60 is an acceptable refresh rate, but want to prioritize exact refresh rate
        val displayModes =
            listOf(
                HD_30,
                HD720_60,
            )

        val result =
            RefreshRateService.findDisplayMode(
                displayModes = displayModes,
                streamWidth = streamWidth,
                streamHeight = streamHeight,
                targetFrameRate = streamRealFrameRate,
                refreshRateSwitch = true,
                resolutionSwitch = true,
            )
        Assert.assertEquals(HD_30.modeId, result?.modeId)
    }
}
