package com.github.damontecres.wholphin.ui.detail.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.components.VoiceInputManager
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.SearchResult
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber

@HiltViewModel(assistedFactory = SearchForViewModel.Factory::class)
class SearchForViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
        val voiceInputManager: VoiceInputManager,
        @Assisted val searchType: BaseItemKind,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(searchType: BaseItemKind): SearchForViewModel
        }

        val state = MutableStateFlow(SearchForState())

        init {
            state.value = SearchForState()
            viewModelScope.launchIO {
                try {
                    val request =
                        GetItemsRequest(
                            userId = serverRepository.currentUser?.id,
                            includeItemTypes = listOf(searchType),
                            recursive = true,
                            fields = SlimItemFields,
                            sortBy = listOf(ItemSortBy.DATE_LAST_CONTENT_ADDED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = 25,
                        )
                    val recent = api.itemsApi.getItems(request).toBaseItems(api, false)
                    state.update {
                        it.copy(recent = SearchResult.Success(recent))
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching recent %s", searchType)
                    state.update {
                        it.copy(recent = SearchResult.Error(ex))
                    }
                }
            }
        }

        fun search(
            searchType: BaseItemKind,
            query: String,
        ) {
            viewModelScope.launchIO {
                if (state.value.query != query) {
                    if (query.isBlank()) {
                        state.update { it.copy(query = query, results = SearchResult.NoQuery) }
                        return@launchIO
                    }
                    state.update { it.copy(query = query, results = SearchResult.NoQuery) }
                    try {
                        val request =
                            GetItemsRequest(
                                userId = serverRepository.currentUser?.id,
                                searchTerm = query,
                                includeItemTypes = listOf(searchType),
                                recursive = true,
                                fields = SlimItemFields,
                            )
                        val pager = ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope).init()
                        state.update {
                            it.copy(
                                query = query,
                                results = SearchResult.Success(pager),
                            )
                        }
                    } catch (ex: Exception) {
                        Timber.e(ex)
                        state.update { it.copy(query = query, results = SearchResult.Error(ex)) }
                    }
                }
            }
        }
    }

data class SearchForState(
    val query: String = "",
    val recent: SearchResult = SearchResult.Searching,
    val results: SearchResult = SearchResult.NoQuery,
)
