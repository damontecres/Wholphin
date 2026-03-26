package com.github.damontecres.wholphin.ui.detail.collection

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
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
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.KeyValueService
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.ThemeSongPlayer
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.collectLatestIn
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.formatTypeName
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.ui.util.FilterUtils
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = CollectionViewModel.Factory::class)
class CollectionViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val navigationManager: NavigationManager,
        private val preferencesService: UserPreferencesService,
        private val themeSongPlayer: ThemeSongPlayer,
        private val mediaManagementService: MediaManagementService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val backdropService: BackdropService,
        private val keyValueService: KeyValueService,
        private val libraryDisplayInfoDao: LibraryDisplayInfoDao,
        private val imageUrlService: ImageUrlService,
        val mediaReportService: MediaReportService,
        @Assisted private val itemId: UUID,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(itemId: UUID): CollectionViewModel
        }

        private val viewOptionsFlow =
            serverRepository.currentUser
                .asFlow()
                .filterNotNull()
                .flatMapLatest {
                    keyValueService.get(it.id, VIEW_OPTIONS_KEY, CollectionViewOptions())
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), CollectionViewOptions())

        private val libraryDisplayInfoFlow =
            serverRepository.currentUser
                .asFlow()
                .filterNotNull()
                .flatMapLatest {
                    libraryDisplayInfoDao.getItemAsFlow(it.rowId, itemId.toServerString())
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

        private val _state = MutableStateFlow(CollectionState())
        val state: StateFlow<CollectionState> = _state

        init {
            addCloseable { release() }
            // Get global per-user view options for collections
            viewOptionsFlow.collectLatestIn(viewModelScope) { viewOptions ->
                Timber.v("Updated viewOptions")
                _state.update {
                    it.copy(viewOptions = viewOptions)
                }
            }
            libraryDisplayInfoFlow
                .filterNotNull()
                .collectLatestIn(viewModelScope) { libraryDisplayInfo ->
                    Timber.v("Updated libraryDisplayInfo")
                    _state.update {
                        it.copy(
                            itemFilter = libraryDisplayInfo.filter,
                            sortAndDirection = libraryDisplayInfo.sortAndDirection,
                        )
                    }
                }
            viewModelScope.launchDefault {
                val collection =
                    api.userLibraryApi
                        .getItem(itemId)
                        .content
                        .let { BaseItem(it, false) }
                backdropService.submit(collection)
                val logoImageUrl = null
                // TODO add logo back
//                    if (ImageType.LOGO in collection.data.imageTags.orEmpty()) {
//                        imageUrlService.getItemImageUrl(collection, ImageType.LOGO)
//                    } else {
//                        null
//                    }
                _state.update {
                    it.copy(
                        collection = collection,
                        logoImageUrl = logoImageUrl,
                    )
                }
                listenForStateUpdates()
                themeSongPlayer.playThemeFor(
                    itemId,
                    preferencesService
                        .getCurrent()
                        .appPreferences.interfacePreferences.playThemeSongs,
                )
            }
        }

        fun release() {
            themeSongPlayer.stop()
        }

        /**
         * Collects on [state] and fetches data when needed
         */
        private fun listenForStateUpdates() =
            viewModelScope.launchDefault {
                state
                    .map {
                        Triple(
                            it.sortAndDirection,
                            it.itemFilter,
                            it.viewOptions.separateTypes,
                        )
                    }.distinctUntilChanged()
                    .collectLatest { (sort, filter, separateTypes) ->
                        try {
                            updateData(sort, filter, separateTypes)
                        } catch (ex: Exception) {
                            Timber.e(
                                ex,
                                "Error fetching data for collection %s",
                                itemId,
                            )
                            _state.update { it.copy(loadingState = LoadingState.Error(ex)) }
                        }
                    }
            }

        private suspend fun updateData(
            sort: SortAndDirection,
            filter: GetItemsFilter,
            separateTypes: Boolean,
        ) {
            Timber.d("Begin updateData for %s", itemId)
            _state.update { it.copy(loadingState = LoadingState.Loading) }
            if (!separateTypes) {
                val result = fetchItems(sort, filter, typesInCollection)
                _state.update { it.copy(items = result) }
            } else {
                supervisorScope {
                    val jobs =
                        typesInCollection.map { type ->
                            async(Dispatchers.IO) {
                                val title = context.getString(formatTypeName(type))
                                val result =
                                    try {
                                        val pager = fetchItems(sort, filter, listOf(type))
                                        HomeRowLoadingState.Success(title, pager)
                                    } catch (ex: Exception) {
                                        Timber.e(
                                            ex,
                                            "Error fetching %s for collection %s",
                                            type,
                                            itemId,
                                        )
                                        HomeRowLoadingState.Error(title, exception = ex)
                                    }
                                type to result
                            }
                        }
                    jobs.forEach { job ->
                        val (type, row) = job.await()
                        _state.update {
                            val separateItems =
                                it.separateItems.toMutableMap().apply {
                                    put(type, row)
                                }
                            it.copy(separateItems = separateItems)
                        }
                    }
                }
            }
            _state.update { it.copy(loadingState = LoadingState.Success) }
            Timber.d("End updateData for %s", itemId)
        }

        private suspend fun fetchItems(
            sort: SortAndDirection?,
            filter: GetItemsFilter?,
            types: List<BaseItemKind>,
        ): ApiRequestPager<GetItemsRequest> {
            val request = createGetItemsRequest(sort, filter, types)
            val useSeriesForPrimary = !state.value.viewOptions.separateTypes
            return ApiRequestPager(
                api,
                request,
                GetItemsRequestHandler,
                viewModelScope,
                useSeriesForPrimary = useSeriesForPrimary,
            ).init()
        }

        private fun createGetItemsRequest(
            sort: SortAndDirection?,
            filter: GetItemsFilter?,
            types: List<BaseItemKind>,
        ): GetItemsRequest {
            val includeItemTypes: List<BaseItemKind>?
            val excludeItemTypes: List<BaseItemKind>?
            // Workaround for https://github.com/jellyfin/jellyfin/issues/16454
            if (types.size == 1 && types.first() == BaseItemKind.BOX_SET) {
                includeItemTypes = null
                excludeItemTypes =
                    BaseItemKind.entries
                        .toMutableList()
                        .apply { remove(BaseItemKind.BOX_SET) }
            } else {
                includeItemTypes = types
                excludeItemTypes = null
            }
            val request =
                GetItemsRequest(
                    userId = serverRepository.currentUser.value?.id,
                    parentId = itemId,
                    includeItemTypes = includeItemTypes,
                    excludeItemTypes = excludeItemTypes,
                    recursive = false,
                    sortBy = sort?.let { listOf(sort.sort) },
                    sortOrder = sort?.let { listOf(sort.direction) },
                    fields = SlimItemFields,
                ).let {
                    filter?.applyTo(it, false) ?: it
                }
            return request
        }

        fun changeSort(sortAndDirection: SortAndDirection) {
            viewModelScope.launchIO {
                val user = serverRepository.currentUser.value
                val state = _state.value
                if (user != null) {
                    libraryDisplayInfoDao.saveItem(
                        LibraryDisplayInfo(
                            user = user,
                            itemId = itemId,
                            sort = sortAndDirection.sort,
                            direction = sortAndDirection.direction,
                            filter = state.itemFilter,
                            viewOptions = null,
                        ),
                    )
                }
            }
        }

        fun changeFilter(filter: GetItemsFilter) {
            viewModelScope.launchIO {
                val user = serverRepository.currentUser.value
                val state = _state.value
                if (user != null) {
                    libraryDisplayInfoDao.saveItem(
                        LibraryDisplayInfo(
                            user = user,
                            itemId = itemId,
                            sort = state.sortAndDirection.sort,
                            direction = state.sortAndDirection.direction,
                            filter = filter,
                            viewOptions = null,
                        ),
                    )
                }
            }
        }

        fun changeViewOptions(viewOptions: CollectionViewOptions) {
            viewModelScope.launchIO {
                if (!viewOptions.cardViewOptions.showDetails) {
                    val collection = state.value.collection
                    if (collection != null) {
                        backdropService.submit(collection)
                    } else {
                        backdropService.clearBackdrop()
                    }
                }
                serverRepository.currentUser.value?.id?.let { userId ->
                    keyValueService.save(userId, VIEW_OPTIONS_KEY, viewOptions)
                }
            }
        }

        suspend fun getPossibleFilterValues(filterOption: ItemFilterBy<*>): List<FilterValueOption> =
            FilterUtils.getFilterOptionValues(
                api,
                serverRepository.currentUser.value?.id,
                itemId,
                filterOption,
            )

        suspend fun letterPosition(letter: Char): Int =
            withContext(Dispatchers.IO) {
                val sort = state.value.sortAndDirection
                val filter = state.value.itemFilter
                val request =
                    createGetItemsRequest(
                        sort = sort,
                        filter = filter,
                        types = typesInCollection,
                    ).copy(
                        enableImageTypes = null,
                        fields = null,
                        nameLessThan = letter.toString(),
                        limit = 0,
                        enableTotalRecordCount = true,
                    )
                val result by GetItemsRequestHandler.execute(api, request)
                result.totalRecordCount
            }

        fun navigate(destination: Destination) {
            release()
            navigationManager.navigateTo(destination)
        }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
            position: RowColumn?,
        ) = viewModelScope.launch(Dispatchers.IO + ExceptionHandler()) {
            favoriteWatchManager.setWatched(itemId, played)
            if (position != null) {
                refreshItem(itemId, position, false)
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
            position: RowColumn?,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            if (position != null) {
                refreshItem(itemId, position, false)
            }
        }

        fun canDelete(
            item: BaseItem,
            appPreferences: AppPreferences,
        ): Boolean = mediaManagementService.canDelete(item, appPreferences)

        fun deleteItem(
            item: BaseItem,
            position: RowColumn?,
        ) {
            deleteItem(context, mediaManagementService, item) {
                viewModelScope.launchIO {
                    if (position != null) {
                        refreshItem(itemId, position, true)
                    }
                }
            }
        }

        private suspend fun refreshItem(
            itemId: UUID,
            position: RowColumn,
            isDelete: Boolean,
        ) {
            state.value.let { state ->
                val items =
                    if (state.viewOptions.separateTypes) {
                        val key =
                            state.separateItems.keys
                                .toList()
                                .getOrNull(position.row)
                        (state.separateItems[key] as? HomeRowLoadingState.Success)?.items as? ApiRequestPager<*>
                    } else {
                        state.items as? ApiRequestPager<*>
                    }
                if (isDelete) {
                    items?.refreshPagesAfter(position.column)
                } else {
                    items?.refreshItem(position.column, itemId)
                }
            }
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchDefault {
                val collection = state.value.collection
                if (item.id == collection?.id) {
                    // Always show the collection's backdrop if requested
                    backdropService.submit(collection)
                } else if (state.value.viewOptions.cardViewOptions.showDetails) {
                    backdropService.submit(item)
                } else {
                    backdropService.clearBackdrop()
                }
            }
        }

        companion object {
            val typesInCollection =
                listOf(
                    BaseItemKind.MOVIE,
                    BaseItemKind.SERIES,
                    BaseItemKind.EPISODE,
                    BaseItemKind.VIDEO,
                    BaseItemKind.BOX_SET,
                )

            const val VIEW_OPTIONS_KEY = "CollectionViewOptions"
        }
    }

@Stable
data class CollectionState(
    val loadingState: LoadingState = LoadingState.Pending,
    val collection: BaseItem? = null,
    val sortAndDirection: SortAndDirection = SortAndDirection.DEFAULT,
    val itemFilter: GetItemsFilter = GetItemsFilter(),
    val viewOptions: CollectionViewOptions = CollectionViewOptions(),
    val items: List<BaseItem?> = emptyList(),
    val separateItems: Map<BaseItemKind, HomeRowLoadingState> = emptyMap(),
    val logoImageUrl: String? = null,
)
