package com.github.damontecres.wholphin.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.PageConfig
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.CachedPageData
import com.github.damontecres.wholphin.services.CustomPageRowsCache
import com.github.damontecres.wholphin.services.HomeSettingsService
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.ServerPluginApi
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.tvAccess
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jellyfin.sdk.model.api.UserDto
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class CustomPageViewModel
    @Inject
    constructor(
        val navigationManager: NavigationManager,
        private val serverRepository: ServerRepository,
        private val serverPluginApi: ServerPluginApi,
        private val homeSettingsService: HomeSettingsService,
        private val navDrawerService: NavDrawerService,
        private val userPreferencesService: UserPreferencesService,
        private val backdropService: BackdropService,
        private val rowsCache: CustomPageRowsCache,
    ) : ViewModel() {
        private val _state = MutableStateFlow(CustomPageState.EMPTY)
        val state: StateFlow<CustomPageState> = _state

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }

        fun load(pageId: String) {
            viewModelScope.launchIO {
                val userDto = serverRepository.currentUserDto.value
                if (userDto == null) {
                    _state.update { it.copy(loading = LoadingState.Error("No active user")) }
                    return@launchIO
                }

                // If we already have this page cached, show it immediately and refresh silently.
                val cached = rowsCache.get(userDto.id, pageId)
                if (cached != null) {
                    _state.update {
                        it.copy(
                            page = cached.page,
                            rows = cached.rows,
                            loading = LoadingState.Success,
                        )
                    }
                } else {
                    _state.update { it.copy(loading = LoadingState.Loading) }
                }

                refresh(userDto, pageId, hadCache = cached != null)
            }
        }

        private suspend fun refresh(
            userDto: UserDto,
            pageId: String,
            hadCache: Boolean,
        ) {
            val page =
                try {
                    serverPluginApi.fetchPage(pageId)
                } catch (ex: Exception) {
                    Timber.w(ex, "Failed to load custom page %s", pageId)
                    if (!hadCache) {
                        _state.update { it.copy(loading = LoadingState.Error("Could not load page", ex)) }
                    }
                    return
                }
            if (page == null) {
                if (!hadCache) {
                    _state.update { it.copy(loading = LoadingState.Error("Page not found")) }
                }
                return
            }

            val prefs = userPreferencesService.getCurrent().appPreferences.homePagePreferences
            val libraries = navDrawerService.getAllUserLibraries(userDto.id, userDto.tvAccess)
            val semaphore = Semaphore(4)

            if (!hadCache) {
                _state.update {
                    it.copy(
                        page = page,
                        rows = List(page.rows.size) { HomeRowLoadingState.Pending("") },
                        loading = LoadingState.Success,
                    )
                }
            }

            val deferred =
                page.rows.map { row ->
                    viewModelScope.async(Dispatchers.IO) {
                        semaphore.withPermit {
                            try {
                                homeSettingsService.fetchDataForRow(
                                    row = row,
                                    scope = viewModelScope,
                                    prefs = prefs,
                                    userDto = userDto,
                                    libraries = libraries,
                                    limit = prefs.maxItemsPerRow,
                                    isRefresh = hadCache,
                                )
                            } catch (ex: Exception) {
                                Timber.w(ex, "Error fetching row in custom page %s", pageId)
                                HomeRowLoadingState.Error("", exception = ex)
                            }
                        }
                    }
                }
            val rows = deferred.awaitAll()
            _state.update { it.copy(page = page, rows = rows, loading = LoadingState.Success) }
            rowsCache.put(userDto.id, pageId, CachedPageData(page, rows))
        }
    }

data class CustomPageState(
    val page: PageConfig?,
    val rows: List<HomeRowLoadingState>,
    val loading: LoadingState,
) {
    companion object {
        val EMPTY = CustomPageState(null, emptyList(), LoadingState.Pending)
    }
}
