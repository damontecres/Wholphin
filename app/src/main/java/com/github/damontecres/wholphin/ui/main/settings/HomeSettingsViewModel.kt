package com.github.damontecres.wholphin.ui.main.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.NavDrawerItemRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomePageSettings
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowConfig.ContinueWatching
import com.github.damontecres.wholphin.data.model.HomeRowConfig.ContinueWatchingCombined
import com.github.damontecres.wholphin.data.model.HomeRowConfig.NextUp
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.HomePageResolvedSettings
import com.github.damontecres.wholphin.services.HomeRowConfigDisplay
import com.github.damontecres.wholphin.services.HomeSettingsService
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeSettingsViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val homeSettingsService: HomeSettingsService,
        private val serverRepository: ServerRepository,
        private val userPreferencesService: UserPreferencesService,
        private val navDrawerItemRepository: NavDrawerItemRepository,
        private val backdropService: BackdropService,
        private val seerrServerRepository: SeerrServerRepository,
        @param:IoCoroutineScope private val ioScope: CoroutineScope,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HomePageSettingsState.EMPTY)
        val state: StateFlow<HomePageSettingsState> = _state

        val discoverEnabled = seerrServerRepository.active

        init {
            addCloseable { saveToLocal() }
            viewModelScope.launchIO {
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
                val currentSettings =
                    homeSettingsService.currentSettings.first { it != HomePageResolvedSettings.EMPTY }
                Timber.v("currentSettings=%s", currentSettings)
                _state.update {
                    it.copy(
                        libraries = libraries,
                        rows = currentSettings.rows,
                    )
                }
                fetchRowData()
            }
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }

        private suspend fun fetchRowData() {
            val limit = 6
            val rows =
                serverRepository.currentUserDto.value?.let { userDto ->
                    val prefs = userPreferencesService.getCurrent().appPreferences.homePagePreferences
                    state.value.let { state ->
                        state.rows
                            .map { it.config }
                            .map { row ->
                                // TODO parallelize
                                homeSettingsService.fetchDataForRow(
                                    row = row,
                                    scope = viewModelScope,
                                    prefs = prefs,
                                    userDto = userDto,
                                    libraries = state.libraries,
                                    limit = limit,
                                )
                            }
                    }
                }
            rows?.let { rows ->
                rows
                    .firstOrNull { it is HomeRowLoadingState.Success && it.items.isNotEmpty() }
                    ?.let {
                        it as HomeRowLoadingState.Success
                        it.items.firstOrNull()?.let {
                            Timber.v("Updating backdrop")
                            updateBackdrop(it)
                        }
                    }
                updateState {
                    it.copy(loading = LoadingState.Success, rowData = rows)
                }
            }
        }

        private fun <T> List<T>.move(
            direction: MoveDirection,
            index: Int,
        ): List<T> =
            toMutableList().apply {
                if (direction == MoveDirection.DOWN) {
                    val down = this[index]
                    val up = this[index + 1]
                    set(index, up)
                    set(index + 1, down)
                } else {
                    val up = this[index]
                    val down = this[index - 1]
                    set(index - 1, up)
                    set(index, down)
                }
            }

        fun moveRow(
            direction: MoveDirection,
            index: Int,
        ) {
            updateState {
                val rows = it.rows.move(direction, index)
                // TODO would be more efficient to move rowData, but uncombined continue watching is two rows
//                val rowData = it.rowData.move(direction, index)
                it.copy(
                    loading = LoadingState.Loading,
                    rows = rows,
                )
            }
            viewModelScope.launchIO { fetchRowData() }
        }

        fun deleteRow(index: Int) {
            updateState {
                val rows = it.rows.toMutableList().apply { removeAt(index) }
                val rowData = it.rowData.toMutableList().apply { removeAt(index) }
                it.copy(
                    rows = rows,
                    rowData = rowData,
                )
            }
        }

        fun addRow(type: MetaRowType): Job =
            viewModelScope.launchIO {
                val id = state.value.rows.size
                val newRow =
                    when (type) {
                        MetaRowType.CONTINUE_WATCHING -> {
                            HomeRowConfigDisplay(
                                id = id,
                                title = context.getString(R.string.continue_watching),
                                config = ContinueWatching(),
                            )
                        }

                        MetaRowType.NEXT_UP -> {
                            HomeRowConfigDisplay(
                                id = id,
                                title = context.getString(R.string.continue_watching),
                                config = NextUp(),
                            )
                        }

                        MetaRowType.COMBINED_CONTINUE_WATCHING -> {
                            HomeRowConfigDisplay(
                                id = id,
                                title = context.getString(R.string.combine_continue_next),
                                config = ContinueWatchingCombined(),
                            )
                        }

                        MetaRowType.FAVORITES -> {
                            throw IllegalArgumentException("Should use addRow(BaseItemKind) instead")
                        }

                        MetaRowType.DISCOVER -> {
                            TODO()
                        }
                    }
                updateState {
                    it.copy(
                        loading = LoadingState.Loading,
                        rows = it.rows.toMutableList().apply { add(newRow) },
                    )
                }
                fetchRowData()
            }

        fun addRow(
            library: Library,
            rowType: LibraryRowType,
        ): Job =
            viewModelScope.launchIO {
                val id = state.value.rows.size
                val newRow =
                    when (rowType) {
                        LibraryRowType.RECENTLY_ADDED -> {
                            val title =
                                library.name.let { context.getString(R.string.recently_added_in, it) }
                            HomeRowConfigDisplay(
                                id = id,
                                title = title,
                                config = HomeRowConfig.RecentlyAdded(library.itemId),
                            )
                        }

                        LibraryRowType.RECENTLY_RELEASED -> {
                            val title =
                                library.name.let {
                                    context.getString(
                                        R.string.recently_released_in,
                                        it,
                                    )
                                }
                            HomeRowConfigDisplay(
                                id = id,
                                title = title,
                                config = HomeRowConfig.RecentlyReleased(library.itemId),
                            )
                        }

                        LibraryRowType.GENRES -> {
                            val title = library.name.let { context.getString(R.string.genres_in, it) }
                            HomeRowConfigDisplay(
                                id = id,
                                title = title,
                                config = HomeRowConfig.Genres(library.itemId),
                            )
                        }
                    }
                updateState {
                    it.copy(
                        loading = LoadingState.Loading,
                        rows = it.rows.toMutableList().apply { add(newRow) },
                    )
                }
                fetchRowData()
            }

        fun addFavoriteRow(type: BaseItemKind): Job =
            viewModelScope.launchIO {
                Timber.v("Adding favorite row for $type")
                val id = state.value.rows.size
                val newRow =
                    HomeRowConfigDisplay(
                        id = id,
                        title = context.getString(favoriteOptions[type]!!),
                        config = HomeRowConfig.Favorite(type),
                    )
                updateState {
                    it.copy(
                        loading = LoadingState.Loading,
                        rows = it.rows.toMutableList().apply { add(newRow) },
                    )
                }
                fetchRowData()
            }

        fun updateViewOptions(
            rowId: Int,
            viewOptions: HomeRowViewOptions,
        ) {
            viewModelScope.launchIO {
                updateState {
                    val index = it.rows.indexOfFirst { it.id == rowId }
                    val newRowConfig =
                        it.rows[index]
                            .config
                            .updateViewOptions(viewOptions)
                    val newRow = it.rows[index].copy(config = newRowConfig)
                    it.copy(
                        rows =
                            it.rows.toMutableList().apply {
                                set(index, newRow)
                            },
                        rowData =
                            it.rowData.toMutableList().apply {
                                val row = it.rowData[index]
                                val newRow =
                                    if (row is HomeRowLoadingState.Success) {
                                        row.copy(viewOptions = viewOptions)
                                    } else {
                                        row
                                    }
                                set(index, newRow)
                            },
                    )
                }
            }
        }

        fun updateViewOptionsForAll(viewOptions: HomeRowViewOptions) {
            updateState {
                it.copy(
                    rowData =
                        it.rowData.toMutableList().map { row ->
                            if (row is HomeRowLoadingState.Success) {
                                row.copy(viewOptions = viewOptions)
                            } else {
                                row
                            }
                        },
                )
            }
        }

        fun saveToLocal() {
            // This uses injected ioScope so that it will still run when the page is closing
            ioScope.launchIO {
                serverRepository.currentUser.value?.let { user ->
                    val rows = state.value.rows.map { it.config }
                    val settings = HomePageSettings(rows = rows)
                    try {
                        Timber.d("saveToLocal")
                        val local = homeSettingsService.loadFromLocal(user.id)
                        // Only save if there are changes
                        if (local != settings) {
                            homeSettingsService.saveToLocal(user.id, settings)
                            showToast(context, context.getString(R.string.save), Toast.LENGTH_SHORT)
                        }
                    } catch (ex: Exception) {
                        Timber.e(ex)
                        showToast(context, "Error saving: ${ex.localizedMessage}")
                    }
                }
            }
        }

        private fun updateState(update: (HomePageSettingsState) -> HomePageSettingsState) {
            _state.update {
                update.invoke(it)
            }
            homeSettingsService.currentSettings.update { HomePageResolvedSettings(state.value.rows) }
        }

        fun resizeCards(relative: Int) {
            updateState {
                val newRows =
                    it.rows.toMutableList().map { row ->
                        val vo = row.config.viewOptions
                        val newVo = vo.copy(heightDp = vo.heightDp + (4 * relative))
                        row.copy(config = row.config.updateViewOptions(newVo))
                    }
                it.copy(
                    rows = newRows,
                    rowData =
                        it.rowData.toMutableList().mapIndexed { index, row ->
                            if (row is HomeRowLoadingState.Success) {
                                row.copy(viewOptions = newRows[index].config.viewOptions)
                            } else {
                                row
                            }
                        },
                )
            }
        }
    }

data class HomePageSettingsState(
    val loading: LoadingState,
    val rows: List<HomeRowConfigDisplay>,
    val rowData: List<HomeRowLoadingState>,
    val libraries: List<Library>,
) {
    companion object {
        val EMPTY =
            HomePageSettingsState(
                LoadingState.Pending,
                listOf(),
                listOf(),
                listOf(),
            )
    }
}

@Immutable
@Serializable
data class Library(
    @Serializable(UUIDSerializer::class) val itemId: UUID,
    val name: String,
    val collectionType: CollectionType,
)
