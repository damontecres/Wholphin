package com.github.damontecres.wholphin.test

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.github.damontecres.wholphin.MainContent
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.services.ScreensaverState
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class InstrumentedBasicUiTests {
    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    lateinit var screensaverService: ScreensaverService

    @Before
    fun setup() {
        screensaverService = mockk(relaxed = true)
        every { screensaverService.state } returns MutableStateFlow(ScreensaverState(false, false, false, false))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun myTest() {
        // Start the app
        composeTestRule.setContent {
            WholphinTheme {
                MainContent(
                    backStack = mutableListOf(SetupDestination.ServerList),
                    navigationManager = mockk(relaxed = true),
                    appPreferences = mockk(relaxed = true),
                    backdropService = mockk(relaxed = true),
                    screensaverService = screensaverService,
                    requestedDestination = Destination.Home(),
                    modifier = Modifier,
                )
            }
        }

        composeTestRule.onNodeWithText("Add Server").assertExists()
        composeTestRule.onNodeWithTag("add_server").performKeyInput {
            pressKey(Key.DirectionDown) // TODO
        }
        composeTestRule.onNodeWithTag("add_server").performClickEnter()

        composeTestRule.onNodeWithText("Discovered Servers").assertExists()
    }
}

@OptIn(ExperimentalTestApi::class)
fun SemanticsNodeInteraction.performClickEnter() =
    performKeyInput {
        pressKey(Key.DirectionCenter)
    }
