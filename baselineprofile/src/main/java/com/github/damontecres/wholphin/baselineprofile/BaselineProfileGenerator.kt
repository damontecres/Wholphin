package com.github.damontecres.wholphin.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline & startup profiles for Wholphin
 *
 * It signs in to a Jellyfin server and walks through the main screens using only D-pad key events,
 * the same as a TV remote. By default, this uses the public Jellyfin demo server. To use a different
 * server, pass instrumentation arguments such as
 * `-Pandroid.testInstrumentationRunnerArguments.wholphin.server=http://host:8096`
 * (also `wholphin.username` & `wholphin.password`).
 *
 * See DEVELOPMENT.md for how to run it.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    private val args = InstrumentationRegistry.getArguments()

    private val targetAppId =
        args.getString("targetAppId")
            ?: throw IllegalStateException("targetAppId not passed as instrumentation argument")

    @Test
    fun generate() {
        rule.collect(
            packageName = targetAppId,
            includeInStartupProfile = false,
        ) {
            pressHome()
            startActivityAndWait()

            val driver = WholphinDriver(device)
            driver.signInIfNeeded()

            // Home page: scroll through the rows
            check(device.wait(Until.hasObject(driver.homeRow), WholphinDriver.UI_TIMEOUT)) {
                "Home page did not load"
            }
            repeat(3) {
                device.pressDPadDown()
                driver.pause()
            }
            repeat(4) {
                device.pressDPadRight()
                driver.pause()
            }
            repeat(3) {
                device.pressDPadUp()
                driver.pause()
            }

            // Navigation drawer: open, move through libraries, & open one
            device.pressDPadLeft()
            driver.pause()
            repeat(3) {
                device.pressDPadDown()
                driver.pause()
            }
            device.pressDPadCenter()
            device.waitForIdle()
            driver.pause(WholphinDriver.LOAD_PAUSE)

            // Library grid: scroll then open the first item's details
            repeat(3) {
                device.pressDPadDown()
                driver.pause()
            }
            device.pressDPadUp()
            driver.pause()
            device.pressDPadCenter()
            device.waitForIdle()
            driver.pause(WholphinDriver.LOAD_PAUSE)

            // Details page: scroll the content, then start playback & back out
            device.pressDPadDown()
            driver.pause()
            device.pressDPadUp()
            driver.pause()
            device.pressDPadCenter()
            device.waitForIdle()
            driver.pause(WholphinDriver.PLAYBACK_PAUSE)
            // Show & hide the playback controls
            device.pressDPadCenter()
            driver.pause()
            device.pressBack()
            driver.pause()
            device.pressBack()
            driver.pause()
            device.pressBack()
            driver.pause()
        }
    }
}
