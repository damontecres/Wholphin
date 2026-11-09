package com.github.damontecres.wholphin.test

import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.ServerUserConfig
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.HomeDataService
import com.github.damontecres.wholphin.services.HomePageResolvedSettings
import com.github.damontecres.wholphin.services.HomePageSettingsSource
import com.github.damontecres.wholphin.services.HomeRowConfigDisplay
import com.github.damontecres.wholphin.services.HomeSettingsService
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.main.HomeViewModel
import com.github.damontecres.wholphin.ui.util.EmptyStringProvider
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class TestHomeRowJobs {
    private val dispatcher = StandardTestDispatcher()
    private val userId = UUID.randomUUID()
    private val user = ServerUserConfig(userId)
    private val repository = mockk<ServerRepository>()
    private val settingsService = mockk<HomeSettingsService>()
    private val data = mockk<HomeDataService>()
    private val nav = mockk<NavDrawerService>()
    private val preferences = mockk<UserPreferencesService>()
    private val rowConfig = HomeRowConfig.ContinueWatching()
    private val settings =
        HomePageResolvedSettings(
            userId,
            HomePageSettingsSource.LOCAL,
            listOf(
                HomeRowConfigDisplay(0, EmptyStringProvider, rowConfig),
            ),
        )
    private val dummyRowState = HomeRowLoadingState.Success(EmptyStringProvider, emptyList())
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        WholphinDispatchers.configure(dispatcher)
        every { repository.currentUserDtoFlow } returns MutableStateFlow(null)
        every { settingsService.currentSettings } returns MutableStateFlow(HomePageResolvedSettings.EMPTY)
        coEvery { preferences.getCurrent() } returns UserPreferences(AppPreferences.getDefaultInstance(), null)
        coEvery { nav.getAllUserLibraries(any(), any()) } returns emptyList()
        viewModel =
            HomeViewModel(
                context = mockk(),
                navigationManager = mockk(),
                serverRepository = repository,
                serverReportService = mockk(),
                navDrawerService = nav,
                homeSettingsService = settingsService,
                homeDataService = data,
                favoriteWatchManager = mockk(),
                datePlayedService = mockk(relaxed = true),
                backdropService = mockk(),
                userPreferencesService = preferences,
                mediaManagementService = mockk(),
                latestNextUpService = mockk(),
            )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        WholphinDispatchers.reset()
    }

    @Test
    fun cancellingLoadCancelsInFlightRowRequests() =
        runTest(dispatcher) {
            runCurrent()
            var completed = 0
            coEvery { data.fetchDataForRow(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
                delay(5_000)
                completed++
                dummyRowState
            }
            val loadJob = launch { viewModel.loadHomeRows(user, settings) }
            runCurrent()
            loadJob.cancel()
            advanceUntilIdle()
            assertEquals(0, completed)
        }

    @Test
    fun cancellationExceptionIsNotMappedToRowError() =
        runTest(dispatcher) {
            runCurrent()
            coEvery { data.fetchDataForRow(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
                throw CancellationException("Simulated row cancellation")
            }
            val loadJob = launch { viewModel.loadHomeRows(user, settings) }
            advanceUntilIdle()
            assertTrue(loadJob.isCancelled)
            val rows = viewModel.state.value.homeRows
            val hasError = rows.any { it is HomeRowLoadingState.Error }
            assertFalse(hasError)
        }

    @Test
    fun rowScopeRemainsViewModelScopeAfterLoadCompletes() =
        runTest(dispatcher) {
            runCurrent()
            val scopeSlot = slot<CoroutineScope>()
            coEvery {
                data.fetchDataForRow(
                    any(),
                    capture(scopeSlot),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns dummyRowState

            viewModel.loadHomeRows(user, settings)
            advanceUntilIdle()

            assertTrue(scopeSlot.isCaptured)
            val captured = scopeSlot.captured
            assertSame(viewModel.viewModelScope, captured)
            assertTrue(captured.isActive)
        }
}
