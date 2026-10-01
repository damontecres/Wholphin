package com.github.damontecres.wholphin.ui.components

import androidx.lifecycle.SavedStateHandle
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilterOverride
import com.github.damontecres.wholphin.services.DeletedItem
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.ui.successQueryResult
import com.github.damontecres.wholphin.ui.successResponse
import com.github.damontecres.wholphin.util.GetArtistsHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.GetPersonsHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.operations.UserLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetArtistsRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Each endpoint is stubbed with a distinct total, so a returned index identifies which endpoint
 * produced it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionFolderViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val mockApi = mockk<ApiClient>(relaxed = true)
    private val mockServerRepository = mockk<ServerRepository>(relaxed = true)
    private val mockMediaManagementService = mockk<MediaManagementService>(relaxed = true)
    private val mockUserLibraryApi = mockk<UserLibraryApi>()

    private val artistsRequest = slot<GetArtistsRequest>()
    private val itemsRequest = slot<GetItemsRequest>()

    private val libraryId = UUID.randomUUID()

    private val library =
        BaseItemDto(
            id = libraryId,
            type = BaseItemKind.COLLECTION_FOLDER,
            collectionType = CollectionType.MUSIC,
        )

    @Before
    fun setUp() {
        WholphinDispatchers.configure(testDispatcher)

        every { mockApi.userLibraryApi } returns mockUserLibraryApi
        coEvery { mockUserLibraryApi.getItem(libraryId) } returns successResponse(library)

        // No current user, so no saved LibraryDisplayInfo is consulted
        every { mockServerRepository.currentUser } returns null
        every { mockMediaManagementService.deletedItemFlow } returns MutableSharedFlow<DeletedItem>()

        // mockkObject spies by default, so every handler must be stubbed or the real one runs
        // against the relaxed ApiClient
        mockkObject(GetItemsRequestHandler)
        mockkObject(GetArtistsHandler)
        mockkObject(GetPersonsHandler)
        coEvery { GetItemsRequestHandler.execute(mockApi, capture(itemsRequest)) } returns
            successQueryResult(totalRecordCount = LIBRARY_ITEM_COUNT)
        coEvery { GetArtistsHandler.execute(mockApi, capture(artistsRequest)) } returns
            successQueryResult(totalRecordCount = ARTIST_COUNT)
        coEvery { GetPersonsHandler.execute(mockApi, any()) } returns
            successQueryResult(totalRecordCount = PERSON_COUNT)
    }

    @After
    fun tearDown() {
        WholphinDispatchers.reset()
        unmockkObject(GetItemsRequestHandler)
        unmockkObject(GetArtistsHandler)
        unmockkObject(GetPersonsHandler)
    }

    private fun createViewModel(filter: GetItemsFilter) =
        CollectionFolderViewModel(
            savedStateHandle = SavedStateHandle(),
            api = mockApi,
            context = mockk(relaxed = true),
            serverRepository = mockServerRepository,
            libraryDisplayInfoDao = mockk(relaxed = true),
            favoriteWatchManager = mockk(relaxed = true),
            backdropService = mockk(relaxed = true),
            navigationManager = mockk(relaxed = true),
            themeSongPlayer = mockk(relaxed = true),
            userPreferencesService = mockk(relaxed = true),
            mediaManagementService = mockMediaManagementService,
            musicService = mockk(relaxed = true),
            streamChoiceService = mockk(relaxed = true),
            serverReportService = mockk(relaxed = true),
            filterOptionCache = mockk(relaxed = true),
            itemId = libraryId.toString(),
            initialSortAndDirection = null,
            recursive = true,
            collectionFilter = CollectionFolderFilter(filter = filter),
            useSeriesForPrimary = false,
            defaultViewOptions = ViewOptionsSquare,
        )

    /** Counting via /Items would return every song and album, overshooting the artist grid */
    @Test
    fun `positionOfLetter counts artists for the ARTIST override`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(GetItemsFilter(override = GetItemsFilterOverride.ARTIST))
            advanceUntilIdle()

            val position = viewModel.positionOfLetter('K')

            assertEquals(ARTIST_COUNT, position)
            assertEquals("K", artistsRequest.captured.nameLessThan)
            assertEquals(libraryId, artistsRequest.captured.parentId)
            assertEquals(0, artistsRequest.captured.limit)

            // Counting library items rather than artists is the bug this guards against
            coVerify(exactly = 0) { GetItemsRequestHandler.execute(any(), any<GetItemsRequest>()) }
        }

    /**
     * Without SortName, the alphabet highlight falls back to Name and highlights "T" for artists
     * like "The Beatles" even though the grid sorts them under "B".
     */
    @Test
    fun `artist pager requests SortName for alphabet highlighting`() =
        runTest(testDispatcher) {
            createViewModel(GetItemsFilter(override = GetItemsFilterOverride.ARTIST))
            advanceUntilIdle()

            assertTrue(ItemFields.SORT_NAME in artistsRequest.captured.fields.orEmpty())
        }

    /**
     * /Persons has no nameLessThan or startIndex, so counting up to a letter is not expressible.
     * Falling back to /Items would count media rather than people, ie the same defect as the artist
     * grid, so guard against it even though no screen currently sorts people by name.
     */
    @Test
    fun `positionOfLetter does not fall back to counting items for the PERSON override`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(GetItemsFilter(override = GetItemsFilterOverride.PERSON))
            advanceUntilIdle()

            val position = viewModel.positionOfLetter('K')

            assertEquals(null, position)
            coVerify(exactly = 0) { GetItemsRequestHandler.execute(any(), any<GetItemsRequest>()) }
            coVerify(exactly = 0) { GetArtistsHandler.execute(any(), any<GetArtistsRequest>()) }
        }

    @Test
    fun `positionOfLetter counts items for the NONE override`() =
        runTest(testDispatcher) {
            val viewModel =
                createViewModel(
                    GetItemsFilter(
                        override = GetItemsFilterOverride.NONE,
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                    ),
                )
            advanceUntilIdle()

            val position = viewModel.positionOfLetter('K')

            assertEquals(LIBRARY_ITEM_COUNT, position)
            assertEquals("K", itemsRequest.captured.nameLessThan)
            assertEquals(libraryId, itemsRequest.captured.parentId)
            assertEquals(0, itemsRequest.captured.limit)
        }

    companion object {
        private const val LIBRARY_ITEM_COUNT = 7315
        private const val ARTIST_COUNT = 730
        private const val PERSON_COUNT = 12
    }
}
