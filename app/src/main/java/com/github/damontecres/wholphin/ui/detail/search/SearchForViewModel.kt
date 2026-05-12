package com.github.damontecres.wholphin.ui.detail.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.components.VoiceInputManager
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.SearchResult
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SearchForViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
        val voiceInputManager: VoiceInputManager,
    ) : ViewModel() {
        val state = MutableStateFlow(SearchForState())

        init {
            state.value = SearchForState()
        }

        fun search(
            searchType: BaseItemKind,
            query: String,
        ) {
            viewModelScope.launchIO {
                if (state.value.query != query) {
                    if (query.isBlank()) {
                        state.update { SearchForState(query, SearchResult.NoQuery) }
                        return@launchIO
                    }
                    state.update { SearchForState(query, SearchResult.Searching) }
                    try {
                        val request =
                            GetItemsRequest(
                                userId = serverRepository.currentUser.value?.id,
                                searchTerm = query,
                                includeItemTypes = listOf(searchType),
                                recursive = true,
                                fields = SlimItemFields,
                            )
                        val pager = ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope).init()
                        state.update { SearchForState(query, SearchResult.Success(pager)) }
                    } catch (ex: Exception) {
                        Timber.e(ex)
                        state.update { SearchForState(query, SearchResult.Error(ex)) }
                    }
                }
            }
        }
    }

data class SearchForState(
    val query: String = "",
    val results: SearchResult = SearchResult.NoQuery,
)
