package com.github.damontecres.wholphin.ui.detail.collection

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.ThemeSongPlayer
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.formatTypeName
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.nav.Destination
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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID

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
        val mediaReportService: MediaReportService,
        @Assisted private val itemId: UUID,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(itemId: UUID): CollectionViewModel
        }

        private val _state = MutableStateFlow(CollectionState())
        val state: StateFlow<CollectionState> = _state

        init {
            addCloseable { release() }
            viewModelScope.launchDefault {
                val collection =
                    api.userLibraryApi
                        .getItem(itemId)
                        .content
                        .let { BaseItem(it, false) }

                // TODO fetch view options
                val viewOptions = CollectionViewOptions()
                _state.update {
                    it.copy(
                        collection = collection,
                        viewOptions = viewOptions,
                    )
                }
                updateData().join()
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

        private fun updateData() =
            viewModelScope.launchDefault {
                state
                    .map { Pair(it.sortAndDirection, it.viewOptions.mixed) }
                    .distinctUntilChanged()
                    .collectLatest { (sort, mixed) ->
                        _state.update { it.copy(loadingState = LoadingState.Loading) }
                        if (mixed) {
                            val result = fetchItems(sort, typesInCollection)
                            _state.update { it.copy(items = result) }
                        } else {
                            supervisorScope {
                                val jobs =
                                    typesInCollection.map { type ->
                                        async(Dispatchers.IO) {
                                            val title = context.getString(formatTypeName(type))
                                            val result =
                                                try {
                                                    val pager = fetchItems(sort, listOf(type))
                                                    // TODO view options
                                                    HomeRowLoadingState.Success(title, pager)
                                                } catch (ex: Exception) {
                                                    Timber.e(
                                                        ex,
                                                        "Error fetching %s for collection %s",
                                                        type,
                                                        itemId,
                                                    )
                                                    HomeRowLoadingState.Error(
                                                        title,
                                                        exception = ex,
                                                    )
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
                    }
            }

        private suspend fun fetchItems(
            sort: SortAndDirection,
            types: List<BaseItemKind>,
        ): ApiRequestPager<GetItemsRequest> {
            val request =
                GetItemsRequest(
                    userId = serverRepository.currentUser.value?.id,
                    parentId = itemId,
                    includeItemTypes = types,
                    recursive = true,
                    sortBy = listOf(sort.sort),
                    sortOrder = listOf(sort.direction),
                )
            return ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope).init()
        }

        fun changeSort(sortAndDirection: SortAndDirection) {
            _state.update { it.copy(sortAndDirection = sortAndDirection) }
            // TODO persist
            updateData()
        }

        fun changeFilter(filter: GetItemsFilter) {
            _state.update { it.copy(itemFilter = filter) }
            // TODO persist
            updateData()
        }

        fun changeViewOptions(viewOptions: CollectionViewOptions) {
            val shouldRefresh = _state.value.viewOptions.mixed != viewOptions.mixed
            _state.update { it.copy(viewOptions = viewOptions) }
            if (shouldRefresh) {
                updateData()
            }
        }

        suspend fun getPossibleFilterValues(filterOption: ItemFilterBy<*>): List<FilterValueOption> =
            FilterUtils.getFilterOptionValues(
                api,
                serverRepository.currentUser.value?.id,
                itemId,
                filterOption,
            )

        suspend fun letterPosition(letter: Char): Int {
            TODO()
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
            position?.let {
                // TODO
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
            position: RowColumn?,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            if (position != null) {
                // TODO
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
                // TODO
            }
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchDefault {
                backdropService.submit(item)
            }
        }

        companion object {
            val typesInCollection =
                listOf(
                    BaseItemKind.MOVIE,
                    BaseItemKind.SERIES,
                    BaseItemKind.EPISODE,
                    BaseItemKind.BOX_SET,
                )
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
)
