package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.withLocalPlayback
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
import com.github.damontecres.wholphin.services.PlaybackResult
import com.github.damontecres.wholphin.services.PlaybackResultService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.services.tvAccess
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.util.EmptyStringProvider
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
        private val playbackResultService: PlaybackResultService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HomeState.EMPTY)
        val state: StateFlow<HomeState> = _state

        // Locally-known playback outcomes (race-free) used to patch "continue watching" rows. Held
        // as this view model's own derived overrides: refreshed live as results arrive and re-applied
        // on every row (re)load, so a freshly fetched but still-stale server row reflects them.
        private val knownResults = ConcurrentHashMap<UUID, PlaybackResult>()

        init {
            datePlayedService.invalidateAll()
            playbackResultService.playbackResultFlow
                .onEach { result ->
                    knownResults[result.itemId] = result
                    _state.update { state ->
                        state.copy(homeRows = state.homeRows.map { patchWatchingRow(it, knownResults::get) })
                    }
                }.catch { ex ->
                    Timber.e(ex, "Error applying playback result to home rows")
                }.launchIn(viewModelScope)
        }

        fun init() {
            viewModelScope.launchIO {
                Timber.d("init HomeViewModel")
                try {
                    val preferences = userPreferencesService.getCurrent()
                    val prefs = preferences.appPreferences.homePagePreferences

                    serverRepository.currentUserDto?.let { userDto ->
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
                                        List(settings.rows.size) {
                                            HomeRowLoadingState.Pending(EmptyStringProvider)
                                        }
                                    },
                            )
                        }

                        val semaphore = Semaphore(4)

                        val deferred =
                            settings.rows
                                .map { row ->
                                    viewModelScope.async(WholphinDispatchers.IO) {
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
                                            } catch (ex: InvalidStatusException) {
                                                if (ex.status == 404) {
                                                    Timber.w(ex, "404 on row %s", row)
                                                    HomeRowLoadingState.Success(
                                                        row.title,
                                                        emptyList(),
                                                    )
                                                } else {
                                                    Timber.e(
                                                        ex,
                                                        "Error %s on row %s",
                                                        ex.status,
                                                        row,
                                                    )
                                                    HomeRowLoadingState.Error(
                                                        row.title,
                                                        exception = ex,
                                                    )
                                                }
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
                                remaining.removeIf { it.index == rowIndex }
                                // Patch outside _state.update: the cache take() has a side effect
                                // and update {} may re-run its block on concurrent updates.
                                val patchedRow = patchWatchingRow(rowData, knownResults::get)
                                _state.update { state ->
                                    val newRows =
                                        state.homeRows.toMutableList().apply {
                                            set(rowIndex, patchedRow)
                                        }
                                    state.copy(
                                        homeRows = newRows,
                                    )
                                }
                            }
                            _state.update {
                                it.copy(
                                    loadingState = LoadingState.Success,
                                    refreshState = LoadingState.Success,
                                )
                            }
                        } else {
                            val rows = deferred.awaitAll().map { patchWatchingRow(it, knownResults::get) }
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
                        _state.update { it.copy(refreshState = LoadingState.Error(ex)) }
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
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            withContext(WholphinDispatchers.Main) {
                init()
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            withContext(WholphinDispatchers.Main) {
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
                    serverRepository.currentUser?.id?.let { userId ->
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

/**
 * Applies locally-known playback outcomes (race-free) to a freshly fetched "continue watching"
 * row: the server may not have processed the playback-stopped report yet, so a just-watched item
 * would otherwise show a stale resume bar or none at all. Finished items are dropped from the row;
 * partially-watched items get their resume position/percentage refreshed. Non-watching rows, null
 * items, and items without a known result are returned unchanged.
 *
 * [lookup] returns the locally-known result for an item id, or null if none. It is non-consuming,
 * so the same result may be re-applied on later reloads. This is idempotent: an absolute resume
 * position / played state is set, and finished items are dropped.
 */
internal fun patchWatchingRow(
    row: HomeRowLoadingState,
    lookup: (UUID) -> PlaybackResult?,
): HomeRowLoadingState {
    if (row !is HomeRowLoadingState.Success) return row
    val rowType = row.rowType ?: return row
    if (!isWatchingRow(rowType)) return row
    val newItems =
        row.items.flatMap { item ->
            if (item == null) return@flatMap listOf<BaseItem?>(null)
            val result = lookup(item.id) ?: return@flatMap listOf<BaseItem?>(item)
            if (result.played) {
                emptyList()
            } else {
                listOf<BaseItem?>(item.withLocalPlayback(result.positionTicks, result.played))
            }
        }
    return row.copy(items = newItems)
}
