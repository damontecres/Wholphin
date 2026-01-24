package com.github.damontecres.wholphin.ui.main.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.NavDrawerItemRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowConfigDisplay
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.preferences.HomePagePreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.LatestNextUpService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.DefaultItemFields
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.components.getGenreImageMap
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.HomePageContent
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import com.github.damontecres.wholphin.util.GetGenresRequestHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.HomeRowLoadingState.Success
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.request.GetGenresRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.serializer.toUUID
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomePageSettingsViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val userPreferencesService: UserPreferencesService,
        private val navDrawerItemRepository: NavDrawerItemRepository,
        private val backdropService: BackdropService,
        private val latestNextUpService: LatestNextUpService,
        private val imageUrlService: ImageUrlService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HomePageSettingsState.EMPTY)
        val state: StateFlow<HomePageSettingsState> = _state

        init {
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
                _state.update { it.copy(libraries = libraries) }

                // TODO get config

                // Or create default
                val prefs = userPreferencesService.getCurrent().appPreferences.homePagePreferences
                val includedIds =
                    navDrawerItemRepository
                        .getFilteredNavDrawerItems(navDrawerItems)
                        .filter { it is ServerNavDrawerItem }
                        .mapIndexed { index, it ->
                            val id = (it as ServerNavDrawerItem).itemId
                            val name = libraries.firstOrNull { it.itemId == id }?.name
                            val title =
                                name?.let { context.getString(R.string.recently_added_in, it) }
                                    ?: context.getString(R.string.recently_added)
                            HomeRowConfigDisplay(
                                title,
                                HomeRowConfig.RecentlyAdded(
                                    index,
                                    id,
                                    HomeRowViewOptions(),
                                ),
                            )
                        }
                val continueWatchingRows =
                    if (prefs.combineContinueNext) {
                        listOf(
                            HomeRowConfigDisplay(
                                context.getString(R.string.combine_continue_next),
                                HomeRowConfig.ContinueWatchingCombined(
                                    includedIds.size + 1,
                                    HomeRowViewOptions(),
                                ),
                            ),
                        )
                    } else {
                        listOf(
                            HomeRowConfigDisplay(
                                context.getString(R.string.continue_watching),
                                HomeRowConfig.ContinueWatching(
                                    includedIds.size + 1,
                                    HomeRowViewOptions(),
                                ),
                            ),
                            HomeRowConfigDisplay(
                                context.getString(R.string.next_up),
                                HomeRowConfig.NextUp(
                                    includedIds.size + 2,
                                    HomeRowViewOptions(),
                                ),
                            ),
                        )
                    }
                val rowConfig =
                    continueWatchingRows + includedIds +
                        // TODO remove after testing
                        listOf(
                            HomeRowConfigDisplay(
                                "Collection",
                                HomeRowConfig.ByParent(
                                    id = 100,
                                    parentId = "34ab6fd1f51c41bb014981f2e334f465".toUUID(),
                                    recursive = true,
                                    viewOptions = HomeRowViewOptions(),
                                ),
                            ),
                            HomeRowConfigDisplay(
                                "Playlist",
                                HomeRowConfig.ByParent(
                                    id = 101,
                                    parentId = "f94be36e9836127a0bccfc7843b19e5b".toUUID(),
                                    recursive = true,
                                    viewOptions = HomeRowViewOptions(),
                                ),
                            ),
                        )
                _state.update {
                    it.copy(rows = rowConfig)
                }
//                val json = Json.encodeToString(rowConfig)
//                Timber.v("json=$json")

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
                    state.value.rows
                        .map { it.config }
                        .map { row ->
                            // TODO parallelize
                            parseRow(prefs, userDto, row, limit)
                        }.flatten()
                }
            rows?.let { rows ->
                _state.update {
                    it.copy(loading = LoadingState.Success, rowData = rows)
                }
            }
        }

        private suspend fun parseRow(
            prefs: HomePagePreferences,
            userDto: UserDto,
            row: HomeRowConfig,
            limit: Int,
        ): List<HomeRowLoadingState> =
            when (row) {
                is HomeRowConfig.ContinueWatching -> {
                    val resume = latestNextUpService.getResume(userDto.id, limit, true)
                    listOf(
                        Success(
                            title = context.getString(R.string.continue_watching),
                            items = resume,
                            viewOptions = row.viewOptions,
                        ),
                    )
                }

                is HomeRowConfig.NextUp -> {
                    val nextUp =
                        latestNextUpService.getNextUp(
                            userDto.id,
                            limit,
                            prefs.enableRewatchingNextUp,
                            false,
                        )
                    listOf(
                        Success(
                            title = context.getString(R.string.next_up),
                            items = nextUp,
                            viewOptions = row.viewOptions,
                        ),
                    )
                }

                is HomeRowConfig.ContinueWatchingCombined -> {
                    val resume =
                        latestNextUpService.getResume(userDto.id, limit, true)
                    val nextUp =
                        latestNextUpService.getNextUp(
                            userDto.id,
                            limit,
                            prefs.enableRewatchingNextUp,
                            false,
                        )

                    listOf(
                        Success(
                            title = context.getString(R.string.continue_watching),
                            items =
                                latestNextUpService.buildCombined(
                                    resume,
                                    nextUp,
                                ),
                            viewOptions = row.viewOptions,
                        ),
                    )
                }

                is HomeRowConfig.Genres -> {
                    val request =
                        GetGenresRequest(
                            parentId = row.parentId,
                            userId = userDto.id,
                            limit = limit,
                        )
                    val items =
                        GetGenresRequestHandler
                            .execute(api, request)
                            .content.items
                    val genreIds = items.map { it.id }
                    val genreImages =
                        getGenreImageMap(
                            api = api,
                            imageUrlService = imageUrlService,
                            genres = genreIds,
                            parentId = row.parentId,
                            includeItemTypes = null,
                            cardWidthPx = null,
                        )
                    val genres =
                        items.map {
                            BaseItem(it, false, genreImages[it.id])
                        }

                    val name =
                        _state.value.libraries
                            .firstOrNull { it.itemId == row.parentId }
                            ?.name
                    val title =
                        name?.let { context.getString(R.string.genres_in, it) }
                            ?: context.getString(R.string.genres)
                    listOf(
                        Success(
                            title,
                            genres,
                            viewOptions = row.viewOptions,
                        ),
                    )
                }

                is HomeRowConfig.RecentlyAdded -> {
                    val name =
                        _state.value.libraries
                            .firstOrNull { it.itemId == row.parentId }
                            ?.name
                    val title =
                        name?.let { context.getString(R.string.recently_added_in, it) }
                            ?: context.getString(R.string.recently_added)
                    val request =
                        GetLatestMediaRequest(
                            fields = SlimItemFields,
                            imageTypeLimit = 1,
                            parentId = row.parentId,
                            groupItems = true,
                            limit = limit,
                            isPlayed = null, // Server will handle user's preference
                        )
                    val latest =
                        api.userLibraryApi
                            .getLatestMedia(request)
                            .content
                            .map { BaseItem.from(it, api, true) }
                            .let {
                                Success(
                                    title,
                                    it,
                                    row.viewOptions,
                                )
                            }
                    listOf(latest)
                }

                is HomeRowConfig.RecentlyReleased -> {
                    val name =
                        _state.value.libraries
                            .firstOrNull { it.itemId == row.parentId }
                            ?.name
                    val title =
                        name?.let {
                            context.getString(R.string.recently_released_in, it)
                        } ?: context.getString(R.string.recently_released)
                    val request =
                        GetItemsRequest(
                            parentId = row.parentId,
                            limit = limit,
                            sortBy = listOf(ItemSortBy.PREMIERE_DATE),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            fields = DefaultItemFields,
                            recursive = true,
                        )
                    GetItemsRequestHandler
                        .execute(api, request)
                        .content.items
                        .map { BaseItem.from(it, api, true) }
                        .let {
                            listOf(
                                Success(
                                    title,
                                    it,
                                    row.viewOptions,
                                ),
                            )
                        }
                }

                is HomeRowConfig.ByParent -> {
                    val request =
                        GetItemsRequest(
                            userId = userDto.id,
                            parentId = row.parentId,
                            recursive = row.recursive,
                            sortBy = row.sort?.let { listOf(it.sort) },
                            sortOrder = row.sort?.let { listOf(it.direction) },
                            limit = limit,
                            fields = DefaultItemFields,
                        )
                    val name =
                        api.userLibraryApi
                            .getItem(itemId = row.parentId)
                            .content.name
                    GetItemsRequestHandler
                        .execute(api, request)
                        .content.items
                        .map { BaseItem(it, true) }
                        .let {
                            listOf(
                                Success(
                                    name ?: context.getString(R.string.collection),
                                    it,
                                    row.viewOptions,
                                ),
                            )
                        }
                }

                is HomeRowConfig.GetItems -> {
                    val request =
                        row.getItems.let {
                            if (it.limit == null) {
                                it.copy(
                                    userId = userDto.id,
                                    limit = limit,
                                )
                            } else {
                                it.copy(
                                    userId = userDto.id,
                                )
                            }
                        }
                    val name =
                        if (row.name == null && request.parentId != null) {
                            // If a name was not provided, use the parent's name if available
                            api.userLibraryApi
                                .getItem(itemId = request.parentId!!)
                                .content.name
                        } else {
                            row.name
                        }
                    GetItemsRequestHandler
                        .execute(api, request)
                        .content.items
                        .map { BaseItem.from(it, api, true) }
                        .let {
                            listOf(
                                Success(
                                    name ?: context.getString(R.string.collection),
                                    it,
                                    row.viewOptions,
                                ),
                            )
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
            _state.update {
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
            _state.update {
                val rows = it.rows.toMutableList().apply { removeAt(index) }
                val rowData = it.rowData.toMutableList().apply { removeAt(index) }
                it.copy(
                    rows = rows,
                    rowData = rowData,
                )
            }
        }

        fun addRow(type: MetaRowType) {
            viewModelScope.launchIO {
                val id = state.value.rows.size
                val newRow =
                    when (type) {
                        MetaRowType.CONTINUE_WATCHING -> {
                            HomeRowConfigDisplay(
                                context.getString(R.string.continue_watching),
                                HomeRowConfig.ContinueWatching(
                                    id,
                                    HomeRowViewOptions(),
                                ),
                            )
                        }

                        MetaRowType.NEXT_UP -> {
                            HomeRowConfigDisplay(
                                context.getString(R.string.continue_watching),
                                HomeRowConfig.NextUp(
                                    id,
                                    HomeRowViewOptions(),
                                ),
                            )
                        }

                        MetaRowType.COMBINED_CONTINUE_WATCHING -> {
                            HomeRowConfigDisplay(
                                context.getString(R.string.combine_continue_next),
                                HomeRowConfig.ContinueWatchingCombined(
                                    id,
                                    HomeRowViewOptions(),
                                ),
                            )
                        }
                    }
                _state.update {
                    it.copy(
                        loading = LoadingState.Loading,
                        rows = it.rows.toMutableList().apply { add(newRow) },
                    )
                }
                fetchRowData()
            }
        }

        fun addRow(
            library: Library,
            rowType: LibraryRowType,
        ) {
            viewModelScope.launchIO {
                val id = state.value.rows.size
                val newRow =
                    when (rowType) {
                        LibraryRowType.RECENTLY_ADDED -> {
                            val title =
                                library.name.let { context.getString(R.string.recently_added_in, it) }
                            HomeRowConfigDisplay(
                                title,
                                HomeRowConfig.RecentlyAdded(
                                    id,
                                    library.itemId,
                                    HomeRowViewOptions(),
                                ),
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
                                title,
                                HomeRowConfig.RecentlyReleased(
                                    id,
                                    library.itemId,
                                    HomeRowViewOptions(),
                                ),
                            )
                        }

                        LibraryRowType.GENRES -> {
                            val title = library.name.let { context.getString(R.string.genres_in, it) }
                            HomeRowConfigDisplay(
                                title,
                                HomeRowConfig.Genres(
                                    id,
                                    library.itemId,
                                ),
                            )
                        }
                    }
                _state.update {
                    it.copy(
                        loading = LoadingState.Loading,
                        rows = it.rows.toMutableList().apply { add(newRow) },
                    )
                }
                fetchRowData()
            }
        }

        fun updateViewOptions(
            rowId: Int,
            viewOptions: HomeRowViewOptions,
        ) {
            viewModelScope.launchIO {
                _state.update {
                    val index = it.rows.indexOfFirst { it.config.id == rowId }
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
            _state.update {
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
data class Library(
    val itemId: UUID,
    val name: String,
    val collectionType: CollectionType,
)

val settingsWidth = 300.dp

@Composable
fun HomePageSettings(
    modifier: Modifier,
    viewModel: HomePageSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var destination by remember { mutableStateOf<HomePageSettingsDestination>(HomePageSettingsDestination.RowList) }

    BackHandler(destination is HomePageSettingsDestination.ChooseRowType) {
        destination = HomePageSettingsDestination.ChooseLibrary
    }
    BackHandler(destination is HomePageSettingsDestination.ChooseLibrary) {
        destination = HomePageSettingsDestination.RowList
    }
    BackHandler(destination is HomePageSettingsDestination.RowSettings) {
        destination = HomePageSettingsDestination.RowList
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .width(settingsWidth)
                    .fillMaxHeight()
                    .background(color = MaterialTheme.colorScheme.surface),
        ) {
            val destModifier =
                Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            when (val dest = destination) {
                HomePageSettingsDestination.RowList -> {
                    HomePageRowList(
                        state = state,
                        onClickAdd = { destination = HomePageSettingsDestination.ChooseLibrary },
                        onClickMove = viewModel::moveRow,
                        onClickDelete = viewModel::deleteRow,
                        onClick = { index, row ->
                            destination = HomePageSettingsDestination.RowSettings(row.config.id)
                        },
                        modifier = destModifier,
                    )
                }

                is HomePageSettingsDestination.ChooseLibrary -> {
                    HomePageLibraryList(
                        libraries = state.libraries,
                        onClick = { destination = HomePageSettingsDestination.ChooseRowType(it) },
                        onClickMeta = {
                            viewModel.addRow(it)
                            destination = HomePageSettingsDestination.RowList
                        },
                        modifier = destModifier,
                    )
                }

                is HomePageSettingsDestination.ChooseRowType -> {
                    HomePageLibraryRowTypeList(
                        library = dest.library,
                        onClick = {
                            viewModel.addRow(dest.library, it)
                            destination = HomePageSettingsDestination.RowList
                        },
                        modifier = destModifier,
                    )
                }

                is HomePageSettingsDestination.RowSettings -> {
                    val row =
                        state.rows
                            .first { it.config.id == dest.rowId }
                    HomePageRowSettings(
                        title = row.title,
                        viewOptions = row.config.viewOptions,
                        onViewOptionsChange = {
                            viewModel.updateViewOptions(dest.rowId, it)
                        },
                        onApplyApplyAll = {
                            viewModel.updateViewOptionsForAll(row.config.viewOptions)
                        },
                        modifier = destModifier,
                    )
                }
            }
        }
        HomePageContent(
            loadingState = state.loading,
            homeRows = state.rowData,
            onClickItem = { _, _ -> },
            onLongClickItem = { _, _ -> },
            onClickPlay = { _, _ -> },
            showClock = false,
            onUpdateBackdrop = viewModel::updateBackdrop,
            listState = listState,
            modifier =
                Modifier
                    .fillMaxHeight()
                    .weight(1f),
        )
    }
}
