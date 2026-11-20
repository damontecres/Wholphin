package com.github.damontecres.wholphin.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures the effect of the baseline profile by comparing no precompilation with the profile
 * installed. Compare `*NoProfile` with `*WithProfile` in the output. Signs in the same way as
 * [BaselineProfileGenerator], see DEVELOPMENT.md for how to run.
 */
@RunWith(AndroidJUnit4::class)
class WholphinBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun navigationNoProfile() = navigation(CompilationMode.None())

    @Test
    fun navigationWithProfile() = navigation(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) =
        rule.measureRepeated(
            packageName = targetAppId(),
            metrics = listOf(StartupTimingMetric()),
            compilationMode = mode,
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            val driver = WholphinDriver(device)
            driver.signInIfNeeded()
            device.wait(Until.hasObject(driver.homeRow), WholphinDriver.UI_TIMEOUT)
        }

    private fun navigation(mode: CompilationMode) =
        rule.measureRepeated(
            packageName = targetAppId(),
            metrics = listOf(FrameTimingMetric()),
            compilationMode = mode,
            // Setup starts the app, so don't restart it before measuring
            startupMode = null,
            iterations = ITERATIONS,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                val driver = WholphinDriver(device)
                driver.signInIfNeeded()
                device.wait(Until.hasObject(driver.homeRow), WholphinDriver.UI_TIMEOUT)
            },
        ) {
            val driver = WholphinDriver(device)
            // Scroll the home page, then open & close the navigation drawer a few times
            repeat(3) {
                device.pressDPadDown()
                driver.pause(NAV_PAUSE)
            }
            repeat(3) {
                device.pressDPadLeft()
                driver.pause(NAV_PAUSE)
                device.pressDPadRight()
                driver.pause(NAV_PAUSE)
            }
            repeat(3) {
                device.pressDPadUp()
                driver.pause(NAV_PAUSE)
            }
        }

    private fun targetAppId() =
        InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: throw IllegalStateException("targetAppId not passed as instrumentation argument")

    companion object {
        private const val ITERATIONS = 10
        private const val NAV_PAUSE = 500L
    }
}
