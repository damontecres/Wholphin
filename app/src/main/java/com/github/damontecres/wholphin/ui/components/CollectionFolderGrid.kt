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
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilterOverride
import com.github.damontecres.wholphin.data.model.LibraryDisplayInfo
import com.github.damontecres.wholphin.preferences.UserPreferences
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
import com.github.damontecres.wholphin.ui.nav.Destination
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
        val navigationManager: NavigationManager,
    ) : ItemViewModel(api) {
        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val backgroundLoading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val pager = MutableLiveData<List<BaseItem?>>(listOf())
        val sortAndDirection = MutableLiveData<SortAndDirection>()
        val filter = MutableLiveData<GetItemsFilter>(GetItemsFilter())

        private var useSeriesForPrimary: Boolean = true

        fun init(
            itemId: String,
            initialSortAndDirection: SortAndDirection?,
            recursive: Boolean,
            filter: GetItemsFilter,
            useSeriesForPrimary: Boolean,
        ): Job =
            viewModelScope.launch(
                LoadingExceptionHandler(
                    loading,
                    context.getString(R.string.error_loading_collection, itemId),
                ) + Dispatchers.IO,
            ) {
                this@CollectionFolderViewModel.useSeriesForPrimary = useSeriesForPrimary
                this@CollectionFolderViewModel.itemId = itemId
                itemId.toUUIDOrNull()?.let {
                    fetchItem(it)
                }

                val libraryDisplayInfo =
                    serverRepository.currentUser.value?.let { user ->
                        libraryDisplayInfoDao.getItem(user, itemId)
                    }

                val sortAndDirection =
                    libraryDisplayInfo?.sortAndDirection
                        ?: initialSortAndDirection
                        ?: SortAndDirection.DEFAULT

                val filterToUse =
                    if (libraryDisplayInfo?.filter != null) {
                        filter.merge(libraryDisplayInfo.filter)
                    } else {
                        filter
                    }

                loadResults(true, sortAndDirection, recursive, filterToUse, useSeriesForPrimary)
            }

        fun onFilterChange(
            newFilter: GetItemsFilter,
            recursive: Boolean,
        ) {
            Timber.v("onFilterChange: filter=%s", newFilter)
            serverRepository.currentUser.value?.let { user ->
                viewModelScope.launch(Dispatchers.IO) {
                    val libraryDisplayInfo =
                        LibraryDisplayInfo(
                            userId = user.rowId,
                            itemId = itemId,
                            sort = sortAndDirection.value!!.sort,
                            direction = sortAndDirection.value!!.direction,
                            filter = newFilter,
                        )
                    libraryDisplayInfoDao.saveItem(libraryDisplayInfo)
                }
            }
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
            serverRepository.currentUser.value?.let { user ->
                viewModelScope.launch(Dispatchers.IO) {
                    val libraryDisplayInfo =
                        LibraryDisplayInfo(
                            userId = user.rowId,
                            itemId = itemId,
                            sort = sortAndDirection.sort,
                            direction = sortAndDirection.direction,
                            filter = filter,
                        )
                    libraryDisplayInfoDao.saveItem(libraryDisplayInfo)
                }
            }
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
                val newPager = createPager(sortAndDirection, recursive, filter, useSeriesForPrimary)
                newPager.init()
                if (newPager.isNotEmpty()) newPager.getBlocking(0)
                withContext(Dispatchers.Main) {
                    pager.value = newPager
                    loading.value = LoadingState.Success
                    backgroundLoading.value = LoadingState.Success
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
                    ->
                        listOf(
                            FilterValueOption("True", null),
                            FilterValueOption("False", null),
                        )

                    OfficialRatingFilter -> {
                        api.localizationApi.getParentalRatings().content.map {
                            FilterValueOption(it.name ?: "", it.value)
                        }
                    }

                    VideoTypeFilter ->
                        FilterVideoType.entries.map {
                            FilterValueOption(it.readable, it)
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

                    CommunityRatingFilter ->
                        (1..10).map {
                            FilterValueOption("$it", it)
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
    initialFilter: GetItemsFilter,
    recursive: Boolean,
    onClickItem: (Int, BaseItem) -> Unit,
    sortOptions: List<ItemSortBy>,
    playEnabled: Boolean,
    modifier: Modifier = Modifier,
    initialSortAndDirection: SortAndDirection? = null,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    params: CollectionFolderGridParameters = CollectionFolderGridParameters(),
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
    modifier,
    initialSortAndDirection = initialSortAndDirection,
    showTitle = showTitle,
    positionCallback = positionCallback,
    params = params,
    useSeriesForPrimary = useSeriesForPrimary,
    filterOptions = filterOptions,
)

@Composable
fun CollectionFolderGrid(
    preferences: UserPreferences,
    itemId: String,
    initialFilter: GetItemsFilter,
    recursive: Boolean,
    onClickItem: (Int, BaseItem) -> Unit,
    sortOptions: List<ItemSortBy>,
    playEnabled: Boolean,
    modifier: Modifier = Modifier,
    viewModel: CollectionFolderViewModel = hiltViewModel(),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
    initialSortAndDirection: SortAndDirection? = null,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    params: CollectionFolderGridParameters = CollectionFolderGridParameters(),
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
        )
    }
    val sortAndDirection by viewModel.sortAndDirection.observeAsState(SortAndDirection.DEFAULT)
    val filter by viewModel.filter.observeAsState(initialFilter)
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    val backgroundLoading by viewModel.backgroundLoading.observeAsState(LoadingState.Loading)
    val item by viewModel.item.observeAsState()
    val pager by viewModel.pager.observeAsState()

    var moreDialog by remember { mutableStateOf<Optional<PositionItem>>(Optional.absent()) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)

    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state)
        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage()
        LoadingState.Success -> {
            pager?.let { pager ->
                Box(modifier = modifier) {
                    CollectionFolderGridContent(
                        preferences,
                        item,
                        pager,
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
                        params = params,
                        playEnabled = playEnabled,
                        onClickPlay = { shuffle ->
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
    pager: List<BaseItem?>,
    sortAndDirection: SortAndDirection,
    onClickItem: (Int, BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    onSortChange: (SortAndDirection) -> Unit,
    letterPosition: suspend (Char) -> Int,
    sortOptions: List<ItemSortBy>,
    playEnabled: Boolean,
    onClickPlay: (shuffle: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    params: CollectionFolderGridParameters = CollectionFolderGridParameters(),
    currentFilter: GetItemsFilter = GetItemsFilter(),
    filterOptions: List<ItemFilterBy<*>> = listOf(),
    onFilterChange: (GetItemsFilter) -> Unit = {},
    getPossibleFilterValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
) {
    val title = item?.name ?: item?.data?.collectionType?.name ?: stringResource(R.string.collection)

    var showHeader by rememberSaveable { mutableStateOf(true) }

    val gridFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { gridFocusRequester.tryRequestFocus() }
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = modifier,
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
                                onClick = { onClickPlay.invoke(false) },
                            )
                            ExpandableFaButton(
                                title = R.string.shuffle,
                                iconStringRes = R.string.fa_shuffle,
                                onClick = { onClickPlay.invoke(true) },
                            )
                        }
                    }
                }
            }
        }
        CardGrid(
            pager = pager,
            onClickItem = onClickItem,
            onLongClickItem = onLongClickItem,
            letterPosition = letterPosition,
            gridFocusRequester = gridFocusRequester,
            showJumpButtons = false, // TODO add preference
            showLetterButtons = sortAndDirection.sort == ItemSortBy.SORT_NAME,
            modifier = Modifier.fillMaxSize(),
            initialPosition = 0,
            positionCallback = { columns, position ->
                showHeader = position < columns
                positionCallback?.invoke(columns, position)
            },
            cardContent = params.cardContent,
            columns = params.columns,
            spacing = params.spacing,
        )
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
            CollectionType.MOVIES -> listOf(BaseItemKind.MOVIE)
            CollectionType.TVSHOWS -> listOf(BaseItemKind.SERIES)
            CollectionType.HOMEVIDEOS -> listOf(BaseItemKind.VIDEO)
            CollectionType.MUSIC ->
                listOf(
                    BaseItemKind.AUDIO,
                    BaseItemKind.MUSIC_ARTIST,
                    BaseItemKind.MUSIC_ALBUM,
                )

            CollectionType.BOXSETS -> listOf(BaseItemKind.BOX_SET)
            CollectionType.PLAYLISTS -> listOf(BaseItemKind.PLAYLIST)

            else -> listOf()
        }
