package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.HomePageResolvedSettings
import com.github.damontecres.wholphin.services.HomeSettingsService
import com.github.damontecres.wholphin.services.LatestNextUpService
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.services.tvAccess
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
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
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val mediaReportService: MediaReportService,
        private val navDrawerService: NavDrawerService,
        private val homeSettingsService: HomeSettingsService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val datePlayedService: DatePlayedService,
        private val backdropService: BackdropService,
        private val userPreferencesService: UserPreferencesService,
        private val mediaManagementService: MediaManagementService,
        private val latestNextUpService: LatestNextUpService,
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

                    serverRepository.currentUserDto.value?.let { userDto ->
                        val libraries =
                            navDrawerService.getAllUserLibraries(userDto.id, userDto.tvAccess)
                        val settings =
                            homeSettingsService.currentSettings.first { it != HomePageResolvedSettings.EMPTY }
                        val state = state.value

                        // Refreshing if a load has already occurred and the rows haven't significantly changed
                        val refresh =
                            state.loadingState == LoadingState.Success && state.settings == settings
                        Timber.v(
                            "refresh=%s, state.loadingState=%s, %s rows",
                            refresh,
                            state.loadingState,
                            settings.rows.size,
                        )
                        _state.update {
                            it.copy(
                                loadingState = if (refresh) LoadingState.Success else LoadingState.Loading,
                                refreshState = LoadingState.Loading,
                                settings = settings,
                                homeRows =
                                    if (refresh) {
                                        it.homeRows
                                    } else {
                                        List(settings.rows.size) { HomeRowLoadingState.Pending("") }
                                    },
                            )
                        }

                        val semaphore = Semaphore(4)

                        val deferred =
                            settings.rows
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
                                                    isRefresh = refresh,
                                                )
                                            } catch (ex: Exception) {
                                                Timber.e(ex, "Error on row %s", row)
                                                HomeRowLoadingState.Error(
                                                    row.title,
                                                    exception = ex,
                                                )
                                            }
                                        }
                                    }
                                }

                        if (refresh) {
                            // Replace rows as they complete
                            val remaining = deferred.withIndex().toMutableList()
                            while (remaining.isNotEmpty()) {
                                val (rowIndex, rowData) =
                                    select {
                                        // "Return" the first remaining that is completed
                                        remaining
                                            .forEach { (rowIndex, deferred) ->
                                                deferred.onAwait { rowIndex to it }
                                            }
                                    }
                                Timber.v("Got row data index=%s", rowIndex)
                                _state.update { state ->
                                    val newRows =
                                        state.homeRows.toMutableList().apply {
                                            set(rowIndex, rowData)
                                        }
                                    state.copy(
                                        homeRows = newRows,
                                    )
                                }
                                remaining.removeIf { it.index == rowIndex }
                            }
                            _state.update {
                                it.copy(
                                    loadingState = LoadingState.Success,
                                    refreshState = LoadingState.Success,
                                )
                            }
                        } else {
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
                        Timber.d("Home page load complete")
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

        fun deleteItem(
            position: RowColumn,
            item: BaseItem,
        ) {
            deleteItem(context, mediaManagementService, item) {
                viewModelScope.launchDefault {
                    val row = state.value.homeRows.getOrNull(position.row)
                    if (row is HomeRowLoadingState.Success) {
                        _state.update {
                            val newRow =
                                row.items.toMutableList().apply {
                                    removeAt(position.column)
                                }
                            it.copy(
                                homeRows =
                                    it.homeRows.toMutableList().apply {
                                        set(position.row, row.copy(items = newRow))
                                    },
                            )
                        }
                    }
                }
            }
        }

        fun canDelete(
            item: BaseItem,
            appPreferences: AppPreferences,
        ): Boolean = mediaManagementService.canDelete(item, appPreferences)

        fun removeFromNextUp(item: BaseItem) {
            if (item.type == BaseItemKind.EPISODE) {
                viewModelScope.launchDefault {
                    serverRepository.currentUser.value?.id?.let { userId ->
                        latestNextUpService.removeFromNextUp(userId, item)
                        init()
                    }
                }
            } else {
                Timber.w("Item is not an episode %s", item.id)
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
