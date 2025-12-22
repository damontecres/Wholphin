package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.LibraryDisplayInfoDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.filter.CommunityRatingFilter
import com.github.damontecres.wholphin.data.filter.DecadeFilter
import com.github.damontecres.wholphin.data.filter.DefaultFilterOptions
import com.github.damontecres.wholphin.data.filter.FavoriteFilter
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.FilterVideoType
import com.github.damontecres.wholphin.data.filter.GenreFilter
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.filter.OfficialRatingFilter
import com.github.damontecres.wholphin.data.filter.PlayedFilter
import com.github.damontecres.wholphin.data.filter.VideoTypeFilter
import com.github.damontecres.wholphin.data.filter.YearFilter
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilterOverride
import com.github.damontecres.wholphin.data.model.LibraryDisplayInfo
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.ItemViewModel
import com.github.damontecres.wholphin.ui.detail.MoreDialogActions
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItemsForHome
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.HomePageHeader
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.playback.scale
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.GetPersonsHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.localizationApi
import org.jellyfin.sdk.api.client.extensions.yearsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetPersonsRequest
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.TreeSet
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration

@HiltViewModel
class CollectionFolderViewModel
    @Inject
    constructor(
        api: ApiClient,
        @param:ApplicationContext private val context: Context,
        private val serverRepository: ServerRepository,
        private val libraryDisplayInfoDao: LibraryDisplayInfoDao,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val backdropService: BackdropService,
        val navigationManager: NavigationManager,
    ) : ItemViewModel(api) {
        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val backgroundLoading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val pager = MutableLiveData<List<BaseItem?>>(listOf())
        val sortAndDirection = MutableLiveData<SortAndDirection>()
        val filter = MutableLiveData<GetItemsFilter>(GetItemsFilter())
        val viewOptions = MutableLiveData<ViewOptions>()

        private var useSeriesForPrimary: Boolean = true
        private lateinit var collectionFilter: CollectionFolderFilter

        fun init(
            itemId: String,
            initialSortAndDirection: SortAndDirection?,
            recursive: Boolean,
            collectionFilter: CollectionFolderFilter,
            useSeriesForPrimary: Boolean,
            defaultViewOptions: ViewOptions,
        ): Job =
            viewModelScope.launch(
                LoadingExceptionHandler(
                    loading,
                    context.getString(R.string.error_loading_collection, itemId),
                ) + Dispatchers.IO,
            ) {
                this@CollectionFolderViewModel.collectionFilter = collectionFilter
                this@CollectionFolderViewModel.useSeriesForPrimary = useSeriesForPrimary
                this@CollectionFolderViewModel.itemId = itemId
                itemId.toUUIDOrNull()?.let {
                    fetchItem(it)
                }

                val libraryDisplayInfo =
                    serverRepository.currentUser.value?.let { user ->
                        libraryDisplayInfoDao.getItem(user, itemId)
                    }
                this@CollectionFolderViewModel.viewOptions.setValueOnMain(
                    libraryDisplayInfo?.viewOptions ?: defaultViewOptions,
                )

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

                loadResults(true, sortAndDirection, recursive, filterToUse, useSeriesForPrimary)
            }

        private fun saveLibraryDisplayInfo(
            newFilter: GetItemsFilter = this.filter.value!!,
            newSort: SortAndDirection = this.sortAndDirection.value!!,
            viewOptions: ViewOptions? = this.viewOptions.value,
        ) {
            if (collectionFilter.useSavedLibraryDisplayInfo) {
                serverRepository.currentUser.value?.let { user ->
                    viewModelScope.launch(Dispatchers.IO) {
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
            this.viewOptions.value = viewOptions
            viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                saveLibraryDisplayInfo(viewOptions = viewOptions)
                if (!viewOptions.showDetails) {
                    backdropService.clearBackdrop()
                }
            }
        }

        fun onFilterChange(
            newFilter: GetItemsFilter,
            recursive: Boolean,
        ) {
            Timber.v("onFilterChange: filter=%s", newFilter)
            saveLibraryDisplayInfo(newFilter, sortAndDirection.value!!)
            loadResults(false, sortAndDirection.value!!, recursive, newFilter, useSeriesForPrimary)
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
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    if (resetState) {
                        pager.value = listOf()
                        loading.value = LoadingState.Loading
                    }
                    backgroundLoading.value = LoadingState.Loading
                    this@CollectionFolderViewModel.sortAndDirection.value = sortAndDirection
                    this@CollectionFolderViewModel.filter.value = filter
                }
                try {
                    val newPager =
                        createPager(sortAndDirection, recursive, filter, useSeriesForPrimary).init()
                    if (newPager.isNotEmpty()) newPager.getBlocking(0)
                    withContext(Dispatchers.Main) {
                        pager.value = newPager
                        loading.value = LoadingState.Success
                        backgroundLoading.value = LoadingState.Success
                    }
                } catch (ex: Exception) {
                    Timber.e(
                        ex,
                        "Exception while loading data: sort=%s, filter=%s",
                        sortAndDirection,
                        filter,
                    )
                    withContext(Dispatchers.Main) {
                        loading.value = LoadingState.Error(ex)
                        pager.value = listOf()
                    }
                }
            }
        }

        private fun createPager(
            sortAndDirection: SortAndDirection,
            recursive: Boolean,
            filter: GetItemsFilter,
            useSeriesForPrimary: Boolean,
        ): ApiRequestPager<out Any> {
            val item = item.value
            return when (filter.override) {
                GetItemsFilterOverride.NONE -> {
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
                                enableImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB),
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
        }

        suspend fun getFilterOptionValues(filterOption: ItemFilterBy<*>): List<FilterValueOption> =
            try {
                when (filterOption) {
                    GenreFilter -> {
                        api.genresApi
                            .getGenres(
                                parentId = itemUuid,
                                userId = serverRepository.currentUser.value?.id,
                            ).content.items
                            .map { FilterValueOption(it.name ?: "", it.id) }
                    }

                    FavoriteFilter,
                    PlayedFilter,
                    -> {
                        listOf(
                            FilterValueOption("True", null),
                            FilterValueOption("False", null),
                        )
                    }

                    OfficialRatingFilter -> {
                        api.localizationApi.getParentalRatings().content.map {
                            FilterValueOption(it.name ?: "", it.value)
                        }
                    }

                    VideoTypeFilter -> {
                        FilterVideoType.entries.map {
                            FilterValueOption(it.readable, it)
                        }
                    }

                    YearFilter -> {
                        api.yearsApi
                            .getYears(
                                parentId = itemUuid,
                                userId = serverRepository.currentUser.value?.id,
                                sortBy = listOf(ItemSortBy.SORT_NAME),
                                sortOrder = listOf(SortOrder.ASCENDING),
                            ).content.items
                            .mapNotNull {
                                it.name?.toIntOrNull()?.let { FilterValueOption(it.toString(), it) }
                            }
                    }

                    DecadeFilter -> {
                        val items = TreeSet<Int>()
                        api.yearsApi
                            .getYears(
                                parentId = itemUuid,
                                userId = serverRepository.currentUser.value?.id,
                                sortBy = listOf(ItemSortBy.SORT_NAME),
                                sortOrder = listOf(SortOrder.ASCENDING),
                            ).content.items
                            .mapNotNullTo(items) {
                                it.name
                                    ?.toIntOrNull()
                                    ?.div(10)
                                    ?.times(10)
                            }
                        items.toList().sorted().map { FilterValueOption("$it's", it) }
                    }

                    CommunityRatingFilter -> {
                        (1..10).map {
                            FilterValueOption("$it", it)
                        }
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Exception get filter value options for $filterOption")
                listOf()
            }

        suspend fun positionOfLetter(letter: Char): Int? =
            withContext(Dispatchers.IO) {
                item.value?.let { item ->
                    val includeItemTypes =
                        when (item.data.collectionType) {
                            CollectionType.MOVIES -> listOf(BaseItemKind.MOVIE)
                            CollectionType.TVSHOWS -> listOf(BaseItemKind.SERIES)
                            CollectionType.HOMEVIDEOS -> listOf(BaseItemKind.VIDEO)
                            else -> listOf()
                        }
                    val request =
                        GetItemsRequest(
                            parentId = item.id,
                            includeItemTypes = includeItemTypes,
                            nameLessThan = letter.toString(),
                            limit = 0,
                            enableTotalRecordCount = true,
                            recursive = true,
                        )
                    val result by GetItemsRequestHandler.execute(api, request)
                    result.totalRecordCount
                }
            }

        fun setWatched(
            position: Int,
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            (pager.value as? ApiRequestPager<*>)?.refreshItem(position, itemId)
        }

        fun setFavorite(
            position: Int,
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            (pager.value as? ApiRequestPager<*>)?.refreshItem(position, itemId)
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }
    }

/**
 * Shows a collection folder as a grid
 *
 * This is the "Library" tab for Movies or TV shows
 */
@Composable
fun CollectionFolderGrid(
    preferences: UserPreferences,
    itemId: UUID,
    initialFilter: CollectionFolderFilter,
    recursive: Boolean,
    onClickItem: (Int, BaseItem) -> Unit,
    sortOptions: List<ItemSortBy>,
    playEnabled: Boolean,
    defaultViewOptions: ViewOptions,
    modifier: Modifier = Modifier,
    initialSortAndDirection: SortAndDirection? = null,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    useSeriesForPrimary: Boolean = true,
    filterOptions: List<ItemFilterBy<*>> = DefaultFilterOptions,
) = CollectionFolderGrid(
    preferences,
    itemId.toServerString(),
    initialFilter,
    recursive,
    onClickItem,
    sortOptions,
    playEnabled,
    defaultViewOptions = defaultViewOptions,
    modifier = modifier,
    initialSortAndDirection = initialSortAndDirection,
    showTitle = showTitle,
    positionCallback = positionCallback,
    useSeriesForPrimary = useSeriesForPrimary,
    filterOptions = filterOptions,
)

@Composable
fun CollectionFolderGrid(
    preferences: UserPreferences,
    itemId: String,
    initialFilter: CollectionFolderFilter,
    recursive: Boolean,
    onClickItem: (Int, BaseItem) -> Unit,
    sortOptions: List<ItemSortBy>,
    playEnabled: Boolean,
    defaultViewOptions: ViewOptions,
    modifier: Modifier = Modifier,
    viewModel: CollectionFolderViewModel = hiltViewModel(key = itemId),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
    initialSortAndDirection: SortAndDirection? = null,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    useSeriesForPrimary: Boolean = true,
    filterOptions: List<ItemFilterBy<*>> = DefaultFilterOptions,
) {
    val context = LocalContext.current
    OneTimeLaunchedEffect {
        viewModel.init(
            itemId,
            initialSortAndDirection,
            recursive,
            initialFilter,
            useSeriesForPrimary,
            defaultViewOptions,
        )
    }
    val sortAndDirection by viewModel.sortAndDirection.observeAsState(SortAndDirection.DEFAULT)
    val filter by viewModel.filter.observeAsState(initialFilter.filter)
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    val backgroundLoading by viewModel.backgroundLoading.observeAsState(LoadingState.Loading)
    val item by viewModel.item.observeAsState()
    val pager by viewModel.pager.observeAsState()
    val viewOptions by viewModel.viewOptions.observeAsState(defaultViewOptions)

    var moreDialog by remember { mutableStateOf<Optional<PositionItem>>(Optional.absent()) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)

    when (val state = loading) {
        is LoadingState.Error -> {
            ErrorMessage(state)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage()
        }

        LoadingState.Success -> {
            pager?.let { pager ->
                val title =
                    initialFilter.nameOverride
                        ?: item?.name
                        ?: item?.data?.collectionType?.name
                        ?: stringResource(R.string.collection)
                Box(modifier = modifier) {
                    CollectionFolderGridContent(
                        preferences = preferences,
                        item = item,
                        title = title,
                        pager = pager,
                        sortAndDirection = sortAndDirection!!,
                        modifier = Modifier.fillMaxSize(),
                        onClickItem = onClickItem,
                        onLongClickItem = { position, item ->
                            moreDialog.makePresent(PositionItem(position, item))
                        },
                        onSortChange = {
                            viewModel.onSortChange(it, recursive, filter)
                        },
                        filterOptions = filterOptions,
                        currentFilter = filter,
                        onFilterChange = {
                            viewModel.onFilterChange(it, recursive)
                        },
                        getPossibleFilterValues = {
                            viewModel.getFilterOptionValues(it)
                        },
                        showTitle = showTitle,
                        sortOptions = sortOptions,
                        positionCallback = positionCallback,
                        letterPosition = { viewModel.positionOfLetter(it) ?: -1 },
                        viewOptions = viewOptions,
                        defaultViewOptions = defaultViewOptions,
                        onSaveViewOptions = { viewModel.saveViewOptions(it) },
                        onChangeBackdrop = viewModel::updateBackdrop,
                        playEnabled = playEnabled,
                        onClickPlay = { _, item ->
                            viewModel.navigationManager.navigateTo(Destination.Playback(item))
                        },
                        onClickPlayAll = { shuffle ->
                            itemId.toUUIDOrNull()?.let {
                                viewModel.navigationManager.navigateTo(
                                    Destination.PlaybackList(
                                        itemId = it,
                                        startIndex = 0,
                                        shuffle = shuffle,
                                        recursive = recursive,
                                        sortAndDirection = sortAndDirection,
                                        filter = filter,
                                    ),
                                )
                            }
                        },
                    )

                    AnimatedVisibility(
                        backgroundLoading == LoadingState.Loading,
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
    }
    moreDialog.compose { (position, item) ->
        DialogPopup(
            showDialog = true,
            title = item.title ?: "",
            dialogItems =
                buildMoreDialogItemsForHome(
                    context = context,
                    item = item,
                    seriesId = null,
                    playbackPosition = item.playbackPosition,
                    watched = item.played,
                    favorite = item.favorite,
                    actions =
                        MoreDialogActions(
                            navigateTo = { viewModel.navigationManager.navigateTo(it) },
                            onClickWatch = { itemId, watched ->
                                viewModel.setWatched(position, itemId, watched)
                            },
                            onClickFavorite = { itemId, watched ->
                                viewModel.setFavorite(position, itemId, watched)
                            },
                            onClickAddPlaylist = {
                                playlistViewModel.loadPlaylists(MediaType.VIDEO)
                                showPlaylistDialog.makePresent(it)
                            },
                        ),
                ),
            onDismissRequest = { moreDialog.makeAbsent() },
            dismissOnClick = true,
            waitToLoad = true,
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
}

@Composable
fun CollectionFolderGridContent(
    preferences: UserPreferences,
    item: BaseItem?,
    title: String,
    pager: List<BaseItem?>,
    sortAndDirection: SortAndDirection,
    onClickItem: (Int, BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    onSortChange: (SortAndDirection) -> Unit,
    letterPosition: suspend (Char) -> Int,
    sortOptions: List<ItemSortBy>,
    playEnabled: Boolean,
    getPossibleFilterValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
    defaultViewOptions: ViewOptions,
    onSaveViewOptions: (ViewOptions) -> Unit,
    viewOptions: ViewOptions,
    onClickPlayAll: (shuffle: Boolean) -> Unit,
    onClickPlay: (Int, BaseItem) -> Unit,
    onChangeBackdrop: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    currentFilter: GetItemsFilter = GetItemsFilter(),
    filterOptions: List<ItemFilterBy<*>> = listOf(),
    onFilterChange: (GetItemsFilter) -> Unit = {},
) {
    val context = LocalContext.current

    var showHeader by rememberSaveable { mutableStateOf(true) }
    var showViewOptions by rememberSaveable { mutableStateOf(false) }
    var viewOptions by remember { mutableStateOf(viewOptions) }

    val gridFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { gridFocusRequester.tryRequestFocus() }
    var backdropImageUrl by remember { mutableStateOf<String?>(null) }

    var position by rememberInt(0)
    val focusedItem = pager.getOrNull(position)
    if (viewOptions.showDetails) {
        LaunchedEffect(focusedItem) {
            focusedItem?.let(onChangeBackdrop)
        }
    }

    Box(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            AnimatedVisibility(
                showHeader,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (showTitle) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val endPadding =
                        16.dp + if (sortAndDirection.sort == ItemSortBy.SORT_NAME) 24.dp else 0.dp
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .padding(start = 16.dp, end = endPadding)
                                .fillMaxWidth(),
                    ) {
                        if (sortOptions.isNotEmpty() || filterOptions.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier,
                            ) {
                                if (sortOptions.isNotEmpty()) {
                                    SortByButton(
                                        sortOptions = sortOptions,
                                        current = sortAndDirection,
                                        onSortChange = onSortChange,
                                        modifier = Modifier,
                                    )
                                }
                                if (filterOptions.isNotEmpty()) {
                                    FilterByButton(
                                        filterOptions = filterOptions,
                                        current = currentFilter,
                                        onFilterChange = onFilterChange,
                                        getPossibleValues = getPossibleFilterValues,
                                        modifier = Modifier,
                                    )
                                }
                                ExpandableFaButton(
                                    title = R.string.view_options,
                                    iconStringRes = R.string.fa_sliders,
                                    onClick = { showViewOptions = true },
                                    modifier = Modifier,
                                )
                            }
                        }
                        if (playEnabled) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier,
                            ) {
                                ExpandablePlayButton(
                                    title = R.string.play,
                                    resume = Duration.ZERO,
                                    icon = Icons.Default.PlayArrow,
                                    onClick = { onClickPlayAll.invoke(false) },
                                )
                                ExpandableFaButton(
                                    title = R.string.shuffle,
                                    iconStringRes = R.string.fa_shuffle,
                                    onClick = { onClickPlayAll.invoke(true) },
                                )
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(viewOptions.showDetails) {
                HomePageHeader(
                    item = focusedItem,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(16.dp),
                )
            }
            CardGrid(
                pager = pager,
                onClickItem = onClickItem,
                onLongClickItem = onLongClickItem,
                onClickPlay = onClickPlay,
                letterPosition = letterPosition,
                gridFocusRequester = gridFocusRequester,
                showJumpButtons = false, // TODO add preference
                showLetterButtons = sortAndDirection.sort == ItemSortBy.SORT_NAME,
                modifier = Modifier.fillMaxSize(),
                initialPosition = 0,
                positionCallback = { columns, newPosition ->
                    showHeader = newPosition < columns
                    position = newPosition
                    positionCallback?.invoke(columns, newPosition)
                },
                cardContent = { item, onClick, onLongClick, mod ->
                    GridCard(
                        item = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        imageContentScale = viewOptions.contentScale.scale,
                        imageAspectRatio = viewOptions.aspectRatio.ratio,
                        imageType = viewOptions.imageType,
                        showTitle = viewOptions.showTitles,
                        modifier = mod,
                    )
                },
                columns = viewOptions.columns,
                spacing = viewOptions.spacing.dp,
            )
            AnimatedVisibility(showViewOptions) {
                ViewOptionsDialog(
                    viewOptions = viewOptions,
                    defaultViewOptions = defaultViewOptions,
                    onDismissRequest = {
                        showViewOptions = false
                        onSaveViewOptions.invoke(viewOptions)
                    },
                    onViewOptionsChange = { viewOptions = it },
                )
            }
        }
    }
}

data class PositionItem(
    val position: Int,
    val item: BaseItem,
)

data class CollectionFolderGridParameters(
    val columns: Int = 6,
    val spacing: Dp = 16.dp,
    val cardContent: @Composable (
        item: BaseItem?,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        mod: Modifier,
    ) -> Unit = { item, onClick, onLongClick, mod ->
        GridCard(
            item = item,
            onClick = onClick,
            onLongClick = onLongClick,
            imageContentScale = ContentScale.FillBounds,
            modifier = mod,
        )
    },
) {
    companion object {
        val POSTER =
            CollectionFolderGridParameters(
                columns = 6,
                spacing = 16.dp,
                cardContent = { item, onClick, onLongClick, mod ->
                    GridCard(
                        item = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        imageContentScale = ContentScale.FillBounds,
                        imageAspectRatio = AspectRatios.TALL,
                        modifier = mod,
                    )
                },
            )
        val WIDE =
            CollectionFolderGridParameters(
                columns = 4,
                spacing = 24.dp,
                cardContent = { item, onClick, onLongClick, mod ->
                    GridCard(
                        item = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        imageContentScale = ContentScale.Crop,
                        imageAspectRatio = AspectRatios.WIDE,
                        modifier = mod,
                    )
                },
            )
        val SQUARE =
            CollectionFolderGridParameters(
                columns = 6,
                spacing = 16.dp,
                cardContent = { item, onClick, onLongClick, mod ->
                    GridCard(
                        item = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        imageContentScale = ContentScale.FillBounds,
                        imageAspectRatio = AspectRatios.SQUARE,
                        modifier = mod,
                    )
                },
            )
    }
}

val CollectionType.baseItemKinds: List<BaseItemKind>
    get() =
        when (this) {
            CollectionType.MOVIES -> {
                listOf(BaseItemKind.MOVIE)
            }

            CollectionType.TVSHOWS -> {
                listOf(BaseItemKind.SERIES)
            }

            CollectionType.HOMEVIDEOS -> {
                listOf(BaseItemKind.VIDEO)
            }

            CollectionType.MUSIC -> {
                listOf(
                    BaseItemKind.AUDIO,
                    BaseItemKind.MUSIC_ARTIST,
                    BaseItemKind.MUSIC_ALBUM,
                )
            }

            CollectionType.BOXSETS -> {
                listOf(BaseItemKind.BOX_SET)
            }

            CollectionType.PLAYLISTS -> {
                listOf(BaseItemKind.PLAYLIST)
            }

            else -> {
                listOf()
            }
        }
