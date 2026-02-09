package com.github.damontecres.wholphin.services

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionServiceTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private val mockApi = mockk<ApiClient>(relaxed = true)
    private val mockServerRepository = mockk<ServerRepository>()
    private val mockCache = mockk<SuggestionsCache>()
    private val mockWorkManager = mockk<WorkManager>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkObject(GetItemsRequestHandler)
    }

    private fun createService() =
        SuggestionService(
            api = mockApi,
            serverRepository = mockServerRepository,
            cache = mockCache,
            workManager = mockWorkManager,
        )

    private fun mockQueryResult(items: List<BaseItemDto>): Response<BaseItemDtoQueryResult> =
        mockk {
            every { content } returns
                mockk {
                    every { this@mockk.items } returns items
                }
        }

    private fun mockUser(id: UUID = UUID.randomUUID()): JellyfinUser =
        JellyfinUser(
            id = id,
            name = "TestUser",
            serverId = UUID.randomUUID(),
            accessToken = "token",
        )

    private fun mockWorkInfo(state: WorkInfo.State): WorkInfo = mockk<WorkInfo> { every { this@mockk.state } returns state }

    @Test
    fun getSuggestionsFlow_returnsEmpty_whenNoUserLoggedIn() =
        runTest {
            val currentUser = MutableLiveData<JellyfinUser?>(null)
            every { mockServerRepository.currentUser } returns currentUser
            every { mockWorkManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())

            val service = createService()
            val result = service.getSuggestionsFlow(UUID.randomUUID(), BaseItemKind.MOVIE).first()

            assertEquals(SuggestionsResource.Empty, result)
        }

    @Test
    fun maps_active_work_states_to_Loading() =
        runTest {
            listOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED).forEach { state ->
                val userId = UUID.randomUUID()
                val parentId = UUID.randomUUID()
                val currentUser = MutableLiveData<JellyfinUser?>(mockUser(userId))

                every { mockServerRepository.currentUser } returns currentUser
                coEvery { mockCache.get(userId, parentId, BaseItemKind.MOVIE) } returns null
                every { mockWorkManager.getWorkInfosForUniqueWorkFlow(any()) } returns
                    flowOf(listOf(mockWorkInfo(state)))

                val service = createService()
                val result = service.getSuggestionsFlow(parentId, BaseItemKind.MOVIE).first()

                assertEquals(SuggestionsResource.Loading, result)
            }
        }

    @Test
    fun maps_finished_work_states_to_Empty() =
        runTest {
            listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED).forEach { state ->
                val userId = UUID.randomUUID()
                val parentId = UUID.randomUUID()
                val currentUser = MutableLiveData<JellyfinUser?>(mockUser(userId))

                every { mockServerRepository.currentUser } returns currentUser
                coEvery { mockCache.get(userId, parentId, BaseItemKind.MOVIE) } returns null
                every { mockWorkManager.getWorkInfosForUniqueWorkFlow(any()) } returns
                    flowOf(listOf(mockWorkInfo(state)))

                val service = createService()
                val result = service.getSuggestionsFlow(parentId, BaseItemKind.MOVIE).first()

                assertEquals(SuggestionsResource.Empty, result)
            }
        }

    @Test
    fun getSuggestionsFlow_returnsEmpty_whenCacheEmptyAndNoWorkInfo() =
        runTest {
            val userId = UUID.randomUUID()
            val parentId = UUID.randomUUID()
            val currentUser = MutableLiveData<JellyfinUser?>(mockUser(userId))

            every { mockServerRepository.currentUser } returns currentUser
            coEvery { mockCache.get(userId, parentId, BaseItemKind.MOVIE) } returns null
            every { mockWorkManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())

            val service = createService()
            val result = service.getSuggestionsFlow(parentId, BaseItemKind.MOVIE).first()

            assertEquals(SuggestionsResource.Empty, result)
        }

    @Test
    fun passes_correct_arguments_to_cache() =
        runTest {
            val userId = UUID.randomUUID()
            val libraryId = UUID.randomUUID()
            val otherLibraryId = UUID.randomUUID()
            val currentUser = MutableLiveData<JellyfinUser?>(mockUser(userId))

            every { mockServerRepository.currentUser } returns currentUser
            every { mockWorkManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())

            coEvery { mockCache.get(userId, libraryId, BaseItemKind.MOVIE) } returns null
            coEvery {
                mockCache.get(
                    userId,
                    otherLibraryId,
                    BaseItemKind.MOVIE,
                )
            } returns CachedSuggestions(listOf(UUID.randomUUID()))

            val service = createService()
            assertEquals(SuggestionsResource.Empty, service.getSuggestionsFlow(libraryId, BaseItemKind.MOVIE).first())

            coEvery { mockCache.get(userId, libraryId, BaseItemKind.MOVIE) } returns null
            coEvery {
                mockCache.get(
                    userId,
                    libraryId,
                    BaseItemKind.SERIES,
                )
            } returns CachedSuggestions(listOf(UUID.randomUUID()))

            assertEquals(SuggestionsResource.Empty, service.getSuggestionsFlow(libraryId, BaseItemKind.MOVIE).first())
        }

    @Test
    fun getSuggestionsFlow_returnsSuccess_whenCacheHasItems() =
        runTest {
            val userId = UUID.randomUUID()
            val parentId = UUID.randomUUID()
            val currentUser = MutableLiveData<JellyfinUser?>(mockUser(userId))

            every { mockServerRepository.currentUser } returns currentUser

            val cachedId = UUID.randomUUID()
            coEvery {
                mockCache.get(
                    userId,
                    parentId,
                    BaseItemKind.MOVIE,
                )
            } returns CachedSuggestions(listOf(cachedId))

            val dto =
                mockk<BaseItemDto>(relaxed = true) {
                    every { id } returns cachedId
                    every { type } returns BaseItemKind.MOVIE
                }
            io.mockk.mockkObject(GetItemsRequestHandler)
            coEvery { GetItemsRequestHandler.execute(mockApi, any()) } returns mockQueryResult(listOf(dto))

            every { mockWorkManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())

            val service = createService()
            val result = service.getSuggestionsFlow(parentId, BaseItemKind.MOVIE).first()

            assertTrue(result is SuggestionsResource.Success)
            val items = (result as SuggestionsResource.Success).items
            assertEquals(1, items.size)
            assertEquals(cachedId, items[0].id)
        }

    @Test
    fun getSuggestionsFlow_emitsEmpty_whenApiFails() =
        runTest {
            val userId = UUID.randomUUID()
            val parentId = UUID.randomUUID()
            val currentUser = MutableLiveData<JellyfinUser?>(mockUser(userId))

            every { mockServerRepository.currentUser } returns currentUser

            val cachedId = UUID.randomUUID()
            coEvery {
                mockCache.get(
                    userId,
                    parentId,
                    BaseItemKind.MOVIE,
                )
            } returns CachedSuggestions(listOf(cachedId))

            io.mockk.mockkObject(GetItemsRequestHandler)
            coEvery { GetItemsRequestHandler.execute(mockApi, any()) } throws RuntimeException("Network error")

            every { mockWorkManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())

            val service = createService()
            val result = service.getSuggestionsFlow(parentId, BaseItemKind.MOVIE).first()

            assertEquals(SuggestionsResource.Empty, result)
        }
}
