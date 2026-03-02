package com.github.damontecres.wholphin.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.github.damontecres.wholphin.MainContent
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.services.ScreensaverState
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.test.TestActivity
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34])
@RunWith(RobolectricTestRunner::class)
class BasicUiTests {
    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    lateinit var screensaverService: ScreensaverService

    @Before
    fun setup() {
        screensaverService = mockk(relaxed = true)
        every { screensaverService.state } returns
            MutableStateFlow(
                ScreensaverState(
                    false,
                    false,
                    false,
                    false,
                ),
            )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Test enter server address`() {
        composeTestRule.setContent {
            WholphinTheme {
                MainContent(
                    backStack = mutableListOf(SetupDestination.ServerList),
                    navigationManager = mockk(relaxed = true),
                    appPreferences = mockk(relaxed = true),
                    backdropService = mockk(relaxed = true),
                    screensaverService = screensaverService,
                    requestedDestination = Destination.Home(),
                    modifier = Modifier.Companion,
                )
            }
        }

        composeTestRule.onNodeWithText("Add Server").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_server").performKeyInput {
            pressKey(Key.DirectionDown) // TODO fix focus
        }
        composeTestRule.onNodeWithTag("add_server").performClickEnter()

        composeTestRule.onNodeWithText("Discovered Servers").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter server address").performClickEnter()
        composeTestRule.onNodeWithText("Enter Server IP or URL").assertIsDisplayed()
    }
}
