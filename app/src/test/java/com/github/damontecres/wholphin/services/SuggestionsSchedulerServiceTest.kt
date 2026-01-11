package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import androidx.work.Operation
import androidx.work.WorkManager
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsSchedulerServiceTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockActivity: AppCompatActivity
    private lateinit var mockServerRepository: ServerRepository
    private lateinit var mockCache: SuggestionsCache
    private lateinit var mockWorkManager: WorkManager
    private lateinit var lifecycleOwner: LifecycleOwner
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var currentLiveData: MutableLiveData<CurrentUser?>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        lifecycleOwner =
            object : LifecycleOwner {
                override val lifecycle: Lifecycle
                    get() = lifecycleRegistry
            }
        lifecycleRegistry = LifecycleRegistry(lifecycleOwner)

        mockActivity = spyk(mockk<AppCompatActivity>(relaxed = true))
        mockServerRepository = mockk<ServerRepository>(relaxed = true)
        mockCache = mockk<SuggestionsCache>(relaxed = true)
        mockWorkManager = mockk<WorkManager>(relaxed = true)

        every { mockActivity.lifecycle } returns lifecycleRegistry

        currentLiveData = MutableLiveData()
        every { mockServerRepository.current } returns currentLiveData

        every { mockWorkManager.cancelUniqueWork(any()) } returns mockk<Operation>(relaxed = true)
        every { mockWorkManager.enqueue(any<androidx.work.WorkRequest>()) } returns mockk<Operation>(relaxed = true)
        every { mockWorkManager.enqueueUniquePeriodicWork(any(), any(), any()) } returns mockk<Operation>(relaxed = true)

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun mockUser(
        userId: UUID = UUID.randomUUID(),
        serverId: UUID = UUID.randomUUID(),
    ): CurrentUser {
        val user =
            JellyfinUser(
                id = userId,
                name = "TestUser",
                serverId = serverId,
                accessToken = "token",
            )
        val server =
            JellyfinServer(
                id = serverId,
                name = "TestServer",
                url = "http://localhost",
                version = null,
            )
        return CurrentUser(user = user, server = server)
    }

    private fun createService(): SuggestionsSchedulerService =
        SuggestionsSchedulerService(
            context = mockActivity,
            serverRepository = mockServerRepository,
            cache = mockCache,
            workManager = mockWorkManager,
        ).also { it.dispatcher = testDispatcher }

    @Test
    fun constructor_throwsIllegalStateException_whenContextIsNotAppCompatActivity() {
        val nonActivityContext = mockk<Context>(relaxed = true)

        val exception =
            assertThrows(IllegalStateException::class.java) {
                SuggestionsSchedulerService(
                    context = nonActivityContext,
                    serverRepository = mockServerRepository,
                    cache = mockCache,
                    workManager = mockWorkManager,
                )
            }

        assert(exception.message!!.contains("SuggestionsSchedulerService requires an AppCompatActivity context"))
    }

    @Test
    fun init_cancelsWork_whenUserChangesToNull() =
        runTest {
            createService()

            currentLiveData.value = null
            advanceUntilIdle()

            verify { mockWorkManager.cancelUniqueWork(SuggestionsWorker.WORK_NAME) }
        }

    @Test
    fun init_schedulesPeriodicWork_whenUserIsSet() =
        runTest {
            val userWithServer = mockUser()

            coEvery { mockCache.isEmpty() } returns false

            createService()

            currentLiveData.value = userWithServer
            advanceUntilIdle()

            verify { mockWorkManager.cancelUniqueWork(SuggestionsWorker.WORK_NAME) }
            verify {
                mockWorkManager.enqueueUniquePeriodicWork(
                    SuggestionsWorker.WORK_NAME,
                    any(),
                    any(),
                )
            }
        }

    @Test
    fun init_schedulesImmediateFetch_whenCacheIsEmpty() =
        runTest {
            val userWithServer = mockUser()

            coEvery { mockCache.isEmpty() } returns true

            createService()

            currentLiveData.value = userWithServer
            advanceUntilIdle()

            verify { mockWorkManager.enqueue(any<androidx.work.WorkRequest>()) }
            verify {
                mockWorkManager.enqueueUniquePeriodicWork(
                    SuggestionsWorker.WORK_NAME,
                    any(),
                    any(),
                )
            }
        }

    @Test
    fun init_doesNotScheduleImmediateFetch_whenCacheIsNotEmpty() =
        runTest {
            val userWithServer = mockUser()

            coEvery { mockCache.isEmpty() } returns false

            createService()

            currentLiveData.value = userWithServer
            advanceUntilIdle()

            verify(exactly = 0) { mockWorkManager.enqueue(any<androidx.work.WorkRequest>()) }
            verify {
                mockWorkManager.enqueueUniquePeriodicWork(
                    SuggestionsWorker.WORK_NAME,
                    any(),
                    any(),
                )
            }
        }

    @Test
    fun init_cancelsWorkBeforeScheduling_onUserChange() =
        runTest {
            val userWithServer = mockUser()

            coEvery { mockCache.isEmpty() } returns false

            createService()

            currentLiveData.value = null
            advanceUntilIdle()
            verify(atLeast = 1) { mockWorkManager.cancelUniqueWork(SuggestionsWorker.WORK_NAME) }

            currentLiveData.value = userWithServer
            advanceUntilIdle()

            verify(atLeast = 2) { mockWorkManager.cancelUniqueWork(SuggestionsWorker.WORK_NAME) }
        }
}
