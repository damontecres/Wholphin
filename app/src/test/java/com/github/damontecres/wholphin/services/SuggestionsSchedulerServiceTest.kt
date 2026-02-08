package com.github.damontecres.wholphin.services

import androidx.appcompat.app.AppCompatActivity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsSchedulerServiceTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val testDispatcher = StandardTestDispatcher()
    private val currentLiveData = MutableLiveData<CurrentUser?>()
    private val mockActivity = mockk<AppCompatActivity>(relaxed = true)
    private val mockServerRepository = mockk<ServerRepository>(relaxed = true)
    private val mockCache = mockk<SuggestionsCache>(relaxed = true)
    private val mockWorkManager = mockk<WorkManager>(relaxed = true)
    private val lifecycleRegistry = LifecycleRegistry(mockk<LifecycleOwner>(relaxed = true))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockActivity.lifecycle } returns lifecycleRegistry
        every { mockServerRepository.current } returns currentLiveData
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createService() =
        SuggestionsSchedulerService(
            context = mockActivity,
            serverRepository = mockServerRepository,
            cache = mockCache,
            workManager = mockWorkManager,
        ).also { it.dispatcher = testDispatcher }

    @Test
    fun schedules_periodic_work_when_user_present() =
        runTest {
            coEvery { mockCache.isEmpty() } returns false
            createService()
            currentLiveData.value =
                CurrentUser(
                    user = JellyfinUser(id = UUID.randomUUID(), name = "User", serverId = UUID.randomUUID(), accessToken = "token"),
                    server = JellyfinServer(id = UUID.randomUUID(), name = "Server", url = "http://localhost", version = null),
                )
            advanceUntilIdle()
            verify { mockWorkManager.enqueueUniquePeriodicWork(SuggestionsWorker.WORK_NAME, any(), any()) }
        }

    @Test
    fun cancels_work_when_user_null() =
        runTest {
            coEvery { mockCache.isEmpty() } returns false
            createService()
            currentLiveData.value =
                CurrentUser(
                    user = JellyfinUser(id = UUID.randomUUID(), name = "User", serverId = UUID.randomUUID(), accessToken = "token"),
                    server = JellyfinServer(id = UUID.randomUUID(), name = "Server", url = "http://localhost", version = null),
                )
            advanceUntilIdle()
            currentLiveData.value = null
            advanceUntilIdle()
            verify { mockWorkManager.cancelUniqueWork(SuggestionsWorker.WORK_NAME) }
        }

    @Test
    fun schedules_periodic_work_with_delay_when_cache_empty() =
        runTest {
            coEvery { mockCache.isEmpty() } returns true
            val workRequestSlot = slot<PeriodicWorkRequest>()
            every {
                mockWorkManager.enqueueUniquePeriodicWork(
                    SuggestionsWorker.WORK_NAME,
                    any(),
                    capture(workRequestSlot),
                )
            } returns mockk()

            createService()
            currentLiveData.value =
                CurrentUser(
                    user = JellyfinUser(id = UUID.randomUUID(), name = "User", serverId = UUID.randomUUID(), accessToken = "token"),
                    server = JellyfinServer(id = UUID.randomUUID(), name = "Server", url = "http://localhost", version = null),
                )
            advanceUntilIdle()

            verify { mockWorkManager.enqueueUniquePeriodicWork(SuggestionsWorker.WORK_NAME, any(), any()) }
            assertEquals(30000L, workRequestSlot.captured.workSpec.initialDelay)
        }

    @Test
    fun schedules_periodic_work_without_delay_when_cache_not_empty() =
        runTest {
            coEvery { mockCache.isEmpty() } returns false
            val workRequestSlot = slot<PeriodicWorkRequest>()
            every {
                mockWorkManager.enqueueUniquePeriodicWork(
                    SuggestionsWorker.WORK_NAME,
                    any(),
                    capture(workRequestSlot),
                )
            } returns mockk()

            createService()
            currentLiveData.value =
                CurrentUser(
                    user = JellyfinUser(id = UUID.randomUUID(), name = "User", serverId = UUID.randomUUID(), accessToken = "token"),
                    server = JellyfinServer(id = UUID.randomUUID(), name = "Server", url = "http://localhost", version = null),
                )
            advanceUntilIdle()

            verify { mockWorkManager.enqueueUniquePeriodicWork(SuggestionsWorker.WORK_NAME, any(), any()) }
            assertEquals(0L, workRequestSlot.captured.workSpec.initialDelay)
        }
}
