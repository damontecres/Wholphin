package com.github.damontecres.wholphin.services

import androidx.appcompat.app.AppCompatActivity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.common.util.concurrent.ListenableFuture
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
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

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsSchedulerServiceTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val testDispatcher = StandardTestDispatcher()
    private val currentLiveData = MutableLiveData<CurrentUser?>()
    private val mockActivity = mockk<AppCompatActivity>(relaxed = true)
    private val mockServerRepository = mockk<ServerRepository>(relaxed = true)
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
            workManager = mockWorkManager,
        ).also {
            it.dispatcher = testDispatcher
            it.initialDelaySecondsProvider = { 60L }
        }

    private fun mockWorkInfos(infos: List<androidx.work.WorkInfo>) {
        @Suppress("UNCHECKED_CAST")
        val future = mockk<ListenableFuture<List<androidx.work.WorkInfo>>>()
        every { future.get() } returns infos
        every { mockWorkManager.getWorkInfosForUniqueWork(SuggestionsWorker.WORK_NAME) } returns future
    }

    @Test
    fun schedules_periodic_work_when_user_present() =
        runTest {
            mockWorkInfos(emptyList())
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
            mockWorkInfos(emptyList())
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
            mockWorkInfos(emptyList())
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
            assertEquals(60000L, workRequestSlot.captured.workSpec.initialDelay)
        }

    @Test
    fun schedules_periodic_work_with_delay_when_cache_not_empty() =
        runTest {
            mockWorkInfos(emptyList())
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
            assertEquals(60000L, workRequestSlot.captured.workSpec.initialDelay)
        }

    @Test
    fun does_not_schedule_if_already_scheduled_for_same_user() =
        runTest {
            val userId = UUID.randomUUID()
            val workInfo = mockk<androidx.work.WorkInfo>()
            every { workInfo.state } returns androidx.work.WorkInfo.State.ENQUEUED
            every { workInfo.tags } returns setOf("user:$userId")
            mockWorkInfos(listOf(workInfo))

            createService()
            currentLiveData.value =
                CurrentUser(
                    user = JellyfinUser(id = userId, name = "User", serverId = UUID.randomUUID(), accessToken = "token"),
                    server = JellyfinServer(id = UUID.randomUUID(), name = "Server", url = "http://localhost", version = null),
                )
            advanceUntilIdle()

            verify(exactly = 0) { mockWorkManager.enqueueUniquePeriodicWork(any(), any(), any()) }
        }
}
