package com.github.damontecres.wholphin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.DefaultItemFields
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.data.MovieSortOptions
import com.github.damontecres.wholphin.ui.data.SeriesSortOptions
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.data.VideoSortOptions
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.ItemViewModel
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CollectionFolderViewModel
    @Inject
    constructor(
        api: ApiClient,
    ) : ItemViewModel(api) {
        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val pager = MutableLiveData<List<BaseItem?>>(listOf())
        val sortAndDirection = MutableLiveData<SortAndDirection>()
        val filter = MutableLiveData<GetItemsFilter>(GetItemsFilter())

        fun init(
            itemId: UUID,
            potential: BaseItem?,
            sortAndDirection: SortAndDirection,
            recursive: Boolean,
            filter: GetItemsFilter,
        ): Job =
            viewModelScope.launch(
                LoadingExceptionHandler(
                    loading,
                    "Error loading collection $itemId",
                ) + Dispatchers.IO,
            ) {
                fetchItem(itemId, potential)
                loadResults(sortAndDirection, recursive, filter)
            }

        fun loadResults(
            sortAndDirection: SortAndDirection,
            recursive: Boolean,
            filter: GetItemsFilter,
        ) {
            item.value?.let { item ->
                viewModelScope.launch(Dispatchers.IO) {
                    withContext(Dispatchers.Main) {
                        pager.value = listOf()
                        loading.value = LoadingState.Loading
                        this@CollectionFolderViewModel.sortAndDirection.value = sortAndDirection
                        this@CollectionFolderViewModel.filter.value = filter
                    }
                    val includeItemTypes =
                        when (item.data.collectionType) {
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
                    val request =
                        filter.applyTo(
                            GetItemsRequest(
                                parentId = item.id,
                                enableImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB),
                                includeItemTypes = includeItemTypes,
                                recursive = recursive,
                                excludeItemIds = listOf(item.id),
                                sortBy =
                                    listOf(
                                        sortAndDirection.sort,
                                        ItemSortBy.SORT_NAME,
                                        ItemSortBy.PRODUCTION_YEAR,
                                    ),
                                sortOrder =
                                    listOf(
                                        sortAndDirection.direction,
                                        SortOrder.ASCENDING,
                                        SortOrder.ASCENDING,
                                    ),
                                fields = DefaultItemFields,
                            ),
                        )
                    val newPager =
                        ApiRequestPager(
                            api,
                            request,
                            GetItemsRequestHandler,
                            viewModelScope,
                            useSeriesForPrimary = true,
                        )
                    newPager.init()
                    if (newPager.isNotEmpty()) newPager.getBlocking(0)
                    withContext(Dispatchers.Main) {
                        pager.value = newPager
                        loading.value = LoadingState.Success
                    }
                }
            }
        }

        suspend fun positionOfLetter(letter: Char): Int? =
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

/**
 * Shows a collection folder as a grid
 *
 * This is the "Library" tab for Movies or TV shows
 */
@Composable
fun CollectionFolderGrid(
    preferences: UserPreferences,
    itemId: UUID,
    item: BaseItem?,
    initialFilter: GetItemsFilter,
    recursive: Boolean,
    onClickItem: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionFolderViewModel = hiltViewModel(),
    initialSortAndDirection: SortAndDirection =
        SortAndDirection(
            ItemSortBy.SORT_NAME,
            SortOrder.ASCENDING,
        ),
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
) {
    OneTimeLaunchedEffect {
        viewModel.init(itemId, item, initialSortAndDirection, recursive, initialFilter)
    }
    val sortAndDirection by viewModel.sortAndDirection.observeAsState(initialSortAndDirection)
    val filter by viewModel.filter.observeAsState(initialFilter)
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    val item by viewModel.item.observeAsState()
    val pager by viewModel.pager.observeAsState()

    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state)
        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage()
        LoadingState.Success -> {
            pager?.let { pager ->
                CollectionFolderGridContent(
                    preferences,
                    item!!,
                    pager,
                    sortAndDirection = sortAndDirection,
                    modifier = modifier,
                    onClickItem = onClickItem,
                    onSortChange = {
                        viewModel.loadResults(it, recursive, filter)
                    },
                    showTitle = showTitle,
                    positionCallback = positionCallback,
                    letterPosition = { viewModel.positionOfLetter(it) ?: -1 },
                )
            }
        }
    }
}

@Composable
fun CollectionFolderGridContent(
    preferences: UserPreferences,
    item: BaseItem,
    pager: List<BaseItem?>,
    sortAndDirection: SortAndDirection,
    onClickItem: (BaseItem) -> Unit,
    onSortChange: (SortAndDirection) -> Unit,
    letterPosition: suspend (Char) -> Int,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
) {
    val title = item.name ?: item.data.collectionType?.name ?: "Collection"
    val sortOptions =
        when (item.data.collectionType) {
            CollectionType.MOVIES -> MovieSortOptions
            CollectionType.TVSHOWS -> SeriesSortOptions
            CollectionType.HOMEVIDEOS -> VideoSortOptions
            else -> listOf(ItemSortBy.SORT_NAME, ItemSortBy.DATE_CREATED, ItemSortBy.RANDOM)
        }

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
            if (showTitle) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SortByButton(
                sortOptions = sortOptions,
                current = sortAndDirection,
                onSortChange = onSortChange,
                modifier = Modifier,
            )
        }
        CardGrid(
            pager = pager,
            onClickItem = onClickItem,
            onLongClickItem = {},
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
        )
    }
}
