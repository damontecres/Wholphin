package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.lifecycle.MutableLiveData
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.HomePagePreferences
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.api.operations.UserLibraryApi
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
    private lateinit var mockContext: Context
    private lateinit var mockWorkerParams: WorkerParameters
    private lateinit var mockServerRepository: ServerRepository
    private lateinit var mockPreferences: DataStore<AppPreferences>
    private lateinit var mockApi: ApiClient
    private lateinit var mockCache: SuggestionsCache
    private lateinit var mockUserViewsApi: UserViewsApi
    private lateinit var mockItemsApi: ItemsApi

    private val testUserId = UUID.randomUUID()
    private val testServerId = UUID.randomUUID()

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockWorkerParams = mockk(relaxed = true)
        mockServerRepository = mockk(relaxed = true)
        mockPreferences = mockk(relaxed = true)
        mockApi = mockk(relaxed = true)
        mockCache = mockk(relaxed = true)
        mockUserViewsApi = mockk(relaxed = true)
        mockItemsApi = mockk(relaxed = true)

        every { mockApi.userViewsApi } returns mockUserViewsApi
        every { mockApi.itemsApi } returns mockItemsApi
        // We'll mock GetItemsRequestHandler.execute per-test when needed
        every { mockApi.baseUrl } returns "http://localhost"
        every { mockApi.accessToken } returns "test-token"

        every { mockCache.put(any(), any(), any(), any()) } just Runs
        coEvery { mockCache.save() } just Runs
    }

    @After
    fun tearDown() {
        io.mockk.unmockkObject(GetItemsRequestHandler)
    }

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
            context = mockContext,
            workerParams = mockWorkerParams,
            serverRepository = mockServerRepository,
            preferences = mockPreferences,
            api = mockApi,
            cache = mockCache,
        )
    }

    private fun mockAppPreferences(maxItemsPerRow: Int = 25): AppPreferences =
        mockk {
            every { homePagePreferences } returns
                mockk {
                    every { this@mockk.maxItemsPerRow } returns maxItemsPerRow
                }
        }

    private fun mockView(
        id: UUID = UUID.randomUUID(),
        collectionType: CollectionType?,
    ): BaseItemDto =
        mockk {
            every { this@mockk.id } returns id
            every { this@mockk.collectionType } returns collectionType
        }

    private fun mockItem(
        id: UUID = UUID.randomUUID(),
        seriesId: UUID? = null,
    ): BaseItemDto =
        mockk {
            every { this@mockk.id } returns id
            every { this@mockk.seriesId } returns seriesId
        }

    private fun mockQueryResult(items: List<BaseItemDto>): Response<BaseItemDtoQueryResult> =
        mockk {
            every { content } returns
                mockk {
                    every { this@mockk.items } returns items
                }
        }

    // region Input Validation Tests

    @Test
    fun doWork_returnsFailure_whenUserIdMissing() =
        runTest {
            val worker = createWorker(userId = null)
            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun doWork_returnsFailure_whenServerIdMissing() =
        runTest {
            val worker = createWorker(serverId = null)
            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun doWork_returnsFailure_whenUserIdInvalid() =
        runTest {
            val inputData =
                Data
                    .Builder()
                    .putString(SuggestionsWorker.PARAM_USER_ID, "not-a-uuid")
                    .putString(SuggestionsWorker.PARAM_SERVER_ID, testServerId.toString())
                    .build()

            every { mockWorkerParams.inputData } returns inputData

            val worker =
                SuggestionsWorker(
                    context = mockContext,
                    workerParams = mockWorkerParams,
                    serverRepository = mockServerRepository,
                    preferences = mockPreferences,
                    api = mockApi,
                    cache = mockCache,
                )

            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun doWork_returnsFailure_whenServerIdInvalid() =
        runTest {
            val inputData =
                Data
                    .Builder()
                    .putString(SuggestionsWorker.PARAM_USER_ID, testUserId.toString())
                    .putString(SuggestionsWorker.PARAM_SERVER_ID, "not-a-uuid")
                    .build()

            every { mockWorkerParams.inputData } returns inputData

            val worker =
                SuggestionsWorker(
                    context = mockContext,
                    workerParams = mockWorkerParams,
                    serverRepository = mockServerRepository,
                    preferences = mockPreferences,
                    api = mockApi,
                    cache = mockCache,
                )

            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.failure(), result)
        }

    // endregion

    // region Session Restoration Tests

    @Test
    fun doWork_restoresSession_whenApiNotConfigured() =
        runTest {
            every { mockApi.baseUrl } returns null
            every { mockApi.accessToken } returns null

            val mockUser = mockk<CurrentUser>()
            var sessionRestored = false
            every { mockServerRepository.current } answers {
                if (sessionRestored) MutableLiveData(mockUser) else MutableLiveData(null)
            }
            coEvery { mockServerRepository.restoreSession(testServerId, testUserId) } coAnswers {
                sessionRestored = true
                mockUser
            }

            every { mockPreferences.data } returns flowOf(mockAppPreferences())
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } returns mockQueryResult(emptyList())

            val worker = createWorker()
            val result = worker.doWork()

            coVerify { mockServerRepository.restoreSession(testServerId, testUserId) }
            assertEquals(ListenableWorker.Result.success(), result)
        }

    @Test
    fun doWork_returnsFailure_whenSessionRestorationFails() =
        runTest {
            every { mockApi.baseUrl } returns null
            every { mockApi.accessToken } returns null
            every { mockServerRepository.current } returns MutableLiveData(null)
            coEvery { mockServerRepository.restoreSession(any(), any()) } returns null

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun doWork_skipsSessionRestoration_whenApiAlreadyConfigured() =
        runTest {
            every { mockApi.baseUrl } returns "http://localhost"
            every { mockApi.accessToken } returns "token"

            every { mockPreferences.data } returns flowOf(mockAppPreferences())
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } returns mockQueryResult(emptyList())

            val worker = createWorker()
            worker.doWork()

            coVerify(exactly = 0) { mockServerRepository.restoreSession(any(), any()) }
        }

    // endregion

    // region Success Scenarios

    @Test
    fun doWork_returnsSuccess_whenNoViews() =
        runTest {
            every { mockPreferences.data } returns flowOf(mockAppPreferences())
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } returns mockQueryResult(emptyList())

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
        }

    @Test
    fun doWork_cachesMovieSuggestions_forMovieLibrary() =
        runTest {
            val movieViewId = UUID.randomUUID()
            val movieView = mockView(movieViewId, CollectionType.MOVIES)
            val movieItem = mockItem()

            every { mockPreferences.data } returns flowOf(mockAppPreferences(maxItemsPerRow = 10))
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } returns mockQueryResult(listOf(movieView))
            io.mockk.mockkObject(GetItemsRequestHandler)
            coEvery { GetItemsRequestHandler.execute(mockApi, any()) } returns mockQueryResult(listOf(movieItem))

            val capturedIds = slot<List<String>>()
            coEvery { mockCache.put(testUserId, movieViewId, BaseItemKind.MOVIE, capture(capturedIds)) } just Runs

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify { mockCache.put(testUserId, movieViewId, BaseItemKind.MOVIE, any()) }
            coVerify { mockCache.save() }
        }

    @Test
    fun doWork_cachesTvShowSuggestions_forTvLibrary() =
        runTest {
            val tvViewId = UUID.randomUUID()
            val tvView = mockView(tvViewId, CollectionType.TVSHOWS)
            val seriesItem = mockItem()

            every { mockPreferences.data } returns flowOf(mockAppPreferences(maxItemsPerRow = 10))
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } returns mockQueryResult(listOf(tvView))
            io.mockk.mockkObject(GetItemsRequestHandler)
            coEvery { GetItemsRequestHandler.execute(mockApi, any()) } returns mockQueryResult(listOf(seriesItem))

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify { mockCache.put(testUserId, tvViewId, BaseItemKind.SERIES, any()) }
            coVerify { mockCache.save() }
        }

    @Test
    fun doWork_skipsUnsupportedCollectionTypes() =
        runTest {
            val musicViewId = UUID.randomUUID()
            val musicView = mockView(musicViewId, CollectionType.MUSIC)
            val photoViewId = UUID.randomUUID()
            val photoView = mockView(photoViewId, CollectionType.HOMEVIDEOS)

            every { mockPreferences.data } returns flowOf(mockAppPreferences())
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } returns
                mockQueryResult(
                    listOf(musicView, photoView),
                )

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            // Should not cache anything for unsupported collection types
            coVerify(exactly = 0) { mockCache.put(any(), any(), any(), any()) }
            coVerify { mockCache.save() }
        }

    @Test
    fun doWork_processesMultipleLibraries() =
        runTest {
            val movieViewId = UUID.randomUUID()
            val tvViewId = UUID.randomUUID()
            val movieView = mockView(movieViewId, CollectionType.MOVIES)
            val tvView = mockView(tvViewId, CollectionType.TVSHOWS)

            every { mockPreferences.data } returns flowOf(mockAppPreferences(maxItemsPerRow = 10))
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } returns
                mockQueryResult(
                    listOf(movieView, tvView),
                )
            io.mockk.mockkObject(GetItemsRequestHandler)
            coEvery { GetItemsRequestHandler.execute(mockApi, any()) } returns mockQueryResult(listOf(mockItem()))

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify { mockCache.put(testUserId, movieViewId, BaseItemKind.MOVIE, any()) }
            coVerify { mockCache.put(testUserId, tvViewId, BaseItemKind.SERIES, any()) }
            coVerify { mockCache.save() }
        }

    @Test
    fun doWork_handlesNullCollectionType() =
        runTest {
            val viewId = UUID.randomUUID()
            val viewWithNullType = mockView(viewId, null)

            every { mockPreferences.data } returns flowOf(mockAppPreferences())
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } returns
                mockQueryResult(
                    listOf(viewWithNullType),
                )

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            // Should skip views with null collection type
            coVerify(exactly = 0) { mockCache.put(any(), any(), any(), any()) }
        }

    // endregion

    // region Preferences Tests

    @Test
    fun doWork_usesDefaultItemsPerRow_whenPreferenceNotSet() =
        runTest {
            val prefs =
                mockk<AppPreferences> {
                    every { homePagePreferences } returns
                        mockk {
                            every { maxItemsPerRow } returns 0 // Not set
                        }
                }

            every { mockPreferences.data } returns flowOf(prefs)
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } returns mockQueryResult(emptyList())

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
        }

    @Test
    fun doWork_usesDefaultPreferences_whenDataStoreEmpty() =
        runTest {
            every { mockPreferences.data } returns flowOf()
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } returns mockQueryResult(emptyList())

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
        }

    // endregion

    // region Error Handling Tests

    @Test
    fun doWork_returnsRetry_onApiClientException() =
        runTest {
            every { mockPreferences.data } returns flowOf(mockAppPreferences())
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } throws ApiClientException("Network error")

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
        }

    @Test
    fun doWork_returnsFailure_onGenericException() =
        runTest {
            every { mockPreferences.data } returns flowOf(mockAppPreferences())
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } throws RuntimeException("Unexpected error")

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun doWork_returnsRetry_onApiExceptionDuringItemsFetch() =
        runTest {
            val movieViewId = UUID.randomUUID()
            val movieView = mockView(movieViewId, CollectionType.MOVIES)

            every { mockPreferences.data } returns flowOf(mockAppPreferences())
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } returns mockQueryResult(listOf(movieView))
            io.mockk.mockkObject(GetItemsRequestHandler)
            coEvery { GetItemsRequestHandler.execute(mockApi, any()) } throws ApiClientException("Fetch failed")

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
        }

    @Test
    fun doWork_returnsSuccess_whenSomeViewsFailPartialSuccess() =
        runTest {
            val movieViewId = UUID.randomUUID()
            val tvViewId = UUID.randomUUID()
            val movieView = mockView(movieViewId, CollectionType.MOVIES)
            val tvView = mockView(tvViewId, CollectionType.TVSHOWS)

            every { mockPreferences.data } returns flowOf(mockAppPreferences(maxItemsPerRow = 10))
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } returns
                mockQueryResult(listOf(movieView, tvView))
            io.mockk.mockkObject(GetItemsRequestHandler)
            coEvery {
                GetItemsRequestHandler.execute(mockApi, match { it.parentId == movieViewId })
            } returns mockQueryResult(listOf(mockItem()))
            coEvery {
                GetItemsRequestHandler.execute(mockApi, match { it.parentId == tvViewId })
            } throws RuntimeException("Network error for TV view")

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify { mockCache.put(testUserId, movieViewId, BaseItemKind.MOVIE, any()) }
            coVerify(exactly = 0) { mockCache.put(testUserId, tvViewId, BaseItemKind.SERIES, any()) }
            coVerify { mockCache.save() }
        }

    // endregion

    // region Empty Results Tests

    @Test
    fun doWork_handlesEmptyItemsResponse() =
        runTest {
            val movieViewId = UUID.randomUUID()
            val movieView = mockView(movieViewId, CollectionType.MOVIES)

            every { mockPreferences.data } returns flowOf(mockAppPreferences())
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } returns mockQueryResult(listOf(movieView))
            io.mockk.mockkObject(GetItemsRequestHandler)
            coEvery { GetItemsRequestHandler.execute(mockApi, any()) } returns mockQueryResult(emptyList())

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify { mockCache.put(testUserId, movieViewId, BaseItemKind.MOVIE, emptyList()) }
        }

    @Test
    fun doWork_handlesNullItemsResponse() =
        runTest {
            val movieViewId = UUID.randomUUID()
            val movieView = mockView(movieViewId, CollectionType.MOVIES)

            every { mockPreferences.data } returns flowOf(mockAppPreferences())
            coEvery { mockUserViewsApi.getUserViews(userId = testUserId) } returns mockQueryResult(listOf(movieView))
            io.mockk.mockkObject(GetItemsRequestHandler)
            coEvery { GetItemsRequestHandler.execute(mockApi, any()) } returns
                mockQueryResult(emptyList()) as Response<BaseItemDtoQueryResult>

            val worker = createWorker()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify { mockCache.put(testUserId, movieViewId, BaseItemKind.MOVIE, emptyList()) }
        }

    // endregion

    // region Companion Object Tests

    @Test
    fun workName_isCorrect() {
        assertEquals(
            "com.github.damontecres.wholphin.services.SuggestionsWorker",
            SuggestionsWorker.WORK_NAME,
        )
    }

    @Test
    fun paramKeys_areCorrect() {
        assertEquals("userId", SuggestionsWorker.PARAM_USER_ID)
        assertEquals("serverId", SuggestionsWorker.PARAM_SERVER_ID)
    }

    // endregion
}
