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

    @Test
    fun `23 976 fps switches to 4K 24 Hz when the display only offers integer rates`() {
        val modes =
            listOf(
                UHD_24.copy(refreshRate = 24.000002f),
                DisplayMode(9, 3840, 2160, 25f),
                UHD_30.copy(refreshRate = 30.000002f),
            )

        for (resolutionSwitch in listOf(false, true)) {
            for (frameRate in listOf(24000f / 1001, 24f)) {
                Assert.assertEquals(UHD_24.modeId, chooseMode(modes, frameRate, resolutionSwitch)?.modeId)
            }
        }
    }

    @Test
    fun `fractional rates can fall back to integer refresh rates`() {
        for (rate in listOf(24f, 25f, 30f, 60f, 120f)) {
            val integerMode = UHD_24.copy(refreshRate = rate)
            val fractionalMode = UHD_30.copy(refreshRate = rate * 1000 / 1001)

            for (resolutionSwitch in listOf(false, true)) {
                Assert.assertEquals(integerMode, chooseMode(listOf(integerMode), fractionalMode.refreshRate, resolutionSwitch))
                val modes = listOf(fractionalMode, integerMode)
                Assert.assertEquals(fractionalMode, chooseMode(modes, fractionalMode.refreshRate, resolutionSwitch))
                Assert.assertEquals(integerMode, chooseMode(modes, rate, resolutionSwitch))
            }
        }
    }

    @Test
    fun `prefer the closest rate within the chosen resolution`() {
        val fractional24 = UHD_24.copy(refreshRate = 23.976f)
        val closer24 = UHD_30.copy(refreshRate = 23.99f)
        val modes = listOf(fractional24, closer24, HD_24)

        for ((resolutionSwitch, expected) in listOf(false to closer24, true to HD_24)) {
            val result =
                RefreshRateService.findDisplayMode(
                    displayModes = modes,
                    streamWidth = 1920,
                    streamHeight = 1080,
                    targetFrameRate = 24f,
                    refreshRateSwitch = true,
                    resolutionSwitch = resolutionSwitch,
                )
            Assert.assertEquals(expected, result)
        }
    }

    @Test
    fun `prefer a near 1x match over multiples and an exact multiple over its fallback`() {
        val fractional60 = UHD_60.copy(modeId = 9, refreshRate = 59.94f)
        val modes = listOf(UHD_24, fractional60, UHD_60)

        for (resolutionSwitch in listOf(false, true)) {
            Assert.assertEquals(UHD_24, chooseMode(modes, 23.976f, resolutionSwitch))
            for (frameRate in listOf(23.976f, 29.97f)) {
                Assert.assertEquals(UHD_60, chooseMode(listOf(UHD_60), frameRate, resolutionSwitch))
                Assert.assertEquals(fractional60, chooseMode(modes.drop(1), frameRate, resolutionSwitch))
            }
        }
    }

    @Test
    fun `tolerance accepts 120 Hz doubles on either side of the target`() {
        val integer120 = UHD_60.copy(refreshRate = 120f)
        val fractional120 = UHD_60.copy(refreshRate = 119.88f)
        Assert.assertEquals(integer120, chooseMode(listOf(integer120), 59.94f, resolutionSwitch = true))
        Assert.assertEquals(fractional120, chooseMode(listOf(fractional120), 60f, resolutionSwitch = true))
    }

    @Test
    fun `reject unrelated rates and nonpositive or nonfinite stream rates`() {
        val modes = listOf(UHD_24.copy(refreshRate = 25f), UHD_30)
        Assert.assertNull(chooseMode(modes, 24f, resolutionSwitch = false))
        Assert.assertNull(chooseMode(modes, 24.95f, resolutionSwitch = false))
        for (frameRate in listOf(0f, -24f, Float.NaN, Float.POSITIVE_INFINITY)) {
            Assert.assertNull(chooseMode(modes, frameRate, resolutionSwitch = true))
        }
    }

    @Test
    fun `keep exact refresh rate matching when only switching resolution`() {
        val near25 = HD_24.copy(refreshRate = 24.975f)
        val exact25 = UHD_24.copy(refreshRate = 25f)
        val result =
            RefreshRateService.findDisplayMode(
                displayModes = listOf(exact25, near25),
                streamWidth = 1920,
                streamHeight = 1080,
                targetFrameRate = 25f,
                refreshRateSwitch = false,
                resolutionSwitch = true,
            )
        Assert.assertEquals(exact25, result)
    }

    private fun chooseMode(
        modes: List<DisplayMode>,
        frameRate: Float,
        resolutionSwitch: Boolean,
    ): DisplayMode? =
        RefreshRateService.findDisplayMode(
            displayModes = modes,
            streamWidth = 3840,
            streamHeight = 2160,
            targetFrameRate = frameRate,
            refreshRateSwitch = true,
            resolutionSwitch = resolutionSwitch,
        )

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
