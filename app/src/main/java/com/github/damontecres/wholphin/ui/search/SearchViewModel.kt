package com.github.damontecres.wholphin.ui.search

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateMap
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
import com.github.damontecres.wholphin.services.LiveTvService
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.services.tvAccess
import com.github.damontecres.wholphin.ui.ProgramItemFields
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.components.ContextMenuProvider
import com.github.damontecres.wholphin.ui.components.VoiceInputManager
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.livetv.ProgramDialogState
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.util.DataLoadingState
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
import org.jellyfin.sdk.api.client.extensions.artistsApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.personsApi
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
        private val liveTvService: LiveTvService,
    ) : ViewModel(),
        ContextMenuProvider {
        val seerrActive = seerrService.active

        private val _state = MutableStateFlow(SearchState())
        val state: StateFlow<SearchState> = _state
        val position = MutableStateFlow(RowColumn(0, 0))

        private var currentQuery: String? = null
        private var combinedMode = false

        private val _programDialogState = MutableStateFlow(ProgramDialogState())
        val programDialogState: StateFlow<ProgramDialogState> = _programDialogState

        init {
            init()
        }

        private fun init() {
            _state.update { SearchState() }
            viewModelScope.launchDefault {
                val tvAccess = serverRepository.currentUserDto?.tvAccess == true
                val searchableTypes =
                    allSearchableTypes.filter {
                        when (it) {
                            // Remove live tv search if user doesn't have access
                            BaseItemKind.TV_CHANNEL,
                            BaseItemKind.LIVE_TV_PROGRAM,
                            BaseItemKind.TV_PROGRAM,
                            BaseItemKind.PROGRAM,
                            -> tvAccess

                            else -> true
                        }
                    }
                _state.update {
                    it.copy(
                        searchableTypes = searchableTypes,
                        results =
                            SnapshotStateMap<BaseItemKind, SearchResult>().apply {
                                searchableTypes.forEach { put(it, SearchResult.NoQuery) }
                            },
                    )
                }
            }
        }

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
                _state.update {
                    it.copy(
                        results =
                            SnapshotStateMap<BaseItemKind, SearchResult>().apply {
                                it.searchableTypes.forEach { put(it, SearchResult.NoQuery) }
                            },
                    )
                }
                if (combined) {
                    searchCombined(query)
                } else {
                    state.value.searchableTypes.forEach { type ->
                        searchType(query, type)
                    }
                }
                searchSeerr(query)
            } else {
                init()
            }
        }

        private fun searchType(
            query: String,
            type: BaseItemKind,
        ) {
            viewModelScope.launchIO {
                try {
                    val items: List<BaseItem> =
                        when (type) {
                            BaseItemKind.LIVE_TV_PROGRAM -> {
                                val request =
                                    GetItemsRequest(
                                        searchTerm = query,
                                        recursive = true,
                                        includeItemTypes = listOf(type),
                                        fields = ProgramItemFields,
                                        limit = SEARCH_LIMIT,
                                        enableTotalRecordCount = false,
                                    )
                                api.itemsApi.getItems(request).toBaseItems(api, false)
                            }

                            BaseItemKind.MUSIC_ARTIST -> {
                                api.artistsApi
                                    .getArtists(
                                        searchTerm = query,
                                        fields = SlimItemFields,
                                        limit = SEARCH_LIMIT,
                                        enableTotalRecordCount = false,
                                    ).toBaseItems(api, false)
                            }

                            BaseItemKind.PERSON -> {
                                api.personsApi
                                    .getPersons(
                                        searchTerm = query,
                                        fields = SlimItemFields,
                                        limit = SEARCH_LIMIT,
                                    ).toBaseItems(api, false)
                            }

                            else -> {
                                val request =
                                    GetItemsRequest(
                                        searchTerm = query,
                                        recursive = true,
                                        includeItemTypes = listOf(type),
                                        fields = SlimItemFields,
                                        limit = SEARCH_LIMIT,
                                        enableTotalRecordCount = false,
                                    )
                                api.itemsApi.getItems(request).toBaseItems(api, false)
                            }
                        }
                    val sorted =
                        items.sortedWith(
                            compareBy<BaseItem> { SearchRelevance.score(it, query) }
                                .thenBy { it.sortName },
                        )
                    Timber.v("Search finished for %s, %s results", type, sorted.size)
                    _state.value.results[type] = SearchResult.Success(sorted)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception searching for $type")
                    _state.value.results[type] = SearchResult.Error(ex)
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
                        state.value.searchableTypes.getOrNull(position.row)?.let {
                            state.value.results[it]
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
                    if (combinedMode) {
                        _state.update {
                            it.copy(
                                combinedResults = newList,
                            )
                        }
                    } else {
                        state.value.searchableTypes.getOrNull(position.row)?.let { type ->
                            state.value.results[type] = newList
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

        fun fetchProgramForDialog(programId: UUID) {
            _programDialogState.update { it.copy(loading = DataLoadingState.Loading) }
            viewModelScope.launchDefault {
                try {
                    val result = liveTvService.fetchProgramForDialog(programId)
                    _programDialogState.update { it.copy(loading = DataLoadingState.Success(result)) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching program $programId")
                    _programDialogState.update { it.copy(loading = DataLoadingState.Error(ex)) }
                }
            }
        }

        fun cancelRecording(
            series: Boolean,
            timerId: String?,
        ) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                try {
                    val result = liveTvService.cancelRecording(series, timerId)
                    // TODO update program card?
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error canceling timer %s, series=%s", timerId, series)
                    showToast(context, "Error: ${ex.localizedMessage}")
                }
            }
        }

        fun record(
            programId: UUID,
            series: Boolean,
        ) {
            viewModelScope.launchIO {
                try {
                    liveTvService.record(programId, series)
                    // TODO update program card?
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error recording %s, series=%s", programId, series)
                    showToast(context, "Error: ${ex.localizedMessage}")
                }
            }
        }
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
    val results: SnapshotStateMap<BaseItemKind, SearchResult> = SnapshotStateMap(),
    val seerrResults: SearchResult = SearchResult.NoQuery,
    val combinedResults: SearchResult = SearchResult.NoQuery,
    val searchableTypes: List<BaseItemKind> = emptyList(),
)

private val allSearchableTypes =
    listOf(
        BaseItemKind.MOVIE,
        BaseItemKind.SERIES,
        BaseItemKind.EPISODE,
        BaseItemKind.BOX_SET,
        BaseItemKind.PERSON,
        BaseItemKind.TV_CHANNEL,
        BaseItemKind.LIVE_TV_PROGRAM,
        BaseItemKind.MUSIC_ALBUM,
        BaseItemKind.MUSIC_ARTIST,
        BaseItemKind.AUDIO,
        BaseItemKind.MUSIC_VIDEO,
        BaseItemKind.PLAYLIST,
        BaseItemKind.VIDEO,
        BaseItemKind.PHOTO,
        BaseItemKind.PHOTO_ALBUM,
    )

private const val SEARCH_LIMIT = 50
