package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.NavDrawerItemRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.HomePageResolvedSettings
import com.github.damontecres.wholphin.services.HomeSettingsService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.settings.Library
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val serverRepository: ServerRepository,
        private val navDrawerItemRepository: NavDrawerItemRepository,
        private val homeSettingsService: HomeSettingsService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val datePlayedService: DatePlayedService,
        private val backdropService: BackdropService,
        private val userPreferencesService: UserPreferencesService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HomeState.EMPTY)
        val state: StateFlow<HomeState> = _state

        init {
            datePlayedService.invalidateAll()
//            init()
        }

        fun init() {
            viewModelScope.launchIO {
                Timber.d("init HomeViewModel")
                try {
                    val preferences = userPreferencesService.getCurrent()
                    val prefs = preferences.appPreferences.homePagePreferences

                    val navDrawerItems =
                        navDrawerItemRepository
                            .getNavDrawerItems()
                    val libraries =
                        navDrawerItems
                            .filter { it is ServerNavDrawerItem }
                            .map {
                                it as ServerNavDrawerItem
                                Library(it.itemId, it.name, it.type)
                            }
                    serverRepository.currentUserDto.value?.let { userDto ->
                        val settings =
                            homeSettingsService.currentSettings.first { it != HomePageResolvedSettings.EMPTY }
                        val state = state.value

                        // Refreshing if a load has already occurred and the rows haven't significantly changed
                        val refresh =
                            state.loadingState == LoadingState.Success && state.settings == settings

                        val semaphore = Semaphore(4)

                        val watchingRowIndexes =
                            settings.rows
                                .mapIndexedNotNull { index, row ->
                                    if (isWatchingRow(row.config)) index else null
                                }
                        val deferred =
                            settings.rows
                                // Load the watching rows first
                                .sortedByDescending { isWatchingRow(it.config) }
                                .map { row ->
                                    viewModelScope.async(Dispatchers.IO) {
                                        semaphore.withPermit {
                                            Timber.v("Fetching row: %s", row)
                                            try {
                                                homeSettingsService.fetchDataForRow(
                                                    row = row.config,
                                                    scope = viewModelScope,
                                                    prefs = prefs,
                                                    userDto = userDto,
                                                    libraries = libraries,
                                                    limit = prefs.maxItemsPerRow,
                                                )
                                            } catch (ex: Exception) {
                                                Timber.e(ex, "Error on row %s", row)
                                                HomeRowLoadingState.Error(row.title, exception = ex)
                                            }
                                        }
                                    }
                                }

                        if (refresh && state.homeRows.isNotEmpty() && watchingRowIndexes.isNotEmpty()) {
                            // Replace watching rows first
                            Timber.v("Refreshing rows: %s", watchingRowIndexes)
                            val rows =
                                deferred
                                    .filterIndexed { index, _ -> index in watchingRowIndexes }
                                    .awaitAll()
                            _state.update {
                                val newRows =
                                    it.homeRows.toMutableList().apply {
                                        rows.forEachIndexed { index, row ->
                                            set(watchingRowIndexes[index], row)
                                        }
                                    }
                                it.copy(
                                    loadingState = LoadingState.Success,
                                    homeRows = newRows,
                                )
                            }
                        }
                        val rows = deferred.awaitAll()
                        Timber.v("Got all rows")
                        _state.update {
                            it.copy(
                                loadingState = LoadingState.Success,
                                refreshState = LoadingState.Success,
                                homeRows = rows,
                            )
                        }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception during home page loading")
                    if (state.value.loadingState == LoadingState.Success) {
                        showToast(context, "Error refreshing home: ${ex.localizedMessage}")
                    } else {
                        _state.update {
                            it.copy(loadingState = LoadingState.Error(ex))
                        }
                    }
                }
            }
        }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            withContext(Dispatchers.Main) {
                init()
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            withContext(Dispatchers.Main) {
                init()
            }
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }
    }

data class HomeState(
    val loadingState: LoadingState,
    val refreshState: LoadingState,
    val homeRows: List<HomeRowLoadingState>,
    val settings: HomePageResolvedSettings,
) {
    companion object {
        val EMPTY =
            HomeState(
                LoadingState.Pending,
                LoadingState.Pending,
                listOf(),
                HomePageResolvedSettings.EMPTY,
            )
    }
}

/**
 * Whether a row is a "is watching" type
 */
private fun isWatchingRow(row: HomeRowConfig) =
    row is HomeRowConfig.ContinueWatching ||
        row is HomeRowConfig.NextUp ||
        row is HomeRowConfig.ContinueWatchingCombined
