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
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.formatTypeName
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.util.ApiRequestPager
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
        @Assisted private val itemId: UUID,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(itemId: UUID): CollectionViewModel
        }

        private val _state = MutableStateFlow(CollectionState())
        val state: StateFlow<CollectionState> = _state

        init {
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
                updateData()
            }
        }

        private fun updateData() {
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
            updateData()
        }

        fun changeViewOptions(viewOptions: CollectionViewOptions) {
            val shouldRefresh = _state.value.viewOptions.mixed != viewOptions.mixed
            _state.update { it.copy(viewOptions = viewOptions) }
            if (shouldRefresh) {
                updateData()
            }
        }

        suspend fun getPossibleFilterValues(filterBy: ItemFilterBy<*>): List<FilterValueOption> {
            TODO()
        }

        suspend fun letterPosition(letter: Char): Int {
            TODO()
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
