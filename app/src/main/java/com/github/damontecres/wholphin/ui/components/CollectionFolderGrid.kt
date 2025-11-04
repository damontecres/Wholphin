package com.github.damontecres.wholphin.ui.components

import android.content.Context
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.LibraryDisplayInfoDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.data.model.LibraryDisplayInfo
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.ItemViewModel
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CollectionFolderViewModel
    @Inject
    constructor(
        api: ApiClient,
        @param:ApplicationContext private val context: Context,
        private val serverRepository: ServerRepository,
        private val libraryDisplayInfoDao: LibraryDisplayInfoDao,
    ) : ItemViewModel(api) {
        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val pager = MutableLiveData<List<BaseItem?>>(listOf())
        val sortAndDirection = MutableLiveData<SortAndDirection>()
        val filter = MutableLiveData<GetItemsFilter>(GetItemsFilter())

        fun init(
            itemId: String,
            initialSortAndDirection: SortAndDirection?,
            recursive: Boolean,
            filter: GetItemsFilter,
        ): Job =
            viewModelScope.launch(
                LoadingExceptionHandler(
                    loading,
                    context.getString(R.string.error_loading_collection, itemId),
                ) + Dispatchers.IO,
            ) {
                this@CollectionFolderViewModel.itemId = itemId
                itemId?.toUUIDOrNull()?.let {
                    fetchItem(it)
                }

                val sortAndDirection =
                    if (initialSortAndDirection == null) {
                        serverRepository.currentUser?.let { user ->
                            libraryDisplayInfoDao.getItem(user, itemId)?.sortAndDirection
                        } ?: SortAndDirection.DEFAULT
                    } else {
                        SortAndDirection.DEFAULT
                    }

                loadResults(sortAndDirection, recursive, filter)
            }

        fun onSortChange(
            sortAndDirection: SortAndDirection,
            recursive: Boolean,
            filter: GetItemsFilter,
        ) {
            serverRepository.currentUser?.let { user ->
                viewModelScope.launch(Dispatchers.IO) {
                    val libraryDisplayInfo =
                        LibraryDisplayInfo(
                            userId = user.rowId,
                            itemId = itemId,
                            sort = sortAndDirection.sort,
                            direction = sortAndDirection.direction,
                        )
                    libraryDisplayInfoDao.saveItem(libraryDisplayInfo)
                }
            }
            loadResults(sortAndDirection, recursive, filter)
        }

        private fun loadResults(
            sortAndDirection: SortAndDirection,
            recursive: Boolean,
            filter: GetItemsFilter,
        ) {
            val item = item.value
            viewModelScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    pager.value = listOf()
                    loading.value = LoadingState.Loading
                    this@CollectionFolderViewModel.sortAndDirection.value = sortAndDirection
                    this@CollectionFolderViewModel.filter.value = filter
                }
                val includeItemTypes =
                    when (item?.data?.collectionType) {
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
                            parentId = item?.id,
                            enableImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB),
                            includeItemTypes = includeItemTypes,
                            recursive = recursive,
                            excludeItemIds = item?.let { listOf(item.id) },
                            sortBy =
                                buildList {
                                    add(sortAndDirection.sort)
                                    if (sortAndDirection.sort != ItemSortBy.SORT_NAME) {
                                        add(ItemSortBy.SORT_NAME)
                                    }
                                    if (item?.data?.collectionType == CollectionType.MOVIES) {
                                        add(ItemSortBy.PRODUCTION_YEAR)
                                    }
                                },
                            sortOrder =
                                buildList {
                                    add(sortAndDirection.direction)
                                    if (sortAndDirection.sort != ItemSortBy.SORT_NAME) {
                                        add(SortOrder.ASCENDING)
                                    }
                                    if (item?.data?.collectionType == CollectionType.MOVIES) {
                                        add(SortOrder.ASCENDING)
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
    onClickItem: (BaseItem) -> Unit,
    sortOptions: List<ItemSortBy>,
    modifier: Modifier = Modifier,
    initialSortAndDirection: SortAndDirection? = null,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
) = CollectionFolderGrid(
    preferences,
    itemId.toServerString(),
    initialFilter,
    recursive,
    onClickItem,
    sortOptions,
    modifier,
    initialSortAndDirection = initialSortAndDirection,
    showTitle = showTitle,
    positionCallback = positionCallback,
)

@Composable
fun CollectionFolderGrid(
    preferences: UserPreferences,
    itemId: String,
    initialFilter: GetItemsFilter,
    recursive: Boolean,
    onClickItem: (BaseItem) -> Unit,
    sortOptions: List<ItemSortBy>,
    modifier: Modifier = Modifier,
    viewModel: CollectionFolderViewModel = hiltViewModel(),
    initialSortAndDirection: SortAndDirection? = null,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
) {
    OneTimeLaunchedEffect {
        viewModel.init(itemId, initialSortAndDirection, recursive, initialFilter)
    }
    val sortAndDirection by viewModel.sortAndDirection.observeAsState()
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
                    item,
                    pager,
                    sortAndDirection = sortAndDirection!!,
                    modifier = modifier,
                    onClickItem = onClickItem,
                    onSortChange = {
                        viewModel.onSortChange(it, recursive, filter)
                    },
                    showTitle = showTitle,
                    sortOptions = sortOptions,
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
    item: BaseItem?,
    pager: List<BaseItem?>,
    sortAndDirection: SortAndDirection,
    onClickItem: (BaseItem) -> Unit,
    onSortChange: (SortAndDirection) -> Unit,
    letterPosition: suspend (Char) -> Int,
    sortOptions: List<ItemSortBy>,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
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
