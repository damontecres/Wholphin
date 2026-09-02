package com.github.damontecres.wholphin.ui.detail

import android.content.Context
import com.github.damontecres.wholphin.data.LibraryDisplayInfoDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.FilterOptionCache
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.NavDrawerItemState
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.RememberedTabService
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.test.item
import com.github.damontecres.wholphin.test.movie
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.main.settings.Library
import com.github.damontecres.wholphin.ui.successQueryResult
import com.github.damontecres.wholphin.util.DataLoadingState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.artistsApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.personsApi
import org.jellyfin.sdk.api.operations.ArtistsApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.api.operations.PersonsApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.request.GetArtistsRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetPersonsRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TestFavoritesViewModel {
    private val context: Context = mockk()
    private val api: ApiClient = mockk()
    private val navigationManager: NavigationManager = mockk()
    private val serverRepository: ServerRepository = mockk()
    private val libraryDisplayInfoDao: LibraryDisplayInfoDao = mockk()
    private val favoriteWatchManager: FavoriteWatchManager = mockk()
    private val backdropService: BackdropService = mockk()
    private val userPreferencesService: UserPreferencesService = mockk()
    private val mediaManagementService: MediaManagementService = mockk()
    private val streamChoiceService: StreamChoiceService = mockk()
    private val mediaReportService: MediaReportService = mockk()
    private val filterOptionCache: FilterOptionCache = mockk()
    private val rememberedTabService: RememberedTabService = mockk()
    private val navDrawerService: NavDrawerService = mockk()

    private val itemsApi: ItemsApi = mockk()
    private val artistsApi: ArtistsApi = mockk()
    private val personsApi: PersonsApi = mockk()

    @Before
    fun setup() {
        every { api.itemsApi } returns itemsApi
        every { api.artistsApi } returns artistsApi
        every { api.personsApi } returns personsApi
        every { navDrawerService.state } returns
            MutableStateFlow(
                NavDrawerItemState(
                    allLibraries =
                        listOf(
                            library(CollectionType.MOVIES),
                            library(CollectionType.TVSHOWS),
                            library(CollectionType.BOXSETS),
                        ),
                ),
            )
    }

    private fun createViewModel(): FavoritesViewModel =
        FavoritesViewModel(
            context = context,
            api = api,
            navigationManager = navigationManager,
            serverRepository = serverRepository,
            libraryDisplayInfoDao = libraryDisplayInfoDao,
            favoriteWatchManager = favoriteWatchManager,
            backdropService = backdropService,
            userPreferencesService = userPreferencesService,
            mediaManagementService = mediaManagementService,
            streamChoiceService = streamChoiceService,
            mediaReportService = mediaReportService,
            filterOptionCache = filterOptionCache,
            rememberedTabService = rememberedTabService,
            navDrawerService = navDrawerService,
        ).apply {
            setupPossibleTypes()
        }

    private val items = listOf(BaseItem(movie()))

    private val empty = emptyList<BaseItem>()

    private fun successJob(items: List<BaseItem>) = CompletableDeferred(DataLoadingState.Success(items))

    private fun errorJob() = CompletableDeferred(DataLoadingState.Error())

    private suspend fun testInitialFetchAndWait(
        firstTabKey: BaseItemKind,
        jobs: Map<BaseItemKind, Deferred<DataLoadingState<List<BaseItem?>>>>,
        asserts: (FavoritesPageState) -> Unit,
    ) {
        createViewModel().let { viewModel ->
            viewModel.initialFetchAndWait(
                firstTabKey = firstTabKey,
                jobs = jobs,
            )
            viewModel.state.value.let { state ->
                asserts.invoke(state)
            }
        }
    }

    @Test
    fun `Test tabKey for first success`() =
        runTest {
            val jobs =
                mapOf(
                    BaseItemKind.MOVIE to successJob(items),
                    BaseItemKind.SERIES to successJob(items),
                )

            testInitialFetchAndWait(
                firstTabKey = BaseItemKind.MOVIE,
                jobs = jobs,
            ) { state ->
                assertEquals(BaseItemKind.MOVIE, state.tabKey)
            }
        }

    @Test
    fun `Test tabKey for second success`() =
        runTest {
            val jobs =
                mapOf(
                    BaseItemKind.MOVIE to successJob(empty),
                    BaseItemKind.SERIES to successJob(items),
                )

            testInitialFetchAndWait(
                firstTabKey = BaseItemKind.MOVIE,
                jobs = jobs,
            ) { state ->
                assertEquals(BaseItemKind.SERIES, state.tabKey)
            }

            testInitialFetchAndWait(
                firstTabKey = BaseItemKind.SERIES,
                jobs = jobs,
            ) { state ->
                assertEquals(BaseItemKind.SERIES, state.tabKey)
            }
        }

    @Test
    fun `Test tabKey for first failure`() =
        runTest {
            val jobs =
                mapOf(
                    BaseItemKind.MOVIE to errorJob(),
                    BaseItemKind.SERIES to successJob(items),
                )

            testInitialFetchAndWait(
                firstTabKey = BaseItemKind.MOVIE,
                jobs = jobs,
            ) { state ->
                assertEquals(BaseItemKind.MOVIE, state.tabKey)
            }

            testInitialFetchAndWait(
                firstTabKey = BaseItemKind.SERIES,
                jobs = jobs,
            ) { state ->
                assertEquals(BaseItemKind.SERIES, state.tabKey)
            }
        }

    @Test
    fun `Test tabKey for multiple empty`() =
        runTest {
            val jobs =
                mapOf(
                    BaseItemKind.MOVIE to successJob(empty),
                    BaseItemKind.SERIES to successJob(items),
                    BaseItemKind.EPISODE to successJob(empty),
                    BaseItemKind.BOX_SET to successJob(items),
                )

            testInitialFetchAndWait(
                firstTabKey = BaseItemKind.MOVIE,
                jobs = jobs,
            ) { state ->
                assertEquals(BaseItemKind.SERIES, state.tabKey)
            }

            testInitialFetchAndWait(
                firstTabKey = BaseItemKind.SERIES,
                jobs = jobs,
            ) { state ->
                assertEquals(BaseItemKind.SERIES, state.tabKey)
            }

            testInitialFetchAndWait(
                firstTabKey = BaseItemKind.EPISODE,
                jobs = jobs,
            ) { state ->
                assertEquals(BaseItemKind.SERIES, state.tabKey)
            }
        }

    @Test
    fun `Test tabKey for multiple empty with first error`() =
        runTest {
            val jobs =
                mapOf(
                    BaseItemKind.MOVIE to errorJob(),
                    BaseItemKind.SERIES to successJob(empty),
                    BaseItemKind.EPISODE to successJob(items),
                    BaseItemKind.BOX_SET to successJob(items),
                )

            testInitialFetchAndWait(
                firstTabKey = BaseItemKind.SERIES,
                jobs = jobs,
            ) { state ->
                assertEquals(BaseItemKind.MOVIE, state.tabKey)
            }
        }

    @Test
    fun `Test tabKey for multiple empty with first empty`() =
        runTest {
            val jobs =
                mapOf(
                    BaseItemKind.MOVIE to successJob(empty),
                    BaseItemKind.SERIES to successJob(empty),
                    BaseItemKind.EPISODE to successJob(empty),
                    BaseItemKind.BOX_SET to successJob(items),
                )

            testInitialFetchAndWait(
                firstTabKey = BaseItemKind.EPISODE,
                jobs = jobs,
            ) { state ->
                assertEquals(BaseItemKind.BOX_SET, state.tabKey)
            }

            testInitialFetchAndWait(
                firstTabKey = BaseItemKind.BOX_SET,
                jobs = jobs,
            ) { state ->
                assertEquals(BaseItemKind.BOX_SET, state.tabKey)
            }
        }

    @Test
    fun `Test fetchType basic`() =
        runTest {
            val movie = movie()
            coEvery { itemsApi.getItems(any<GetItemsRequest>()) } returns successQueryResult(listOf(movie))

            val viewModel = createViewModel()
            val data =
                viewModel.fetchType(
                    type = BaseItemKind.MOVIE,
                    sortAndDirection = SortAndDirection.DEFAULT,
                    filter = GetItemsFilter(),
                    viewOptions = ViewOptions(),
                )
            assertTrue(data is DataLoadingState.Success)
            assertEquals(BaseItem(movie), (data as DataLoadingState.Success).data.first())
            viewModel.state.value.let { state ->
                assertTrue(BaseItemKind.MOVIE in state.tabs)
                assertTrue(BaseItemKind.MOVIE in state.favorites)
                state.favorites[BaseItemKind.MOVIE]!!.let { st ->
                    assertTrue(st.item is DataLoadingState.Success)
                    assertTrue(st.items is DataLoadingState.Success)
                }
            }
            coVerify(exactly = 1) { itemsApi.getItems(any<GetItemsRequest>()) }
        }

    @Test
    fun `Test fetchType with error`() =
        runTest {
            val ex = Exception()
            coEvery { itemsApi.getItems(any<GetItemsRequest>()) } throws ex

            val viewModel = createViewModel()
            val data =
                viewModel.fetchType(
                    type = BaseItemKind.MOVIE,
                    sortAndDirection = SortAndDirection.DEFAULT,
                    filter = GetItemsFilter(),
                    viewOptions = ViewOptions(),
                )
            assertTrue(data is DataLoadingState.Error)
            assertEquals(ex, (data as DataLoadingState.Error).exception)
            viewModel.state.value.let { state ->
                assertTrue(BaseItemKind.MOVIE in state.tabs)
                assertTrue(BaseItemKind.MOVIE in state.favorites)
                state.favorites[BaseItemKind.MOVIE]!!.let { st ->
                    assertTrue(st.item is DataLoadingState.Success)
                    assertTrue(st.items is DataLoadingState.Error)
                    assertEquals(ex, (st.items as DataLoadingState.Error).exception)
                }
            }
            coVerify(exactly = 1) { itemsApi.getItems(any<GetItemsRequest>()) }
        }

    @Test
    fun `Test fetchType with empty result does not add tab`() =
        runTest {
            coEvery { itemsApi.getItems(any<GetItemsRequest>()) } returns successQueryResult()

            val viewModel = createViewModel()
            val data =
                viewModel.fetchType(
                    type = BaseItemKind.MOVIE,
                    sortAndDirection = SortAndDirection.DEFAULT,
                    filter = GetItemsFilter(),
                    viewOptions = ViewOptions(),
                )
            assertTrue(data is DataLoadingState.Success)
            assertEquals(0, (data as DataLoadingState.Success).data.size)
            viewModel.state.value.let { state ->
                assertFalse(BaseItemKind.MOVIE in state.tabs)

                assertTrue(BaseItemKind.MOVIE in state.favorites)
                state.favorites[BaseItemKind.MOVIE]!!.let { st ->
                    assertTrue(st.item is DataLoadingState.Success)
                    assertTrue(st.items is DataLoadingState.Success)
                    assertEquals(0, (st.items as DataLoadingState.Success).data.size)
                }
            }
            coVerify(exactly = 1) { itemsApi.getItems(any<GetItemsRequest>()) }
        }

    @Test
    fun `Test fetchType for artists`() =
        runTest {
            val item = item(BaseItemKind.MUSIC_ARTIST)
            coEvery { artistsApi.getArtists(any<GetArtistsRequest>()) } returns successQueryResult(listOf(item))

            val viewModel = createViewModel()
            val data =
                viewModel.fetchType(
                    type = BaseItemKind.MUSIC_ARTIST,
                    sortAndDirection = SortAndDirection.DEFAULT,
                    filter = GetItemsFilter(),
                    viewOptions = ViewOptions(),
                )
            assertTrue(data is DataLoadingState.Success)
            assertEquals(BaseItem(item), (data as DataLoadingState.Success).data.first())
            viewModel.state.value.let { state ->
                assertTrue(BaseItemKind.MUSIC_ARTIST in state.tabs)
                assertTrue(BaseItemKind.MUSIC_ARTIST in state.favorites)
                state.favorites[BaseItemKind.MUSIC_ARTIST]!!.let { st ->
                    assertTrue(st.item is DataLoadingState.Success)
                    assertTrue(st.items is DataLoadingState.Success)
                }
            }
            coVerify(exactly = 1) { artistsApi.getArtists(any<GetArtistsRequest>()) }
            coVerify(exactly = 0) { itemsApi.getItems(any<GetItemsRequest>()) }
            coVerify(exactly = 0) { personsApi.getPersons(any<GetPersonsRequest>()) }
        }

    @Test
    fun `Test fetchType for persons`() =
        runTest {
            val item = item(BaseItemKind.PERSON)
            coEvery { personsApi.getPersons(any<GetPersonsRequest>()) } returns successQueryResult(listOf(item))

            val viewModel = createViewModel()
            val data =
                viewModel.fetchType(
                    type = BaseItemKind.PERSON,
                    sortAndDirection = SortAndDirection.DEFAULT,
                    filter = GetItemsFilter(),
                    viewOptions = ViewOptions(),
                )
            assertTrue(data is DataLoadingState.Success)
            assertEquals(BaseItem(item), (data as DataLoadingState.Success).data.first())
            viewModel.state.value.let { state ->
                assertTrue(BaseItemKind.PERSON in state.tabs)
                assertTrue(BaseItemKind.PERSON in state.favorites)
                state.favorites[BaseItemKind.PERSON]!!.let { st ->
                    assertTrue(st.item is DataLoadingState.Success)
                    assertTrue(st.items is DataLoadingState.Success)
                }
            }
            coVerify(exactly = 1) { personsApi.getPersons(any<GetPersonsRequest>()) }
            coVerify(exactly = 0) { itemsApi.getItems(any<GetItemsRequest>()) }
            coVerify(exactly = 0) { artistsApi.getArtists(any<GetArtistsRequest>()) }
        }
}

private fun library(type: CollectionType) =
    Library(
        itemId = UUID.randomUUID(),
        name = "name-${type.serialName}",
        type = BaseItemKind.COLLECTION_FOLDER,
        collectionType = type,
        isRecordingFolder = false,
    )
