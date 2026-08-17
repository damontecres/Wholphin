package com.github.damontecres.wholphin.ui.detail

import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.LibraryDisplayInfoDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.FilterOptionCache
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.ThemeSongPlayer
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.components.CollectionFolderState
import com.github.damontecres.wholphin.ui.components.CollectionFolderViewActions
import com.github.damontecres.wholphin.ui.components.ContextMenuProvider
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.components.defaultViewOptions
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.formatTypeName
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        val navigationManager: NavigationManager,
        private val serverRepository: ServerRepository,
        private val libraryDisplayInfoDao: LibraryDisplayInfoDao,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val backdropService: BackdropService,
        private val themeSongPlayer: ThemeSongPlayer,
        private val userPreferencesService: UserPreferencesService,
        private val mediaManagementService: MediaManagementService,
        private val musicService: MusicService,
        val streamChoiceService: StreamChoiceService,
        val mediaReportService: MediaReportService,
        private val filterOptionCache: FilterOptionCache,
    ) : ViewModel() {
        private val _state = MutableStateFlow(FavoritesPageState())
        val state: StateFlow<FavoritesPageState> = _state

        init {
            init()
        }

        fun init() {
            favoriteOptions.forEach { type ->
                viewModelScope.launchIO {
                    loadType(type)
                }
            }
        }

        private suspend fun loadType(type: BaseItemKind) {
            val collectionState =
                CollectionFolderState(
                    item = DataLoadingState.Loading,
                    items = DataLoadingState.Loading,
                    backgroundLoading = LoadingState.Loading,
                    viewOptions = ViewOptions(),
                )
            _state.value.favorites[type] = collectionState

            val request =
                GetItemsRequest(
                    isFavorite = true,
                    recursive = true,
                    includeItemTypes = listOf(type),
                    fields = SlimItemFields,
                    sortBy = null,
                    sortOrder = null,
                )
            val pager = ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope, pageSize = 50).init()
            val libraryDisplayInfo =
                serverRepository.currentUser?.let { user ->
                    libraryDisplayInfoDao.getItem(user, libraryDisplayItemId(type))
                }
            val sortAndDirection = libraryDisplayInfo?.sortAndDirection ?: SortAndDirection.DEFAULT
            val filter = libraryDisplayInfo?.filter ?: GetItemsFilter(favorite = true)
            val viewOptions = libraryDisplayInfo?.viewOptions ?: type.defaultViewOptions

            _state.value.favorites[type] =
                collectionState.copy(
                    item = DataLoadingState.Success(null),
                    items = DataLoadingState.Success(pager),
                    backgroundLoading = LoadingState.Success,
                    sortAndDirection = sortAndDirection,
                    filter = filter,
                    viewOptions = viewOptions,
                )
            Timber.v("Got %s favorites for %s", pager.size, type)
            if (pager.isNotEmpty()) {
                _state.update {
                    it.copy(
                        tabs =
                            it.tabs
                                .toMutableList()
                                .apply {
                                    add(type)
                                }.sortedBy { favoriteOptions.indexOf(it) },
                    )
                }
            }
        }

        fun libraryDisplayItemId(type: BaseItemKind): String =
            when (type) {
                BaseItemKind.MOVIE -> "movies"
                BaseItemKind.SERIES -> "series"
                BaseItemKind.EPISODE -> "episodes"
                BaseItemKind.VIDEO -> "videos"
                BaseItemKind.PLAYLIST -> "playlists"
                BaseItemKind.PERSON -> "people"
                else -> type.serialName
            }

        fun updateSelectedTabIndex(newIndex: Int) {
            // TODO save to DB
            _state.update { it.copy(tabIndex = newIndex) }
        }

        fun createTypedProvider(type: BaseItemKind) = TypedProvider(type)

        inner class TypedProvider(
            val type: BaseItemKind,
        ) : ContextMenuProvider,
            CollectionFolderViewActions {
            override fun isAdministrator(): Boolean {
                TODO("Not yet implemented")
            }

            override fun navigateTo(destination: Destination) {
                navigationManager.navigateTo(destination)
            }

            override fun canDelete(
                item: BaseItem,
                appPreferences: AppPreferences,
            ): Boolean {
                // TODO
                return false
            }

            override fun deleteItem(
                index: Int,
                item: BaseItem,
            ) {
                TODO("Not yet implemented")
            }

            override fun setWatched(
                position: Int,
                itemId: UUID,
                played: Boolean,
            ) {
                TODO("Not yet implemented")
            }

            override fun setFavorite(
                position: Int,
                itemId: UUID,
                favorite: Boolean,
            ) {
                TODO("Not yet implemented")
            }

            override fun sendReportFor(itemId: UUID) {
                TODO("Not yet implemented")
            }

            override fun updateBackdrop(item: BaseItem) {
                TODO("Not yet implemented")
            }

            override fun onSortChange(
                sortAndDirection: SortAndDirection,
                recursive: Boolean,
                filter: GetItemsFilter,
            ) {
                TODO("Not yet implemented")
            }

            override fun onFilterChange(
                newFilter: GetItemsFilter,
                recursive: Boolean,
            ) {
                TODO("Not yet implemented")
            }

            override suspend fun getFilterOptionValues(filterOption: ItemFilterBy<*>): List<FilterValueOption> {
                TODO("Not yet implemented")
            }

            override suspend fun positionOfLetter(letter: Char): Int? {
                TODO("Not yet implemented")
            }

            override fun saveViewOptions(viewOptions: ViewOptions) {
                TODO("Not yet implemented")
            }
        }
    }

data class FavoritesPageState(
    val favorites: SnapshotStateMap<BaseItemKind, CollectionFolderState> = SnapshotStateMap(),
    val tabs: List<BaseItemKind> = emptyList(),
    // TODO
    val tabIndex: Int = 0,
) {
    val tabDetails: List<TabDetails> get() = tabs.map { TabDetails(formatTypeName(it)) }
}

val favoriteOptions =
    listOf(
        BaseItemKind.MOVIE,
        BaseItemKind.SERIES,
        BaseItemKind.EPISODE,
        BaseItemKind.VIDEO,
        BaseItemKind.PLAYLIST,
        BaseItemKind.PERSON,
    )
