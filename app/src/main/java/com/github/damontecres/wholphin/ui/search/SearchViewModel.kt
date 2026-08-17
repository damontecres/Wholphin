package com.github.damontecres.wholphin.ui.search

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.updateSearchPreferences
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.components.ContextMenuProvider
import com.github.damontecres.wholphin.ui.components.VoiceInputManager
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.SearchRelevance
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val api: ApiClient,
        val navigationManager: NavigationManager,
        private val appPreferences: DataStore<AppPreferences>,
        private val seerrService: SeerrService,
        val voiceInputManager: VoiceInputManager,
        val userPreferencesService: UserPreferencesService,
        private val serverRepository: ServerRepository,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val mediaManagementService: MediaManagementService,
        private val mediaReportService: MediaReportService,
    ) : ViewModel(),
        ContextMenuProvider {
        val seerrActive = seerrService.active

        private val _state = MutableStateFlow(SearchState())
        val state: StateFlow<SearchState> = _state
        val position = MutableStateFlow(RowColumn(0, 0))

        private var currentQuery: String? = null
        private var combinedMode = false

        fun search(
            query: String?,
            combined: Boolean = false,
        ) {
            if (currentQuery == query && combinedMode == combined) {
                return
            }
            currentQuery = query
            combinedMode = combined
            if (query.isNotNullOrBlank()) {
                _state.update { SearchState.searchingState }
                if (combined) {
                    searchCombined(query)
                } else {
                    searchInternal(
                        query,
                        BaseItemKind.MOVIE,
                    ) { result, state -> state.copy(movies = result) }
                    searchInternal(
                        query,
                        BaseItemKind.SERIES,
                    ) { result, state -> state.copy(series = result) }
                    searchInternal(query, BaseItemKind.EPISODE) { result, state ->
                        state.copy(
                            episodes = result,
                        )
                    }
                    searchInternal(query, BaseItemKind.BOX_SET) { result, state ->
                        state.copy(
                            collections = result,
                        )
                    }
                    searchInternal(query, BaseItemKind.MUSIC_ALBUM) { result, state ->
                        state.copy(
                            albums = result,
                        )
                    }
                    searchInternal(query, BaseItemKind.MUSIC_ARTIST) { result, state ->
                        state.copy(
                            artists = result,
                        )
                    }
                    searchInternal(
                        query,
                        BaseItemKind.AUDIO,
                    ) { result, state -> state.copy(songs = result) }
                }
                searchSeerr(query)
            } else {
                _state.update { SearchState() }
            }
        }

        private fun searchInternal(
            query: String,
            type: BaseItemKind,
            update: (SearchResult, SearchState) -> SearchState,
        ) {
            viewModelScope.launchIO {
                try {
                    val request =
                        GetItemsRequest(
                            searchTerm = query,
                            recursive = true,
                            includeItemTypes = listOf(type),
                            fields = SlimItemFields,
                            limit = 50,
                        )
                    val result = api.itemsApi.getItems(request).content
                    val items =
                        result.items.map {
                            BaseItem(it, false)
                        }
                    val sorted =
                        items.sortedWith(
                            compareBy<BaseItem> { SearchRelevance.score(it, query) }
                                .thenBy { it.sortName },
                        )
                    _state.update { update.invoke(SearchResult.Success(sorted), it) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception searching for $type")
                    _state.update { update.invoke(SearchResult.Error(ex), it) }
                }
            }
        }

        private fun searchCombined(query: String) {
            viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
                try {
                    val request =
                        GetItemsRequest(
                            searchTerm = query,
                            recursive = true,
                            includeItemTypes =
                                listOf(
                                    BaseItemKind.MOVIE,
                                    BaseItemKind.SERIES,
                                    BaseItemKind.BOX_SET,
                                ),
                            fields = SlimItemFields,
                            limit = 50,
                        )

                    val result = api.itemsApi.getItems(request).content
                    val items =
                        result.items.map {
                            BaseItem(it, false)
                        }
                    val sorted =
                        items.sortedWith(
                            compareBy<BaseItem> { SearchRelevance.score(it, query) }
                                .thenBy { it.name ?: "" },
                        )
                    _state.update { it.copy(combinedResults = SearchResult.Success(sorted)) }
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception in combined search")
                    _state.update { it.copy(combinedResults = SearchResult.Error(ex)) }
                }
            }
        }

        fun setCombinedResults(enabled: Boolean) {
            viewModelScope.launchIO {
                appPreferences.updateData {
                    it.updateSearchPreferences {
                        combinedSearchResults = enabled
                    }
                }
            }
        }

        fun setVoiceSearchButtonVisible(visible: Boolean) {
            viewModelScope.launchIO {
                appPreferences.updateData {
                    it.updateSearchPreferences {
                        showVoiceSearchButton = visible
                    }
                }
            }
        }

        private fun searchSeerr(query: String) {
            viewModelScope.launchIO {
                if (seerrService.active.first()) {
                    _state.update { it.copy(seerrResults = SearchResult.Searching) }
                    val results =
                        seerrService
                            .search(query)
                            .map { seerrService.createDiscoverItem(it) }
                            .filter { it.type == SeerrItemType.MOVIE || it.type == SeerrItemType.TV }
                    _state.update { it.copy(seerrResults = SearchResult.SuccessSeerr(results)) }
                }
            }
        }

        init {
            addCloseable(voiceInputManager)
        }

        override fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }

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
                    refreshItem(item.id)
                }
            }
        }

        private suspend fun refreshItem(itemId: UUID) {
            try {
                val position = position.value
                val searchResult =
                    if (combinedMode) {
                        state.value.combinedResults
                    } else {
                        when (position.row) {
                            MOVIE_ROW -> state.value.movies
                            SERIES_ROW -> state.value.series
                            EPISODE_ROW -> state.value.episodes
                            COLLECTION_ROW -> state.value.collections
                            ALBUM_ROW -> state.value.albums
                            ARTIST_ROW -> state.value.artists
                            SONG_ROW -> state.value.songs
                            SEERR_ROW -> null
                            else -> null
                        }
                    } ?: return
                val items = (searchResult as? SearchResult.Success)?.items ?: return

                Timber.v("Item refresh: position=%s", position)
                val item = items.getOrNull(position.column)
                // Exact item deleted (eg a movie) or deleted item was within the series
                if (item != null && item.id == itemId) {
                    val newItem =
                        api.userLibraryApi
                            .getItem(item.id)
                            .content
                            .let { BaseItem(it) }
                    val newList =
                        SearchResult.Success(
                            items.toMutableList().apply {
                                set(position.column, newItem)
                            },
                        )
                    _state.update {
                        if (combinedMode) {
                            it.copy(
                                combinedResults = newList,
                            )
                        } else {
                            when (position.row) {
                                MOVIE_ROW -> it.copy(movies = newList)
                                SERIES_ROW -> it.copy(series = newList)
                                EPISODE_ROW -> it.copy(episodes = newList)
                                COLLECTION_ROW -> it.copy(collections = newList)
                                ALBUM_ROW -> it.copy(albums = newList)
                                ARTIST_ROW -> it.copy(artists = newList)
                                SONG_ROW -> it.copy(songs = newList)
                                SEERR_ROW -> it
                                else -> it
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Error refreshing item %s", itemId)
                showToast(context, "Error refreshing")
            }
        }

        override fun setWatched(
            position: Int,
            itemId: UUID,
            played: Boolean,
        ) {
            viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
                favoriteWatchManager.setWatched(itemId, played)
                refreshItem(itemId)
            }
        }

        override fun setFavorite(
            position: Int,
            itemId: UUID,
            favorite: Boolean,
        ) {
            viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
                favoriteWatchManager.setFavorite(itemId, favorite)
                refreshItem(itemId)
            }
        }

        override fun isAdministrator(): Boolean = serverRepository.currentUserDto?.policy?.isAdministrator == true

        override fun sendReportFor(itemId: UUID) = mediaReportService.sendReportFor(itemId)
    }

sealed interface SearchResult {
    data object NoQuery : SearchResult

    data object Searching : SearchResult

    data class Error(
        val ex: Exception,
    ) : SearchResult

    data class Success(
        val items: List<BaseItem?>,
    ) : SearchResult

    data class SuccessSeerr(
        val items: List<DiscoverItem>,
    ) : SearchResult
}

data class SearchState(
    val movies: SearchResult = SearchResult.NoQuery,
    val series: SearchResult = SearchResult.NoQuery,
    val episodes: SearchResult = SearchResult.NoQuery,
    val collections: SearchResult = SearchResult.NoQuery,
    val albums: SearchResult = SearchResult.NoQuery,
    val artists: SearchResult = SearchResult.NoQuery,
    val songs: SearchResult = SearchResult.NoQuery,
    val seerrResults: SearchResult = SearchResult.NoQuery,
    val combinedResults: SearchResult = SearchResult.NoQuery,
) {
    companion object {
        val searchingState =
            SearchState(
                movies = SearchResult.Searching,
                series = SearchResult.Searching,
                episodes = SearchResult.Searching,
                collections = SearchResult.Searching,
                albums = SearchResult.Searching,
                artists = SearchResult.Searching,
                songs = SearchResult.Searching,
                seerrResults = SearchResult.Searching,
                combinedResults = SearchResult.Searching,
            )
    }
}
