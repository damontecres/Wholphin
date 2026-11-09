package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.preferences.updateScreensaverPreferences
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class ScreensaverServiceTest {
    private fun createService(
        scope: CoroutineScope,
        preferencesFlow: MutableStateFlow<UserPreferences>,
    ): ScreensaverService {
        val userPreferencesService = mockk<UserPreferencesService>()
        every { userPreferencesService.flow } returns preferencesFlow
        coEvery { userPreferencesService.getCurrent() } answers { preferencesFlow.value }
        return ScreensaverService(
            mockk<Context>(),
            scope,
            mockk<ApiClient>(),
            userPreferencesService,
            mockk<ImageUrlService>(),
        )
    }

    private fun preferences() =
        AppPreferences.getDefaultInstance().updateScreensaverPreferences {
            enabled = true
            startDelay = 1000L
        }

    @After
    fun tearDown() {
        WholphinDispatchers.reset()
    }

    @Test
    fun preferencesEmissionDoesNotResumeScreensaverDuringPlayback() =
        runTest {
            WholphinDispatchers.configure(StandardTestDispatcher(testScheduler))
            val preferences = preferences()
            val preferencesFlow = MutableStateFlow(UserPreferences(preferences, null))
            val service = createService(backgroundScope, preferencesFlow)
            runCurrent()

            service.keepScreenOn(true)
            runCurrent()
            assertTrue(service.state.value.paused)

            preferencesFlow.value =
                UserPreferences(preferences.updateScreensaverPreferences { dimEnabled = true }, null)
            runCurrent()
            assertTrue(service.state.value.paused)

            service.pulse()
            advanceTimeBy(1000L.milliseconds)
            runCurrent()
            assertFalse(service.state.value.show)
            assertFalse(service.state.value.showDim)
        }

    @Test
    fun stoppingPlaybackResumesScreensaverTimer() =
        runTest {
            WholphinDispatchers.configure(StandardTestDispatcher(testScheduler))
            val preferencesFlow = MutableStateFlow(UserPreferences(preferences(), null))
            val service = createService(backgroundScope, preferencesFlow)
            runCurrent()

            service.keepScreenOn(true)
            runCurrent()
            service.keepScreenOn(false)
            runCurrent()
            assertFalse(service.state.value.paused)

            advanceTimeBy(999L.milliseconds)
            runCurrent()
            assertFalse(service.state.value.show)
            advanceTimeBy(1L.milliseconds)
            runCurrent()
            assertTrue(service.state.value.show)
        }

    @Test
    fun preferencesEmissionPreservesActiveAndTemporaryState() =
        runTest {
            WholphinDispatchers.configure(StandardTestDispatcher(testScheduler))
            val preferences = preferences()
            val preferencesFlow = MutableStateFlow(UserPreferences(preferences, null))
            val service = createService(backgroundScope, preferencesFlow)
            runCurrent()

            service.start()
            preferencesFlow.value =
                UserPreferences(preferences.updateScreensaverPreferences { dimEnabled = true }, null)
            runCurrent()

            assertTrue(service.state.value.enabledTemp)
            assertTrue(service.state.value.active)
            assertTrue(service.state.value.dimActive)
            assertTrue(service.state.value.dimEnabled)
            assertTrue(service.state.value.show)
        }

    @Test
    fun keepingScreenOnPreventsPendingScreensaverActivation() =
        runTest {
            WholphinDispatchers.configure(StandardTestDispatcher(testScheduler))
            val preferencesFlow = MutableStateFlow(UserPreferences(preferences(), null))
            val service = createService(backgroundScope, preferencesFlow)
            runCurrent()

            service.pulse()
            runCurrent()
            advanceTimeBy(500L.milliseconds)
            service.keepScreenOn(true)
            runCurrent()
            advanceTimeBy(500L.milliseconds)
            runCurrent()

            assertFalse(service.state.value.active)
            assertTrue(service.state.value.paused)
        }
}
