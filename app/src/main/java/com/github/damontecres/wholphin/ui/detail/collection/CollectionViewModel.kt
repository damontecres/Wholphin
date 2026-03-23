package com.github.damontecres.wholphin.ui.detail.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import java.util.UUID

@HiltViewModel(assistedFactory = CollectionViewModel.Factory::class)
class CollectionViewModel
    @AssistedInject
    constructor(
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

                viewModelScope.launchIO {
                    state
                        .map { Pair(it.sortAndDirection, it.viewOptions.mixed) }
                        .distinctUntilChanged()
                        .collectLatest { (sort, mixed) ->
                            _state.update { it.copy(loadingState = LoadingState.Loading) }
                            if (mixed) {
                                // TODO types
                                val result =
                                    fetchItems(
                                        sort,
                                        listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                    )
                                _state.update { it.copy(items = result) }
                            } else {
                                val jobs = mutableListOf<Deferred<Unit>>()
                                supervisorScope {
                                    async(Dispatchers.IO) {
                                        val result = fetchItems(sort, listOf(BaseItemKind.MOVIE))
                                        _state.update {
                                            it.copy(
                                                movies =
                                                    HomeRowLoadingState.Success(
                                                        "",
                                                        result,
                                                    ),
                                            )
                                        }
                                    }.also { jobs.add(it) }
                                    jobs.awaitAll()
                                }
                            }
                            _state.update { it.copy(loadingState = LoadingState.Success) }
                        }
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
        }

        fun changeViewOptions(viewOptions: CollectionViewOptions) {
            _state.update { it.copy(viewOptions = viewOptions) }
        }

        suspend fun getPossibleFilterValues(filterBy: ItemFilterBy<*>): List<FilterValueOption> {
            TODO()
        }

        suspend fun letterPosition(letter: Char): Int {
            TODO()
        }
    }

data class CollectionState(
    val loadingState: LoadingState = LoadingState.Pending,
    val collection: BaseItem? = null,
    val sortAndDirection: SortAndDirection = SortAndDirection.DEFAULT,
    val itemFilter: GetItemsFilter = GetItemsFilter(),
    val viewOptions: CollectionViewOptions = CollectionViewOptions(),
    val items: List<BaseItem?> = emptyList(),
    val movies: HomeRowLoadingState = HomeRowLoadingState.Pending(""),
    val series: HomeRowLoadingState = HomeRowLoadingState.Pending(""),
    val episodes: HomeRowLoadingState = HomeRowLoadingState.Pending(""),
    val collections: HomeRowLoadingState = HomeRowLoadingState.Pending(""),
)
