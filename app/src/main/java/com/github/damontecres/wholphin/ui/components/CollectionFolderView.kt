package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.LibraryDisplayInfoDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.filter.DefaultFilterOptions
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilterOverride
import com.github.damontecres.wholphin.data.model.LibraryDisplayInfo
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.ThemeSongPlayer
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.music.addToQueue
import com.github.damontecres.wholphin.ui.equalsNotNull
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.ui.util.FilterUtils
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.GetPersonsHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.successValue
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetPersonsRequest
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = CollectionFolderViewModel.Factory::class)
class CollectionFolderViewModel
    @AssistedInject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val api: ApiClient,
        @param:ApplicationContext private val context: Context,
        val serverRepository: ServerRepository,
        private val libraryDisplayInfoDao: LibraryDisplayInfoDao,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val backdropService: BackdropService,
        private val navigationManager: NavigationManager,
        private val themeSongPlayer: ThemeSongPlayer,
        private val userPreferencesService: UserPreferencesService,
        private val mediaManagementService: MediaManagementService,
        private val musicService: MusicService,
        val streamChoiceService: StreamChoiceService,
        val mediaReportService: MediaReportService,
        @Assisted val itemId: String,
        @Assisted initialSortAndDirection: SortAndDirection?,
        @Assisted("recursive") private val recursive: Boolean,
        @Assisted private val collectionFilter: CollectionFolderFilter,
        @Assisted("useSeriesForPrimary") private val useSeriesForPrimary: Boolean,
        @Assisted defaultViewOptions: ViewOptions,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                itemId: String,
                initialSortAndDirection: SortAndDirection?,
                @Assisted("recursive") recursive: Boolean,
                collectionFilter: CollectionFolderFilter,
                @Assisted("useSeriesForPrimary") useSeriesForPrimary: Boolean,
                defaultViewOptions: ViewOptions,
            ): CollectionFolderViewModel
        }

        private val _state = MutableStateFlow(CollectionFolderState(viewOptions = defaultViewOptions))
        val state: StateFlow<CollectionFolderState> = _state

        var position: Int
            get() = savedStateHandle.get<Int>("position") ?: 0
            set(value) {
                savedStateHandle["position"] = value
            }

        init {
            viewModelScope.launchIO {
                try {
                    val item =
                        itemId.toUUIDOrNull()?.let {
                            api.userLibraryApi
                                .getItem(it)
                                .content
                                .let(::BaseItem)
                        }

                    val libraryDisplayInfo =
                        serverRepository.currentUser?.let { user ->
                            libraryDisplayInfoDao.getItem(user, itemId)
                        }
                    _state.update {
                        it.copy(
                            viewOptions = libraryDisplayInfo?.viewOptions ?: defaultViewOptions,
                        )
                    }

                    val sortAndDirection =
                        if (collectionFilter.useSavedLibraryDisplayInfo) {
                            libraryDisplayInfo?.sortAndDirection
                        } else {
                            null
                        } ?: initialSortAndDirection ?: SortAndDirection.DEFAULT

                    val filterToUse =
                        if (collectionFilter.useSavedLibraryDisplayInfo && libraryDisplayInfo?.filter != null) {
                            collectionFilter.filter.merge(libraryDisplayInfo.filter)
                        } else {
                            collectionFilter.filter
                        }

                    _state.update { it.copy(item = DataLoadingState.Success(item)) }
                    loadResults(true, sortAndDirection, recursive, filterToUse, useSeriesForPrimary)
                        .join()
//                    onResumePage()
                } catch (ex: Exception) {
                    Timber.e(ex, "Error during init")
                    _state.update {
                        it.copy(
                            item = DataLoadingState.Error(ex),
                            items = DataLoadingState.Error(ex),
                        )
                    }
                }
            }
            mediaManagementService.deletedItemFlow
                .onEach { deletedItem ->
                    refreshAfterDelete(position, deletedItem.item)
                }.catch { ex ->
                    Timber.e(ex, "Error refreshing after deleted item")
                }.launchIn(viewModelScope)
        }

        private suspend fun refreshAfterDelete(
            position: Int,
            deletedItem: BaseItem,
        ) {
            try {
                val pager =
                    ((state.value.items as? DataLoadingState.Success)?.data as? ApiRequestPager<*>)
                position.let {
                    Timber.v("Item deleted: position=%s, id=%s", it, itemId)
                    val item = pager?.get(it)
                    // Exact item deleted (eg a movie) or deleted item was within the series
                    if (item?.id == deletedItem.id ||
                        equalsNotNull(item?.data?.id, deletedItem.data.seriesId)
                    ) {
                        pager?.refreshPagesAfter(position)
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Error refreshing after deleted item %s", itemId)
                showToast(context, "Error refreshing after item deleted")
            }
        }

        private fun saveLibraryDisplayInfo(
            newFilter: GetItemsFilter = state.value.filter,
            newSort: SortAndDirection = state.value.sortAndDirection,
            viewOptions: ViewOptions? = state.value.viewOptions,
        ) {
            if (collectionFilter.useSavedLibraryDisplayInfo) {
                serverRepository.currentUser?.let { user ->
                    viewModelScope.launchIO {
                        val libraryDisplayInfo =
                            LibraryDisplayInfo(
                                userId = user.rowId,
                                itemId = itemId,
                                sort = newSort.sort,
                                direction = newSort.direction,
                                filter = newFilter,
                                viewOptions = viewOptions,
                            )
                        libraryDisplayInfoDao.saveItem(libraryDisplayInfo)
                    }
                }
            }
        }

        fun saveViewOptions(viewOptions: ViewOptions) {
            _state.update { it.copy(viewOptions = viewOptions) }
            viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                saveLibraryDisplayInfo(viewOptions = viewOptions)
                if (!viewOptions.showBackdrop) {
                    backdropService.clearBackdrop()
                }
            }
        }

        fun onFilterChange(
            newFilter: GetItemsFilter,
            recursive: Boolean,
        ) {
            Timber.v("onFilterChange: filter=%s", newFilter)
            saveLibraryDisplayInfo(newFilter)
            loadResults(
                false,
                state.value.sortAndDirection,
                recursive,
                newFilter,
                useSeriesForPrimary,
            )
        }

        fun onSortChange(
            sortAndDirection: SortAndDirection,
            recursive: Boolean,
            filter: GetItemsFilter,
        ) {
            Timber.v(
                "onSortChange: sort=%s, recursive=%s, filter=%s",
                sortAndDirection,
                recursive,
                filter,
            )
            saveLibraryDisplayInfo(filter, sortAndDirection)
            loadResults(true, sortAndDirection, recursive, filter, useSeriesForPrimary)
        }

        private fun loadResults(
            resetState: Boolean,
            sortAndDirection: SortAndDirection,
            recursive: Boolean,
            filter: GetItemsFilter,
            useSeriesForPrimary: Boolean,
        ) = viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    items = DataLoadingState.Loading,
                    backgroundLoading = LoadingState.Loading,
                    sortAndDirection = sortAndDirection,
                    filter = filter,
                )
            }
            try {
                val newPager =
                    createPager(sortAndDirection, recursive, filter, useSeriesForPrimary).init()
                if (newPager.isNotEmpty()) newPager.getBlocking(0)
                _state.update {
                    it.copy(
                        items = DataLoadingState.Success(newPager),
                        backgroundLoading = LoadingState.Success,
                    )
                }
            } catch (ex: Exception) {
                Timber.e(
                    ex,
                    "Exception while loading data: sort=%s, filter=%s",
                    sortAndDirection,
                    filter,
                )
                _state.update { it.copy(items = DataLoadingState.Error(ex)) }
            }
        }

        private fun createPager(
            sortAndDirection: SortAndDirection,
            recursive: Boolean,
            filter: GetItemsFilter,
            useSeriesForPrimary: Boolean,
        ): ApiRequestPager<out Any> =
            when (filter.override) {
                GetItemsFilterOverride.NONE -> {
                    val request =
                        createGetItemsRequest(
                            sortAndDirection = sortAndDirection,
                            recursive = recursive,
                            filter = filter,
                        )
                    val newPager =
                        ApiRequestPager(
                            api,
                            request,
                            GetItemsRequestHandler,
                            viewModelScope,
                            useSeriesForPrimary = useSeriesForPrimary,
                        )
                    newPager
                }

                GetItemsFilterOverride.PERSON -> {
                    val request =
                        filter.applyTo(
                            GetPersonsRequest(
                                enableImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB),
                            ),
                        )
                    val newPager =
                        ApiRequestPager(
                            api,
                            request,
                            GetPersonsHandler,
                            viewModelScope,
                            useSeriesForPrimary = useSeriesForPrimary,
                        )
                    newPager
                }
            }

        private fun createGetItemsRequest(
            sortAndDirection: SortAndDirection,
            recursive: Boolean,
            filter: GetItemsFilter,
        ): GetItemsRequest {
            val item = state.value.item.successValue
            val includeItemTypes =
                item
                    ?.data
                    ?.collectionType
                    ?.baseItemKinds
                    .orEmpty()
            val request =
                filter.applyTo(
                    GetItemsRequest(
                        parentId = item?.id,
                        enableImageTypes =
                            listOf(
                                ImageType.PRIMARY,
                                ImageType.THUMB,
                                ImageType.BACKDROP,
                                ImageType.LOGO,
                            ),
                        includeItemTypes = includeItemTypes,
                        recursive = recursive,
                        excludeItemIds = item?.let { listOf(item.id) },
                        sortBy =
                            buildList {
                                if (sortAndDirection.sort != ItemSortBy.DEFAULT) {
                                    add(sortAndDirection.sort)
                                    if (sortAndDirection.sort != ItemSortBy.SORT_NAME) {
                                        add(ItemSortBy.SORT_NAME)
                                    }
                                    if (item?.data?.collectionType == CollectionType.MOVIES) {
                                        add(ItemSortBy.PRODUCTION_YEAR)
                                    }
                                }
                            },
                        sortOrder =
                            buildList {
                                if (sortAndDirection.sort != ItemSortBy.DEFAULT) {
                                    add(sortAndDirection.direction)
                                    if (sortAndDirection.sort != ItemSortBy.SORT_NAME) {
                                        add(SortOrder.ASCENDING)
                                    }
                                    if (item?.data?.collectionType == CollectionType.MOVIES) {
                                        add(SortOrder.ASCENDING)
                                    }
                                }
                            },
                        fields = SlimItemFields,
                    ),
                )
            return request
        }

        suspend fun getFilterOptionValues(filterOption: ItemFilterBy<*>): List<FilterValueOption> =
            FilterUtils.getFilterOptionValues(
                api,
                serverRepository.currentUser?.id,
                itemId.toUUID(),
                filterOption,
            )

        suspend fun positionOfLetter(letter: Char): Int? =
            withContext(Dispatchers.IO) {
                val sort = state.value.sortAndDirection
                val filter = state.value.filter
                val request =
                    createGetItemsRequest(
                        sortAndDirection = sort,
                        recursive = recursive,
                        filter = filter,
                    ).copy(
                        enableImageTypes = null,
                        fields = null,
                        nameLessThan = letter.toString(),
                        limit = 0,
                        enableTotalRecordCount = true,
                    )
                val result by GetItemsRequestHandler.execute(api, request)
                result.totalRecordCount
            }

        fun setWatched(
            position: Int,
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            (state.value.items as? DataLoadingState.Success)?.let {
                (it.data as? ApiRequestPager<*>)?.refreshItem(position, itemId)
            }
        }

        fun setFavorite(
            position: Int,
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            (state.value.items as? DataLoadingState.Success)?.let {
                (it.data as? ApiRequestPager<*>)?.refreshItem(position, itemId)
            }
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }

        fun navigateTo(destination: Destination) {
            release()
            navigationManager.navigateTo(destination)
        }

        fun release() {
            themeSongPlayer.stop()
        }

        fun onResumePage() {
            viewModelScope.launchIO {
                state.value.item.successValue?.let {
                    Timber.v("onResumePage: %s", state.value.items::class)
                    if (it.type == BaseItemKind.BOX_SET && state.value.items !is DataLoadingState.Error) {
                        themeSongPlayer.playThemeFor(it.id)
                    }
                }
            }
        }

        fun deleteItem(
            index: Int,
            item: BaseItem,
        ) {
            deleteItem(context, mediaManagementService, item) {
                viewModelScope.launchDefault {
                    refreshAfterDelete(index, item)
                }
            }
        }

        fun canDelete(
            item: BaseItem,
            appPreferences: AppPreferences,
        ): Boolean = mediaManagementService.canDelete(item, appPreferences)

        fun addToQueue(
            item: BaseItem,
            index: Int,
        ) = addToQueue(api, musicService, item, index)
    }

data class CollectionFolderState(
    val item: DataLoadingState<BaseItem?> = DataLoadingState.Loading,
    val items: DataLoadingState<List<BaseItem?>> = DataLoadingState.Loading,
    val backgroundLoading: LoadingState = LoadingState.Loading,
    val sortAndDirection: SortAndDirection = SortAndDirection.DEFAULT,
    val filter: GetItemsFilter = GetItemsFilter(),
    val viewOptions: ViewOptions,
)

/**
 * Shows a collection folder as a grid
 *
 * This is the "Library" tab for Movies or TV shows
 */
@Composable
fun CollectionFolderView(
    preferences: UserPreferences,
    itemId: UUID,
    initialFilter: CollectionFolderFilter,
    recursive: Boolean,
    onClickItem: (Int, BaseItem) -> Unit,
    sortOptions: List<ItemSortBy>,
    playEnabled: Boolean,
    defaultViewOptions: ViewOptions,
    modifier: Modifier = Modifier,
    viewModelKey: String? = itemId.toServerString(),
    initialSortAndDirection: SortAndDirection? = null,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    useSeriesForPrimary: Boolean = true,
    filterOptions: List<ItemFilterBy<*>> = DefaultFilterOptions,
    focusRequesterOnEmpty: FocusRequester? = null,
) = CollectionFolderView(
    preferences,
    itemId.toServerString(),
    initialFilter,
    recursive,
    GridClickActions(onClickItem = onClickItem),
    sortOptions,
    playEnabled,
    viewModelKey = viewModelKey,
    defaultViewOptions = defaultViewOptions,
    modifier = modifier,
    initialSortAndDirection = initialSortAndDirection,
    showTitle = showTitle,
    positionCallback = positionCallback,
    useSeriesForPrimary = useSeriesForPrimary,
    filterOptions = filterOptions,
    focusRequesterOnEmpty = focusRequesterOnEmpty,
)

@Composable
fun CollectionFolderView(
    preferences: UserPreferences,
    itemId: UUID,
    initialFilter: CollectionFolderFilter,
    recursive: Boolean,
    actions: GridClickActions,
    sortOptions: List<ItemSortBy>,
    playEnabled: Boolean,
    defaultViewOptions: ViewOptions,
    modifier: Modifier = Modifier,
    viewModelKey: String? = itemId.toServerString(),
    initialSortAndDirection: SortAndDirection? = null,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    useSeriesForPrimary: Boolean = true,
    filterOptions: List<ItemFilterBy<*>> = DefaultFilterOptions,
    focusRequesterOnEmpty: FocusRequester? = null,
) = CollectionFolderView(
    preferences,
    itemId.toServerString(),
    initialFilter,
    recursive,
    actions,
    sortOptions,
    playEnabled,
    viewModelKey = viewModelKey,
    defaultViewOptions = defaultViewOptions,
    modifier = modifier,
    initialSortAndDirection = initialSortAndDirection,
    showTitle = showTitle,
    positionCallback = positionCallback,
    useSeriesForPrimary = useSeriesForPrimary,
    filterOptions = filterOptions,
    focusRequesterOnEmpty = focusRequesterOnEmpty,
)

@Composable
fun CollectionFolderView(
    preferences: UserPreferences,
    itemId: String,
    initialFilter: CollectionFolderFilter,
    recursive: Boolean,
    actions: GridClickActions,
    sortOptions: List<ItemSortBy>,
    playEnabled: Boolean,
    defaultViewOptions: ViewOptions,
    modifier: Modifier = Modifier,
    viewModelKey: String? = itemId,
    initialSortAndDirection: SortAndDirection? = null,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    useSeriesForPrimary: Boolean = true,
    filterOptions: List<ItemFilterBy<*>> = DefaultFilterOptions,
    focusRequesterOnEmpty: FocusRequester? = null,
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
    viewModel: CollectionFolderViewModel =
        hiltViewModel<CollectionFolderViewModel, CollectionFolderViewModel.Factory>(
            key = viewModelKey,
        ) {
            it.create(
                itemId = itemId,
                initialSortAndDirection = initialSortAndDirection,
                recursive = recursive,
                collectionFilter = initialFilter,
                useSeriesForPrimary = useSeriesForPrimary,
                defaultViewOptions = defaultViewOptions,
            )
        },
) {
    val state by viewModel.state.collectAsState()

    var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.collectAsState()
    var showViewOptions by rememberSaveable { mutableStateOf(false) }

    val contextActions =
        remember {
            ContextMenuActions(
                navigateTo = viewModel::navigateTo,
                onClickWatch = { itemId, watched ->
                    viewModel.setWatched(viewModel.position, itemId, watched)
                },
                onClickFavorite = { itemId, favorite ->
                    viewModel.setFavorite(viewModel.position, itemId, favorite)
                },
                onClickAddPlaylist = { itemId ->
                    playlistViewModel.loadPlaylists(MediaType.VIDEO)
                    showPlaylistDialog.makePresent(itemId)
                },
                onSendMediaInfo = viewModel.mediaReportService::sendReportFor,
                onDeleteItem = { viewModel.deleteItem(viewModel.position, it) },
                onShowOverview = { overviewDialog = ItemDetailsDialogInfo(it) },
                onChooseVersion = { _, _ ->
                    // Not supported on this page
                },
                onChooseTracks = { result ->
                    // Not supported on this page
                },
                onClearChosenStreams = {
                    // Not supported on this page
                },
            )
        }

    val gridActions =
        remember(actions) {
            GridClickActions(
                onClickItem = actions.onClickItem,
                onLongClickItem =
                    actions.onLongClickItem ?: { position, item ->
                        showContextMenu =
                            ContextMenu.ForBaseItem(
                                fromLongClick = true,
                                item = item,
                                chosenStreams = null,
                                showGoTo = true,
                                showStreamChoices = false,
                                canDelete = viewModel.canDelete(item, preferences.appPreferences),
                                canRemoveContinueWatching = false,
                                canRemoveNextUp = false,
                                actions = contextActions,
                            )
                    },
                onClickPlayAll =
                    actions.onClickPlayAll ?: { shuffle ->
                        itemId.toUUIDOrNull()?.let {
                            val destination =
                                if (state.item.successValue?.type == BaseItemKind.PHOTO_ALBUM) {
                                    Destination.Slideshow(
                                        parentId = it,
                                        index = 0,
                                        filter = CollectionFolderFilter(filter = state.filter),
                                        sortAndDirection = state.sortAndDirection,
                                        recursive = true,
                                        startSlideshow = true,
                                    )
                                } else {
                                    Destination.PlaybackList(
                                        itemId = it,
                                        startIndex = 0,
                                        shuffle = shuffle,
                                        recursive = recursive,
                                        sortAndDirection = state.sortAndDirection,
                                        filter = state.filter,
                                    )
                                }
                            viewModel.navigateTo(destination)
                        }
                        Unit
                    },
                onClickPlayRemoteButton =
                    actions.onClickPlayRemoteButton ?: { index, item ->
                        val destination =
                            if (item.type == BaseItemKind.PHOTO_ALBUM) {
                                Destination.Slideshow(
                                    parentId = item.id,
                                    index = index,
                                    filter = CollectionFolderFilter(filter = state.filter),
                                    sortAndDirection = state.sortAndDirection,
                                    recursive = true,
                                    startSlideshow = true,
                                )
                            } else {
                                Destination.Playback(item)
                            }
                        viewModel.navigateTo(destination)
                    },
            )
        }

    when (val st = state.item) {
        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        // Error is passed down in case the error originates from sorting/filtering
        is DataLoadingState.Error,
        is DataLoadingState.Success<*>,
        -> {
            val item = st.successValue
            val title =
                initialFilter.nameOverride
                    ?: item?.name
                    ?: item?.data?.collectionType?.name
                    ?: stringResource(R.string.collection)
            Box(modifier = modifier) {
                LifecycleResumeEffect(itemId) {
                    viewModel.onResumePage()

                    onPauseOrDispose {
                        viewModel.release()
                    }
                }
                if (state.viewOptions.type == ViewOptionsType.GRID) {
                    CollectionFolderGrid(
                        preferences = preferences,
                        initialPosition = viewModel.position,
                        item = item,
                        title = title,
                        items = state.items,
                        sortAndDirection = state.sortAndDirection,
                        modifier = Modifier.fillMaxSize(),
                        focusRequesterOnEmpty = focusRequesterOnEmpty,
                        onClickItem = gridActions.onClickItem,
                        onLongClickItem = gridActions.onLongClickItem!!,
                        onSortChange = {
                            viewModel.onSortChange(it, recursive, state.filter)
                        },
                        filterOptions = filterOptions,
                        currentFilter = state.filter,
                        onFilterChange = {
                            viewModel.onFilterChange(it, recursive)
                        },
                        getPossibleFilterValues = {
                            viewModel.getFilterOptionValues(it)
                        },
                        showTitle = showTitle,
                        sortOptions = sortOptions,
                        positionCallback = { columns, position ->
                            viewModel.position = position
                            positionCallback?.invoke(columns, position)
                        },
                        letterPosition = { viewModel.positionOfLetter(it) ?: -1 },
                        viewOptions = state.viewOptions,
                        onChangeBackdrop = viewModel::updateBackdrop,
                        playEnabled = playEnabled,
                        onClickPlay = gridActions.onClickPlayRemoteButton!!,
                        onClickPlayAll = gridActions.onClickPlayAll!!,
                        onClickShowViewOptions = { showViewOptions = true },
                    )
                } else {
                    CollectionFolderList(
                        preferences = preferences,
                        initialPosition = viewModel.position,
                        item = item,
                        title = title,
                        items = state.items,
                        sortAndDirection = state.sortAndDirection,
                        modifier = Modifier.fillMaxSize(),
                        focusRequesterOnEmpty = focusRequesterOnEmpty,
                        onClickItem = gridActions.onClickItem,
                        onLongClickItem = gridActions.onLongClickItem!!,
                        onSortChange = {
                            viewModel.onSortChange(it, recursive, state.filter)
                        },
                        filterOptions = filterOptions,
                        currentFilter = state.filter,
                        onFilterChange = {
                            viewModel.onFilterChange(it, recursive)
                        },
                        getPossibleFilterValues = {
                            viewModel.getFilterOptionValues(it)
                        },
                        showTitle = showTitle,
                        sortOptions = sortOptions,
                        positionCallback = { columns, position ->
                            viewModel.position = position
                            positionCallback?.invoke(columns, position)
                        },
                        letterPosition = { viewModel.positionOfLetter(it) ?: -1 },
                        viewOptions = state.viewOptions,
                        onChangeBackdrop = viewModel::updateBackdrop,
                        playEnabled = playEnabled,
                        onClickPlay = gridActions.onClickPlayRemoteButton!!,
                        onClickPlayAll = gridActions.onClickPlayAll!!,
                        onClickShowViewOptions = { showViewOptions = true },
                    )
                }

                AnimatedVisibility(
                    state.backgroundLoading == LoadingState.Loading,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                ) {
                    CircularProgress(
                        Modifier
                            .background(
                                MaterialTheme.colorScheme.background.copy(alpha = .25f),
                                shape = CircleShape,
                            ).size(64.dp)
                            .padding(4.dp),
                    )
                }
            }
        }
    }
    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            showFilePath =
                viewModel.serverRepository.currentUserDto
                    ?.policy
                    ?.isAdministrator == true,
            onDismissRequest = { overviewDialog = null },
        )
    }
    showContextMenu?.let { contextMenu ->
        ContextMenuDialog(
            onDismissRequest = { showContextMenu = null },
            getMediaSource = null,
            contextMenu = contextMenu,
            preferredSubtitleLanguage = null,
        )
    }
    showPlaylistDialog.compose { itemId ->
        PlaylistDialog(
            title = stringResource(R.string.add_to_playlist),
            state = playlistState,
            onDismissRequest = { showPlaylistDialog.makeAbsent() },
            onClick = {
                playlistViewModel.addToPlaylist(it.id, itemId)
                showPlaylistDialog.makeAbsent()
            },
            createEnabled = true,
            onCreatePlaylist = {
                playlistViewModel.createPlaylistAndAddItem(it, itemId)
                showPlaylistDialog.makeAbsent()
            },
            elevation = 3.dp,
        )
    }
    AnimatedVisibility(showViewOptions) {
        ViewOptionsDialog(
            viewOptions = state.viewOptions,
            defaultViewOptions = defaultViewOptions,
            onDismissRequest = {
                showViewOptions = false
                viewModel.saveViewOptions(state.viewOptions)
            },
            onViewOptionsChange = viewModel::saveViewOptions,
        )
    }
}
