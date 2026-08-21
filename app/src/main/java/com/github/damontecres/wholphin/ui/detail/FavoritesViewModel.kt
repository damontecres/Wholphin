package com.github.damontecres.wholphin.ui.detail

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.LibraryDisplayInfoDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.data.model.LibraryDisplayInfo
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.FilterOptionCache
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.RememberedTabService
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.collectLatestIn
import com.github.damontecres.wholphin.ui.components.CollectionFolderState
import com.github.damontecres.wholphin.ui.components.CollectionFolderViewActions
import com.github.damontecres.wholphin.ui.components.ContextMenuProvider
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.components.defaultViewOptions
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.equalsNotNull
import com.github.damontecres.wholphin.ui.formatTypeName
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.NavDrawerItem
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetArtistsHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.GetPersonsHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetArtistsRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetPersonsRequest
import timber.log.Timber
import java.util.SortedMap
import java.util.TreeMap
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        val navigationManager: NavigationManager,
        private val serverRepository: ServerRepository,
        private val libraryDisplayInfoDao: LibraryDisplayInfoDao,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val backdropService: BackdropService,
        private val userPreferencesService: UserPreferencesService,
        private val mediaManagementService: MediaManagementService,
        val streamChoiceService: StreamChoiceService,
        val mediaReportService: MediaReportService,
        private val filterOptionCache: FilterOptionCache,
        private val rememberedTabService: RememberedTabService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(FavoritesPageState())
        val state: StateFlow<FavoritesPageState> = _state

        fun init() {
            viewModelScope.launchIO {
                val rememberTabs =
                    userPreferencesService
                        .getCurrent()
                        .appPreferences.interfacePreferences.rememberSelectedTab
                val tabKey =
                    if (rememberTabs) {
                        rememberedTabService.getRememberedTab(NavDrawerItem.Favorites.id)?.let {
                            BaseItemKind.entries[it]
                        } ?: favoriteOptions.first()
                    } else {
                        favoriteOptions.first()
                    }
                val jobs = favoriteOptions.associateWith { type -> initialFetchForType(type) }
                initialFetchAndWait(tabKey, jobs)
            }
            userPreferencesService.flow
                .map { it.appPreferences.interfacePreferences.showClock }
                .collectLatestIn(viewModelScope) { isShowClock ->
                    _state.update { it.copy(isShowClock = isShowClock) }
                }
        }

        /**
         * Starts queries for favorites, waiting for [firstTabKey] to complete
         *
         * Exposed for testing
         */
        internal suspend fun initialFetchAndWait(
            firstTabKey: BaseItemKind,
            jobs: Map<BaseItemKind, Deferred<DataLoadingState<List<BaseItem?>>>>,
        ) {
            val completed =
                jobs[firstTabKey]?.let {
                    when (val result = it.await()) {
                        DataLoadingState.Loading,
                        DataLoadingState.Pending,
                        -> false

                        is DataLoadingState.Error,
                        -> true

                        is DataLoadingState.Success<List<BaseItem?>> -> result.data.isNotEmpty()
                    }
                } == true
            if (completed) {
                Timber.v("Got completed for %s", firstTabKey)
                _state.update {
                    it.copy(
                        loadingState = FavoritesLoadingState.Success,
                        tabKey = firstTabKey,
                    )
                }
            } else {
                // If the first key wasn't successful, wait in order until first successful & non-empty
                val firstCompleted =
                    favoriteOptions.firstOrNull { type ->
                        val job = jobs[type] ?: return@firstOrNull false
                        val result = job.await()
                        result is DataLoadingState.Error ||
                            (result is DataLoadingState.Success<List<BaseItem?>> && result.data.isNotEmpty())
                    }
                _state.update {
                    if (firstCompleted != null) {
                        it.copy(
                            loadingState = FavoritesLoadingState.Success,
                            tabKey = firstCompleted,
                        )
                    } else {
                        // None were error or successful & non-empty
                        // Check if they were all empty which means the user has no favorites at all
                        val allEmpty =
                            jobs.all { (_, job) ->
                                val result = job.await()
                                result is DataLoadingState.Success<List<BaseItem?>> && result.data.isEmpty()
                            }
                        if (allEmpty) {
                            it.copy(
                                loadingState = FavoritesLoadingState.NoFavorites,
                            )
                        } else {
                            // This means a least one failed
                            val firstError =
                                jobs.entries.firstOrNull { (_, job) ->
                                    job.await() is DataLoadingState.Error
                                }
                            if (firstError != null) {
                                it.copy(
                                    loadingState = FavoritesLoadingState.Success,
                                    tabKey = firstError.key,
                                )
                            } else {
                                // This shouldn't happen
                                it.copy(
                                    loadingState = FavoritesLoadingState.Error("An unknown error occurred"),
                                )
                            }
                        }
                    }
                }
            }
        }

        /**
         * Do the initial fetch for a [BaseItemKind] which will look up any existing saved sort and filter
         */
        private fun initialFetchForType(type: BaseItemKind): Deferred<DataLoadingState<List<BaseItem?>>> =
            viewModelScope.async(WholphinDispatchers.IO) {
                try {
                    val collectionState =
                        CollectionFolderState(
                            item = DataLoadingState.Loading,
                            items = DataLoadingState.Loading,
                            backgroundLoading = LoadingState.Loading,
                            viewOptions = ViewOptions(),
                        )
                    _state.value.favorites[type] = collectionState

                    val libraryDisplayInfo =
                        serverRepository.currentUser?.let { user ->
                            libraryDisplayInfoDao.getItem(user, libraryDisplayItemId(type))
                        }
                    val sortAndDirection =
                        libraryDisplayInfo?.sortAndDirection ?: SortAndDirection.DEFAULT
                    val filter = libraryDisplayInfo?.filter ?: GetItemsFilter(favorite = true)
                    val viewOptions = libraryDisplayInfo?.viewOptions ?: type.defaultViewOptions
                    fetchType(type, sortAndDirection, filter, viewOptions)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching favorites for %s", type)
                    updateForException(type, ex)
                    DataLoadingState.Error(ex)
                }
            }

        /**
         * Query for favorites of the specified [BaseItemKind] using the specified sort and filter
         *
         * Exposed for testing
         */
        internal suspend fun fetchType(
            type: BaseItemKind,
            sortAndDirection: SortAndDirection,
            filter: GetItemsFilter,
            viewOptions: ViewOptions,
        ): DataLoadingState<List<BaseItem?>> {
            val collectionState =
                CollectionFolderState(
                    item = DataLoadingState.Loading,
                    items = DataLoadingState.Loading,
                    backgroundLoading = LoadingState.Loading,
                    viewOptions = ViewOptions(),
                )
            _state.value.favorites[type] = collectionState
            try {
                val pager = createPager(type, filter, sortAndDirection)
                _state.value.favorites[type] =
                    collectionState.copy(
                        item = DataLoadingState.Success(null),
                        items = DataLoadingState.Success(pager),
                        backgroundLoading = LoadingState.Success,
                        sortAndDirection = sortAndDirection,
                        filter = filter,
                        viewOptions = viewOptions,
                    )
                Timber.v("Got %s favorites for %s", pager.size, type)
                _state.update {
                    val newTabs =
                        it.createMap {
                            if (pager.isNotEmpty()) {
                                put(type, TabDetails(formatTypeName(type)))
                            } else {
                                remove(type)
                            }
                        }
                    it.copy(
                        tabs = newTabs,
                    )
                }
                return DataLoadingState.Success(pager)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                updateForException(type, ex)
                return DataLoadingState.Error(ex)
            }
        }

        /**
         * If fetching data for a type fails, this should be called to update the state with the error
         */
        private fun updateForException(
            type: BaseItemKind,
            ex: Exception,
        ) {
            _state.update {
                // Want to store the error for the tab
                _state.value.favorites[type] =
                    CollectionFolderState(
                        item = DataLoadingState.Success(null),
                        items = DataLoadingState.Error(ex),
                        backgroundLoading = LoadingState.Success,
                        viewOptions = type.defaultViewOptions,
                    )
                // And make sure the tab is included
                val newTabs =
                    it.createMap {
                        put(type, TabDetails(formatTypeName(type)))
                    }
                it.copy(tabs = newTabs)
            }
        }

        private fun createGetItemsRequest(
            type: BaseItemKind,
            filter: GetItemsFilter,
            sortAndDirection: SortAndDirection,
        ): GetItemsRequest =
            filter.applyTo(
                GetItemsRequest(
                    isFavorite = true,
                    recursive = true,
                    includeItemTypes = listOf(type),
                    fields = SlimItemFields,
                    sortBy = listOf(sortAndDirection.sort),
                    sortOrder = listOf(sortAndDirection.direction),
                ),
                overwriteIncludeTypes = false,
            )

        private fun createGetArtistsRequest(
            filter: GetItemsFilter,
            sortAndDirection: SortAndDirection,
        ): GetArtistsRequest =
            filter.applyTo(
                GetArtistsRequest(
                    isFavorite = true,
                    fields = SlimItemFields,
                    sortBy = listOf(sortAndDirection.sort),
                    sortOrder = listOf(sortAndDirection.direction),
                ),
            )

        private fun createGetPersonsRequest(filter: GetItemsFilter): GetPersonsRequest =
            filter.applyTo(
                GetPersonsRequest(
                    isFavorite = true,
                    fields = SlimItemFields,
                ),
            )

        private suspend fun createPager(
            type: BaseItemKind,
            filter: GetItemsFilter,
            sortAndDirection: SortAndDirection,
        ): ApiRequestPager<*> =
            when (type) {
                BaseItemKind.MUSIC_ARTIST -> {
                    val request = createGetArtistsRequest(filter, sortAndDirection)
                    ApiRequestPager(api, request, GetArtistsHandler, viewModelScope, pageSize = 50)
                }

                BaseItemKind.PERSON -> {
                    val request = createGetPersonsRequest(filter)
                    ApiRequestPager(api, request, GetPersonsHandler, viewModelScope, pageSize = 50)
                }

                else -> {
                    val request = createGetItemsRequest(type, filter, sortAndDirection)
                    ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope, pageSize = 50)
                }
            }.init()

        fun libraryDisplayItemId(type: BaseItemKind): String {
            // Previous version used hardcoded keys before more types were supported
            // So override those type names so that user's settings are maintained
            val typeKey =
                when (type) {
                    BaseItemKind.MOVIE -> "movies"
                    BaseItemKind.SERIES -> "series"
                    BaseItemKind.EPISODE -> "episodes"
                    BaseItemKind.VIDEO -> "videos"
                    BaseItemKind.PLAYLIST -> "playlists"
                    BaseItemKind.PERSON -> "people"
                    else -> type.serialName
                }
            return "${NavDrawerItem.Favorites.id}_$typeKey"
        }

        private fun collectionStateFor(type: BaseItemKind): CollectionFolderState? = state.value.favorites[type]

        fun updateSelectedTabKey(newKey: BaseItemKind) {
            viewModelScope.launchDefault { backdropService.clearBackdrop() }
            viewModelScope.launchIO {
                val rememberTabs =
                    userPreferencesService
                        .getCurrent()
                        .appPreferences.interfacePreferences.rememberSelectedTab
                if (rememberTabs) {
                    rememberedTabService.saveRememberedTab(
                        NavDrawerItem.Favorites.id,
                        newKey.ordinal,
                    )
                }
            }
            _state.update { it.copy(tabKey = newKey) }
        }

        private suspend fun refreshAfterMutate(
            type: BaseItemKind,
            position: Int,
            itemId: UUID,
        ) {
            try {
                val pager =
                    collectionStateFor(type)?.let {
                        ((it.items as? DataLoadingState.Success)?.data as? ApiRequestPager<*>)
                    }
                pager?.refreshItem(position, itemId)
            } catch (ex: Exception) {
                Timber.e(ex, "Error refreshing after mutate %s", itemId)
                showToast(context, "Error refreshing after item mutate")
            }
        }

        private suspend fun refreshAfterDelete(
            type: BaseItemKind,
            position: Int,
            deletedItem: BaseItem,
        ) {
            try {
                val pager =
                    collectionStateFor(type)?.let {
                        ((it.items as? DataLoadingState.Success)?.data as? ApiRequestPager<*>)
                    }

                position.let {
                    Timber.v("Item deleted: position=%s, id=%s", it, deletedItem.id)
                    val item = pager?.get(it)
                    // Exact item deleted (eg a movie) or deleted item was within the series
                    if (item?.id == deletedItem.id ||
                        equalsNotNull(item?.data?.id, deletedItem.data.seriesId)
                    ) {
                        pager?.refreshPagesAfter(position)
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Error refreshing after deleted item %s", deletedItem.id)
                showToast(context, "Error refreshing after item deleted")
            }
        }

        private fun saveLibraryDisplayInfo(
            type: BaseItemKind,
            newFilter: GetItemsFilter,
            newSort: SortAndDirection,
            viewOptions: ViewOptions?,
        ) {
            serverRepository.currentUser?.let { user ->
                viewModelScope.launchIO {
                    val libraryDisplayInfo =
                        LibraryDisplayInfo(
                            userId = user.rowId,
                            itemId = libraryDisplayItemId(type),
                            sort = newSort.sort,
                            direction = newSort.direction,
                            filter = newFilter,
                            viewOptions = viewOptions,
                        )
                    libraryDisplayInfoDao.saveItem(libraryDisplayInfo)
                }
            }
        }

        private fun saveViewOptions(
            type: BaseItemKind,
            viewOptions: ViewOptions,
        ) {
            viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
                collectionStateFor(type)?.let { collectionState ->
                    saveLibraryDisplayInfo(
                        type = type,
                        newFilter = collectionState.filter,
                        newSort = collectionState.sortAndDirection,
                        viewOptions = viewOptions,
                    )
                    state.value.favorites[type] =
                        collectionState.copy(
                            viewOptions = viewOptions,
                        )
                    if (!viewOptions.showBackdrop) {
                        backdropService.clearBackdrop()
                    }
                }
            }
        }

        fun createTypedProvider(type: BaseItemKind) = TypedProvider(type)

        /**
         * This provides context menu actions and other actions on a per [BaseItemKind] basis
         */
        inner class TypedProvider(
            val type: BaseItemKind,
        ) : ContextMenuProvider,
            CollectionFolderViewActions {
            override fun isAdministrator(): Boolean = serverRepository.currentUserDto?.policy?.isAdministrator == true

            override fun navigateTo(destination: Destination) = navigationManager.navigateTo(destination)

            override fun canDelete(
                item: BaseItem,
                appPreferences: AppPreferences,
            ): Boolean = mediaManagementService.canDelete(item, appPreferences)

            override fun deleteItem(
                index: Int,
                item: BaseItem,
            ) {
                deleteItem(context, mediaManagementService, item) {
                    viewModelScope.launchDefault {
                        refreshAfterDelete(type, index, item)
                    }
                }
            }

            override fun setWatched(
                position: Int,
                itemId: UUID,
                played: Boolean,
            ) {
                viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
                    favoriteWatchManager.setWatched(itemId, played)
                    refreshAfterMutate(type, position, itemId)
                }
            }

            override fun setFavorite(
                position: Int,
                itemId: UUID,
                favorite: Boolean,
            ) {
                viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
                    favoriteWatchManager.setWatched(itemId, favorite)
                    collectionStateFor(type)?.let { collectionState ->
                        // TODO use pager.refreshPagesAfter ??
                        fetchType(
                            type,
                            collectionState.sortAndDirection,
                            collectionState.filter,
                            collectionState.viewOptions,
                        )
                    }
                }
            }

            override fun sendReportFor(itemId: UUID) = mediaReportService.sendReportFor(itemId)

            override fun updateBackdrop(item: BaseItem) {
                viewModelScope.launchIO {
                    backdropService.submit(item)
                }
            }

            override fun onSortChange(
                sortAndDirection: SortAndDirection,
                recursive: Boolean,
                filter: GetItemsFilter,
            ) {
                viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
                    Timber.v("onSortChange: type=%s, sortAndDirection=%s", type, sortAndDirection)
                    collectionStateFor(type)?.let { collectionState ->
                        saveLibraryDisplayInfo(
                            type = type,
                            newFilter = filter,
                            newSort = sortAndDirection,
                            viewOptions = collectionState.viewOptions,
                        )
                        state.value.favorites[type] =
                            collectionState.copy(
                                filter = filter,
                                sortAndDirection = sortAndDirection,
                            )
                        fetchType(type, sortAndDirection, filter, collectionState.viewOptions)
                    }
                }
            }

            override fun onFilterChange(
                newFilter: GetItemsFilter,
                recursive: Boolean,
            ) {
                viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
                    Timber.v("onFilterChange: type=%s, newFilter=%s", type, newFilter)
                    collectionStateFor(type)?.let { collectionState ->
                        saveLibraryDisplayInfo(
                            type = type,
                            newFilter = newFilter,
                            newSort = collectionState.sortAndDirection,
                            viewOptions = collectionState.viewOptions,
                        )
                        state.value.favorites[type] =
                            collectionState.copy(
                                filter = newFilter,
                            )
                        fetchType(
                            type,
                            collectionState.sortAndDirection,
                            newFilter,
                            collectionState.viewOptions,
                        )
                    }
                }
            }

            override suspend fun getFilterOptionValues(filterOption: ItemFilterBy<*>): List<FilterValueOption> =
                // TODO use includeItemTypes?
                filterOptionCache.getFilterOptionValues(
                    null,
                    filterOption,
                )

            override suspend fun positionOfLetter(letter: Char): Int? =
                collectionStateFor(type)?.let { collectionState ->
                    when (type) {
                        BaseItemKind.MUSIC_ARTIST -> {
                            val request =
                                createGetArtistsRequest(
                                    collectionState.filter,
                                    collectionState.sortAndDirection,
                                ).copy(
                                    enableUserData = false,
                                    limit = 0,
                                    enableTotalRecordCount = true,
                                )
                            GetArtistsHandler.execute(api, request).content.totalRecordCount
                        }

                        BaseItemKind.PERSON -> {
                            null
                        }

                        else -> {
                            val request =
                                createGetItemsRequest(
                                    type,
                                    collectionState.filter,
                                    collectionState.sortAndDirection,
                                ).copy(
                                    enableUserData = false,
                                    limit = 0,
                                    enableTotalRecordCount = true,
                                )
                            GetItemsRequestHandler.execute(api, request).content.totalRecordCount
                        }
                    }
                }

            override fun saveViewOptions(viewOptions: ViewOptions) {
                saveViewOptions(type, viewOptions)
            }
        }
    }

data class FavoritesPageState(
    val loadingState: FavoritesLoadingState = FavoritesLoadingState.Pending,
    val favorites: SnapshotStateMap<BaseItemKind, CollectionFolderState> = SnapshotStateMap(),
    val tabs: SortedMap<BaseItemKind, TabDetails> = TreeMap(compareBy { favoriteOptions.indexOf(it) }),
    val tabKey: BaseItemKind = favoriteOptions.first(),
    val isShowClock: Boolean = true,
)

sealed interface FavoritesLoadingState {
    data object Pending : FavoritesLoadingState

    data object Loading : FavoritesLoadingState

    data object Success : FavoritesLoadingState

    data object NoFavorites : FavoritesLoadingState

    data class Error(
        val message: String? = null,
        val exception: Throwable? = null,
    ) : FavoritesLoadingState {
        constructor(exception: Throwable) : this(null, exception)

        val localizedMessage: String =
            listOfNotNull(message, exception?.localizedMessage).joinToString(" - ")
    }
}

val favoriteOptions =
    listOf(
        BaseItemKind.MOVIE,
        BaseItemKind.SERIES,
        BaseItemKind.EPISODE,
        BaseItemKind.BOX_SET,
        BaseItemKind.PERSON,
        BaseItemKind.TV_CHANNEL,
        BaseItemKind.MUSIC_ALBUM,
        BaseItemKind.MUSIC_ARTIST,
        BaseItemKind.AUDIO,
        BaseItemKind.MUSIC_VIDEO,
        BaseItemKind.PLAYLIST,
        BaseItemKind.VIDEO,
        BaseItemKind.PHOTO,
        BaseItemKind.PHOTO_ALBUM,
    )

/**
 * Creates a new tab map from the current state applying the supplied block which can contain mutations
 */
private inline fun FavoritesPageState.createMap(block: TreeMap<BaseItemKind, TabDetails>.() -> Unit) =
    TreeMap<BaseItemKind, TabDetails>(compareBy { favoriteOptions.indexOf(it) })
        .apply {
            putAll(this@createMap.tabs)
            block.invoke(this)
        }
