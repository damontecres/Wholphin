package com.github.damontecres.wholphin.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.services.ScreensaverState
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.SetupNavigationManager
import com.github.damontecres.wholphin.test.TestActivity
import com.github.damontecres.wholphin.ui.setup.SwitchServerContent
import com.github.damontecres.wholphin.ui.setup.SwitchServerViewModel
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.operations.QuickConnectApi
import org.jellyfin.sdk.discovery.DiscoveryService
import org.jellyfin.sdk.discovery.RecommendedServerInfo
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34])
@RunWith(RobolectricTestRunner::class)
class BasicUiTests {
    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    @Inject
    lateinit var jellyfin: Jellyfin

    @Inject
    lateinit var api: ApiClient

    @Inject
    lateinit var setupNavigationManager: SetupNavigationManager

    lateinit var screensaverService: ScreensaverService

    lateinit var switchServerViewModel: SwitchServerViewModel

    val discovery: DiscoveryService = mockk()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        Dispatchers.setMain(TestModule.testDispatcher)
        mockkStatic(Dispatchers::class)

        every { Dispatchers.IO } returns TestModule.testDispatcher
        every { Dispatchers.Default } returns TestModule.testDispatcher

        hiltRule.inject()
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

        every { jellyfin.createApi(any(), any(), any(), any(), any()) } returns api
        every { jellyfin.discovery } returns discovery
        coEvery { discovery.getRecommendedServers("localhost") } returns
            listOf(
                RecommendedServerInfo(
                    address = "localhost",
                    responseTime = 50,
                    score = RecommendedServerInfoScore.GREAT,
                    issues = emptyList(),
                    systemInfo =
                        Result.success(
                            PublicSystemInfo(
                                id = UUID.randomUUID().toString(),
                                startupWizardCompleted = true,
                            ),
                        ),
                ),
            )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Test enter server address`() {
        val quickConnectApi = mockk<QuickConnectApi>()
        every { api.quickConnectApi } returns quickConnectApi
        coEvery { quickConnectApi.getQuickConnectEnabled() } returns successResponse(true)

        composeTestRule.setContent {
            WholphinTheme {
                switchServerViewModel = hiltViewModel()
                SwitchServerContent(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = switchServerViewModel,
                )
            }
        }

        TestModule.testDispatcher.scheduler.advanceUntilIdle()

        composeTestRule.onNodeWithText("Add Server").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_server").performKeyInput {
            pressKey(Key.DirectionDown) // TODO fix focus
        }
        composeTestRule.onNodeWithTag("add_server").performClickEnter()

        composeTestRule.onNodeWithText("Discovered Servers").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter server address").performClickEnter()
        composeTestRule.onNodeWithText("Enter Server IP or URL").assertIsDisplayed()

        composeTestRule.onNodeWithTag("server_url_text").performTextInput("localhost")
        composeTestRule.onNodeWithText("Submit").requestFocus().performClickEnter()

        TestModule.testDispatcher.scheduler.advanceUntilIdle()

        switchServerViewModel.addServerState.value.let {
            if (it is LoadingState.Error) throw it.exception ?: Exception(it.message)
        }

//        coVerify(exactly = 1) { discovery.getRecommendedServers("localhost") }
        Assert.assertEquals(1, setupNavigationManager.backStack.size)
        Assert.assertTrue(setupNavigationManager.backStack.last() is SetupDestination.UserList)
    }
}
