package com.github.damontecres.wholphin.services

import androidx.datastore.core.DataStore
import androidx.lifecycle.MutableLiveData
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.api.operations.UserViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

class SuggestionsWorkerTest {
    private val testUserId = UUID.randomUUID()
    private val testServerId = UUID.randomUUID()
    private val mockWorkerParams = mockk<WorkerParameters>(relaxed = true)
    private val mockServerRepository = mockk<ServerRepository>(relaxed = true)
    private val mockPreferences = mockk<DataStore<AppPreferences>>(relaxed = true)
    private val mockApi = mockk<ApiClient>(relaxed = true)
    private val mockCache = mockk<SuggestionsCache>(relaxed = true)
    private val mockUserViewsApi = mockk<UserViewsApi>(relaxed = true)

    @Before
    fun setUp() {
        every { mockApi.userViewsApi } returns mockUserViewsApi
        every { mockApi.baseUrl } returns "http://localhost"
        every { mockApi.accessToken } returns "test-token"
    }

    @After
    fun tearDown() = unmockkObject(GetItemsRequestHandler)

    private fun createWorker(
        userId: UUID? = testUserId,
        serverId: UUID? = testServerId,
    ): SuggestionsWorker {
        val inputData =
            Data
                .Builder()
                .apply {
                    userId?.let { putString(SuggestionsWorker.PARAM_USER_ID, it.toString()) }
                    serverId?.let { putString(SuggestionsWorker.PARAM_SERVER_ID, it.toString()) }
                }.build()
        every { mockWorkerParams.inputData } returns inputData
        return SuggestionsWorker(
            context = mockk(relaxed = true),
            workerParams = mockWorkerParams,
            serverRepository = mockServerRepository,
            preferences = mockPreferences,
            api = mockApi,
            cache = mockCache,
        )
    }

    private fun mockPrefs() =
        mockk<AppPreferences>(relaxed = true) {
            every { homePagePreferences } returns mockk { every { maxItemsPerRow } returns 25 }
        }

    private fun mockQueryResult(items: List<BaseItemDto> = emptyList()) =
        mockk<org.jellyfin.sdk.api.client.Response<BaseItemDtoQueryResult>>(relaxed = true) {
            every { content } returns mockk { every { this@mockk.items } returns items }
        }

    @Test
    fun returns_failure_on_invalid_input() =
        runTest {
            listOf(
                createWorker(userId = null),
                createWorker(serverId = null),
            ).forEach { worker ->
                assertEquals(ListenableWorker.Result.failure(), worker.doWork())
            }
        }

    @Test
    fun restores_session_when_api_not_configured() =
        runTest {
            every { mockApi.baseUrl } returns null
            every { mockApi.accessToken } returns null
            val mockUser = mockk<CurrentUser>()
            var restored = false
            every { mockServerRepository.current } answers { MutableLiveData(if (restored) mockUser else null) }
            coEvery { mockServerRepository.restoreSession(testServerId, testUserId) } coAnswers {
                restored = true
                mockUser
            }
            every { mockPreferences.data } returns flowOf(mockPrefs())
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } returns mockQueryResult()

            val result = createWorker().doWork()

            coVerify { mockServerRepository.restoreSession(testServerId, testUserId) }
            assertEquals(ListenableWorker.Result.success(), result)
        }

    @Test
    fun caches_suggestions_for_supported_types() =
        runTest {
            listOf(
                CollectionType.MOVIES to BaseItemKind.MOVIE,
                CollectionType.TVSHOWS to BaseItemKind.SERIES,
            ).forEach { (collectionType, itemKind) ->
                val viewId = UUID.randomUUID()
                val view =
                    mockk<BaseItemDto>(relaxed = true) {
                        every { id } returns viewId
                        every { this@mockk.collectionType } returns collectionType
                    }
                every { mockPreferences.data } returns flowOf(mockPrefs())
                coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } returns mockQueryResult(listOf(view))
                mockkObject(GetItemsRequestHandler)
                coEvery { GetItemsRequestHandler.execute(mockApi, any()) } returns mockQueryResult(listOf(mockk(relaxed = true)))

                val result = createWorker().doWork()

                assertEquals(ListenableWorker.Result.success(), result)
                coVerify { mockCache.put(testUserId, viewId, itemKind, any()) }
                coVerify { mockCache.save() }
            }
        }

    @Test
    fun fetches_contextual_suggestions_when_genres_available() =
        runTest {
            val viewId = UUID.randomUUID()
            val view =
                mockk<BaseItemDto>(relaxed = true) {
                    every { id } returns viewId
                    every { this@mockk.collectionType } returns CollectionType.MOVIES
                }
            every { mockPreferences.data } returns flowOf(mockPrefs())
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } returns mockQueryResult(listOf(view))
            mockkObject(GetItemsRequestHandler)

            val genreId = UUID.randomUUID()
            val historyItem =
                mockk<BaseItemDto>(relaxed = true) {
                    every { id } returns UUID.randomUUID()
                    every { genreItems } returns listOf(mockk { every { id } returns genreId })
                }
            val contextualItem = mockk<BaseItemDto>(relaxed = true) { every { id } returns UUID.randomUUID() }
            val randomItem = mockk<BaseItemDto>(relaxed = true) { every { id } returns UUID.randomUUID() }
            val freshItem = mockk<BaseItemDto>(relaxed = true) { every { id } returns UUID.randomUUID() }

            var callCount = 0
            coEvery { GetItemsRequestHandler.execute(mockApi, any()) } answers {
                callCount++
                when (callCount) {
                    1 -> mockQueryResult(listOf(historyItem))
                    2 -> mockQueryResult(listOf(contextualItem))
                    3 -> mockQueryResult(listOf(randomItem))
                    4 -> mockQueryResult(listOf(freshItem))
                    else -> mockQueryResult(emptyList())
                }
            }

            val result = createWorker().doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify { mockCache.put(testUserId, viewId, BaseItemKind.MOVIE, any()) }
        }

    @Test
    fun skips_contextual_suggestions_when_no_genres_available() =
        runTest {
            val viewId = UUID.randomUUID()
            val view =
                mockk<BaseItemDto>(relaxed = true) {
                    every { id } returns viewId
                    every { this@mockk.collectionType } returns CollectionType.MOVIES
                }
            every { mockPreferences.data } returns flowOf(mockPrefs())
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } returns mockQueryResult(listOf(view))
            mockkObject(GetItemsRequestHandler)

            val historyItem =
                mockk<BaseItemDto>(relaxed = true) {
                    every { id } returns UUID.randomUUID()
                    every { genreItems } returns emptyList()
                }
            val randomItem = mockk<BaseItemDto>(relaxed = true) { every { id } returns UUID.randomUUID() }
            val freshItem = mockk<BaseItemDto>(relaxed = true) { every { id } returns UUID.randomUUID() }

            var callCount = 0
            coEvery { GetItemsRequestHandler.execute(mockApi, any()) } answers {
                callCount++
                when (callCount) {
                    1 -> mockQueryResult(listOf(historyItem))
                    2 -> mockQueryResult(listOf(randomItem))
                    3 -> mockQueryResult(listOf(freshItem))
                    else -> mockQueryResult(emptyList())
                }
            }

            val result = createWorker().doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify { mockCache.put(testUserId, viewId, BaseItemKind.MOVIE, any()) }
        }

    @Test
    fun returns_retry_on_network_error() =
        runTest {
            every { mockPreferences.data } returns flowOf(mockPrefs())
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } throws ApiClientException("Network error")

            val result = createWorker().doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
        }
}
