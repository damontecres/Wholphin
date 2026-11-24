package com.github.damontecres.wholphin.baselineprofile

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * Drives Wholphin's UI (sign in & navigation) for the profile generator and benchmarks
 */
class WholphinDriver(
    private val device: UiDevice,
) {
    private val args = InstrumentationRegistry.getArguments()
    private val serverUrl = args.getString("wholphin.server") ?: DEMO_SERVER
    private val username = args.getString("wholphin.username") ?: DEMO_USERNAME
    private val password = args.getString("wholphin.password") ?: DEMO_PASSWORD

    // The home page has no title, so look for any of the rows shown on a typical home page
    val homeRow =
        By.text(
            java.util.regex.Pattern
                .compile("Continue watching|Next up|Recently added.*"),
        )

    /**
     * Sign in via the UI if the app is not already signed in (eg on the first iteration)
     */
    fun signInIfNeeded() {
        // Wait for either the home page or one of the sign in screens
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT
        while (SystemClock.elapsedRealtime() < deadline &&
            !device.hasObject(homeRow) &&
            listOf("Add Server", "Add User").none { device.hasObject(By.text(it)) }
        ) {
            SystemClock.sleep(250)
        }
        if (device.hasObject(homeRow)) return

        // The "add" card is focused by default on both screens, so use the D-pad instead of touch
        if (device.hasObject(By.text("Add Server"))) {
            pause(LOAD_PAUSE)
            // Focus needs a nudge for the card to take the key press
            device.pressDPadDown()
            device.pressDPadCenter()
            device.wait(Until.hasObject(By.text("Discovered Servers")), UI_TIMEOUT)
            // Touch doesn't activate the button, so navigate to it with the D-pad
            val keys =
                listOf(
                    { device.pressDPadCenter() },
                    { device.pressDPadDown() },
                    { device.pressDPadCenter() },
                )
            for (key in keys) {
                if (device.hasObject(By.text("Enter Server IP or URL"))) break
                key()
                pause(LOAD_PAUSE)
            }
            device.wait(Until.hasObject(By.text("Enter Server IP or URL")), UI_TIMEOUT)
            enterText(serverUrl)
            device.pressEnter()
        }
        device.wait(Until.hasObject(By.text("Add User")), UI_TIMEOUT)
        if (device.hasObject(By.text("Add User"))) {
            device.pressDPadCenter()
            device.wait(Until.hasObject(By.text("Use username/password")), UI_TIMEOUT)
            device.pressDPadCenter()
            device.wait(Until.hasObject(By.clazz(EDIT_TEXT)), UI_TIMEOUT)
            val fields = device.findObjects(By.clazz(EDIT_TEXT))
            fields[0].text = username
            if (password.isNotEmpty() && fields.size > 1) fields[1].text = password
            // Focus the password field & submit with the keyboard's "Go" action
            fields[1].click()
            pause()
            device.pressEnter()
        }
        if (!device.wait(Until.hasObject(homeRow), SIGN_IN_TIMEOUT)) {
            val out = java.io.ByteArrayOutputStream()
            device.dumpWindowHierarchy(out)
            throw IllegalStateException("Failed to sign in to $serverUrl: $out")
        }
    }

    private fun enterText(value: String) {
        device.wait(Until.hasObject(By.clazz(EDIT_TEXT)), UI_TIMEOUT)
        val field =
            device.findObject(By.clazz(EDIT_TEXT)) ?: run {
                val out = java.io.ByteArrayOutputStream()
                device.dumpWindowHierarchy(out)
                throw IllegalStateException("No text field found: $out")
            }
        field.text = value
    }

    fun pause(millis: Long = KEY_PAUSE) = SystemClock.sleep(millis)

    companion object {
        const val DEMO_SERVER = "https://demo.jellyfin.org/stable"
        const val DEMO_USERNAME = "demo"
        const val DEMO_PASSWORD = ""

        const val EDIT_TEXT = "android.widget.EditText"

        const val UI_TIMEOUT = 10_000L
        const val SIGN_IN_TIMEOUT = 30_000L
        const val KEY_PAUSE = 750L
        const val LOAD_PAUSE = 2_500L
        const val PLAYBACK_PAUSE = 5_000L
    }
}
