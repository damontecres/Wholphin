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
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.ui.SlimItemFields
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
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
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
    ) : ViewModel() {
        private val _state = MutableStateFlow(FavoritesPageState())
        val state: StateFlow<FavoritesPageState> = _state

        init {
            init()
        }

        fun init() {
            favoriteOptions.forEach { type ->
                viewModelScope.launchIO {
                    loadType(type)
                }
            }
        }

        private suspend fun loadType(type: BaseItemKind) {
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
            val sortAndDirection = libraryDisplayInfo?.sortAndDirection ?: SortAndDirection.DEFAULT
            val filter = libraryDisplayInfo?.filter ?: GetItemsFilter(favorite = true)

            val request =
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

            val pager = ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope, pageSize = 50).init()
            val viewOptions = libraryDisplayInfo?.viewOptions ?: type.defaultViewOptions

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
            if (pager.isNotEmpty()) {
                _state.update {
                    it.copy(
                        tabs =
                            it.tabs
                                .toMutableList()
                                .apply {
                                    add(type)
                                }.sortedBy { favoriteOptions.indexOf(it) },
                    )
                }
            }
        }

        fun libraryDisplayItemId(type: BaseItemKind): String =
            when (type) {
                BaseItemKind.MOVIE -> "movies"
                BaseItemKind.SERIES -> "series"
                BaseItemKind.EPISODE -> "episodes"
                BaseItemKind.VIDEO -> "videos"
                BaseItemKind.PLAYLIST -> "playlists"
                BaseItemKind.PERSON -> "people"
                else -> type.serialName
            }

        private fun collectionStateFor(type: BaseItemKind): CollectionFolderState? = state.value.favorites[type]

        fun updateSelectedTabIndex(newIndex: Int) {
            // TODO save to DB
            _state.update { it.copy(tabIndex = newIndex) }
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
                    // TODO use pager.refreshPagesAfter ??
                    loadType(type)
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
                    }
                }
            }

            override fun onFilterChange(
                newFilter: GetItemsFilter,
                recursive: Boolean,
            ) {
                viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
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
                    // TODO
                    0
                }

            override fun saveViewOptions(viewOptions: ViewOptions) {
                saveViewOptions(type, viewOptions)
            }
        }
    }

data class FavoritesPageState(
    val favorites: SnapshotStateMap<BaseItemKind, CollectionFolderState> = SnapshotStateMap(),
    val tabs: List<BaseItemKind> = emptyList(),
    // TODO
    val tabIndex: Int = 0,
) {
    val tabDetails: List<TabDetails> get() = tabs.map { TabDetails(formatTypeName(it)) }
}

val favoriteOptions =
    listOf(
        BaseItemKind.MOVIE,
        BaseItemKind.SERIES,
        BaseItemKind.EPISODE,
        BaseItemKind.VIDEO,
        BaseItemKind.PLAYLIST,
        BaseItemKind.PERSON,
    )
