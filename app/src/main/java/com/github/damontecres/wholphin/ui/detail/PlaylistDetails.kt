package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.LibraryDisplayInfoDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.filter.DefaultPlaylistItemsOptions
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.data.model.LibraryDisplayInfo
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.DefaultItemFields
import com.github.damontecres.wholphin.ui.TimeFormatter
import com.github.damontecres.wholphin.ui.cards.ItemCardImage
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import com.github.damontecres.wholphin.ui.components.FilterByButton
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.SortByButton
import com.github.damontecres.wholphin.ui.data.BoxSetSortOptions
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.enableMarquee
import com.github.damontecres.wholphin.ui.formatDateTime
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.FilterUtils
import com.github.damontecres.wholphin.ui.util.LocalClock
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration

@HiltViewModel
class PlaylistViewModel
    @Inject
    constructor(
        api: ApiClient,
        val navigationManager: NavigationManager,
        private val backdropService: BackdropService,
        private val serverRepository: ServerRepository,
        private val libraryDisplayInfoDao: LibraryDisplayInfoDao,
    ) : ItemViewModel(api) {
        val loading = MutableLiveData<LoadingState>(LoadingState.Pending)
        val items = MutableLiveData<List<BaseItem?>>(listOf())
        val filterAndSort =
            MutableStateFlow<FilterAndSort>(
                FilterAndSort(
                    filter = GetItemsFilter(),
                    sortAndDirection =
                        SortAndDirection(
                            ItemSortBy.DEFAULT,
                            SortOrder.ASCENDING,
                        ),
                ),
            )

        fun init(playlistId: UUID) {
            loading.value = LoadingState.Loading
            viewModelScope.launch(
                Dispatchers.IO +
                    LoadingExceptionHandler(loading, "Failed to fetch playlist $playlistId"),
            ) {
                val playlist = fetchItem(playlistId)
                val libraryDisplayInfo =
                    serverRepository.currentUser.value?.let { user ->
                        libraryDisplayInfoDao.getItem(user, itemId)
                    }
                val filter = libraryDisplayInfo?.filter ?: GetItemsFilter()
                val sortAndDirection =
                    libraryDisplayInfo?.sortAndDirection ?: SortAndDirection(
                        ItemSortBy.DEFAULT,
                        SortOrder.ASCENDING,
                    )
                loadItems(filter, sortAndDirection)
            }
        }

        fun loadItems(
            filter: GetItemsFilter,
            sortAndDirection: SortAndDirection,
        ) {
            viewModelScope.launchIO {
                backdropService.clearBackdrop()
                loading.setValueOnMain(LoadingState.Loading)
                this@PlaylistViewModel.filterAndSort.update {
                    FilterAndSort(filter, sortAndDirection)
                }

                serverRepository.currentUser.value?.let { user ->
                    val playlistId = item.value!!.id
                    viewModelScope.launchIO {
                        val libraryDisplayInfo =
                            libraryDisplayInfoDao.getItem(user, itemId)?.copy(
                                filter = filter,
                                sort = sortAndDirection.sort,
                                direction = sortAndDirection.direction,
                            )
                                ?: LibraryDisplayInfo(
                                    userId = user.rowId,
                                    itemId = itemId,
                                    sort = sortAndDirection.sort,
                                    direction = sortAndDirection.direction,
                                    filter = filter,
                                    viewOptions = null,
                                )
                        libraryDisplayInfoDao.saveItem(libraryDisplayInfo)
                    }

                    val request =
                        filter.applyTo(
                            GetItemsRequest(
                                parentId = playlistId,
                                userId = user.id,
                                fields = DefaultItemFields,
                                sortBy = listOf(sortAndDirection.sort),
                                sortOrder = listOf(sortAndDirection.direction),
                            ),
                        )
                    try {
                        val pager =
                            ApiRequestPager(
                                api,
                                request,
                                GetItemsRequestHandler,
                                viewModelScope,
                            ).init()

                        withContext(Dispatchers.Main) {
                            items.value = pager
                            loading.value = LoadingState.Success
                        }
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error fetching playlist %s", itemId)
                        withContext(Dispatchers.Main) {
                            items.value = listOf()
                            loading.value = LoadingState.Error(ex)
                        }
                    }
                }
            }
        }

        suspend fun getFilterOptionValues(filterOption: ItemFilterBy<*>): List<FilterValueOption> =
            FilterUtils.getFilterOptionValues(
                api,
                serverRepository.currentUser.value?.id,
                itemUuid,
                filterOption,
            )

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }
    }

@Immutable
data class FilterAndSort(
    val filter: GetItemsFilter,
    val sortAndDirection: SortAndDirection,
)

@Composable
fun PlaylistDetails(
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.init(destination.itemId)
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Pending)
    val playlist by viewModel.item.observeAsState(null)
    val items by viewModel.items.observeAsState(listOf())
    val filterAndSort by viewModel.filterAndSort.collectAsState()

    var longClickDialog by remember { mutableStateOf<DialogParams?>(null) }

    val goToString = stringResource(R.string.go_to)
    val playFromHereString = stringResource(R.string.play_from_here)

    PlaylistDetailsContent(
        loadingState = loading,
        playlist = playlist,
        items = items,
        onChangeBackdrop = viewModel::updateBackdrop,
        onClickIndex = { index, _ ->
            viewModel.navigationManager.navigateTo(
                Destination.PlaybackList(
                    itemId = destination.itemId,
                    startIndex = index,
                    shuffle = false,
                    filter = filterAndSort.filter,
                    sortAndDirection = filterAndSort.sortAndDirection,
                ),
            )
        },
        onClickPlay = { shuffle ->
            viewModel.navigationManager.navigateTo(
                Destination.PlaybackList(
                    itemId = destination.itemId,
                    startIndex = 0,
                    shuffle = shuffle,
                    filter = filterAndSort.filter,
                    sortAndDirection = filterAndSort.sortAndDirection,
                ),
            )
        },
        onLongClickIndex = { index, item ->
            longClickDialog =
                DialogParams(
                    fromLongClick = true,
                    title = item.name ?: "",
                    items =
                        listOf(
                            DialogItem(
                                goToString,
                                Icons.Default.ArrowForward,
                            ) {
                                viewModel.navigationManager.navigateTo(item.destination())
                            },
                            DialogItem(
                                playFromHereString,
                                Icons.Default.PlayArrow,
                            ) {
                                viewModel.navigationManager.navigateTo(
                                    Destination.PlaybackList(
                                        itemId = destination.itemId,
                                        startIndex = index,
                                        shuffle = false,
                                        filter = filterAndSort.filter,
                                        sortAndDirection = filterAndSort.sortAndDirection,
                                    ),
                                )
                            },
                        ),
                )
        },
        filterAndSort = filterAndSort,
        onFilterAndSortChange = viewModel::loadItems,
        getPossibleFilterValues = viewModel::getFilterOptionValues,
        modifier = modifier,
    )
    longClickDialog?.let { params ->
        DialogPopup(
            params = params,
            onDismissRequest = { longClickDialog = null },
        )
    }
}

@Composable
fun PlaylistDetailsContent(
    playlist: BaseItem?,
    items: List<BaseItem?>,
    onClickIndex: (Int, BaseItem) -> Unit,
    onLongClickIndex: (Int, BaseItem) -> Unit,
    onClickPlay: (shuffle: Boolean) -> Unit,
    onChangeBackdrop: (BaseItem) -> Unit,
    filterAndSort: FilterAndSort,
    onFilterAndSortChange: (GetItemsFilter, SortAndDirection) -> Unit,
    getPossibleFilterValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
    loadingState: LoadingState,
    modifier: Modifier = Modifier,
) {
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusedIndex by remember { mutableIntStateOf(savedIndex) }
    val focus = remember { FocusRequester() }
    val focusedItem = items.getOrNull(focusedIndex)
    LaunchedEffect(focusedItem) {
        focusedItem?.let(onChangeBackdrop)
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(loadingState) {
        if (loadingState is LoadingState.Success || loadingState is LoadingState.Error) {
            focusRequester.tryRequestFocus()
        }
    }

    val playButtonFocusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .padding(top = 16.dp)
                    .fillMaxSize(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier =
                    Modifier
                        .fillMaxWidth(),
            ) {
                PlaylistDetailsHeader(
                    focusedItem = focusedItem,
                    onClickPlay = onClickPlay,
                    playButtonFocusRequester = playButtonFocusRequester,
                    focusRequester = if (items.isEmpty()) focusRequester else remember { FocusRequester() },
                    filterAndSort = filterAndSort,
                    onFilterAndSortChange = onFilterAndSortChange,
                    getPossibleFilterValues = getPossibleFilterValues,
                    modifier =
                        Modifier
                            .padding(top = 80.dp)
                            .fillMaxWidth(.25f),
                )
                when (loadingState) {
                    is LoadingState.Error -> {
                        ErrorMessage(loadingState, modifier)
                    }

                    LoadingState.Pending, LoadingState.Loading -> {
                        LoadingPage(modifier)
                    }

                    LoadingState.Success -> {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                        ) {
                            Text(
                                text = playlist?.name ?: stringResource(R.string.playlist),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.displayMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (items.isNotEmpty()) {
                                LazyColumn(
                                    contentPadding = PaddingValues(8.dp),
                                    modifier =
                                        Modifier
                                            .padding(bottom = 32.dp)
                                            .fillMaxHeight()
//                            .fillMaxWidth(.8f)
                                            .weight(1f)
                                            .background(
                                                MaterialTheme.colorScheme
                                                    .surfaceColorAtElevation(1.dp)
                                                    .copy(alpha = .75f),
                                                shape = RoundedCornerShape(16.dp),
                                            ).focusProperties {
                                                onExit = {
                                                    playButtonFocusRequester.tryRequestFocus()
                                                }
                                            }.focusRequester(focusRequester)
                                            .focusGroup()
                                            .focusRestorer(focus),
                                ) {
                                    itemsIndexed(items) { index, item ->
                                        PlaylistItem(
                                            item = item,
                                            index = index,
                                            onClick = {
                                                savedIndex = index
                                                item?.let {
                                                    onClickIndex.invoke(index, item)
                                                }
                                            },
                                            onLongClick = {
                                                savedIndex = index
                                                item?.let {
                                                    onLongClickIndex.invoke(index, item)
                                                }
                                            },
                                            modifier =
                                                Modifier
                                                    .height(80.dp)
                                                    .ifElse(
                                                        index == savedIndex,
                                                        Modifier.focusRequester(focus),
                                                    ).onFocusChanged {
                                                        if (it.isFocused) {
                                                            focusedIndex = index
                                                        }
                                                    }.focusProperties {
                                                        left = playButtonFocusRequester
                                                        previous = playButtonFocusRequester
                                                    },
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = stringResource(R.string.no_results),
                                        style = MaterialTheme.typography.titleLarge,
                                        textAlign = TextAlign.Center,
                                        modifier =
                                            Modifier
                                                .focusProperties {
                                                    onExit = {
                                                        playButtonFocusRequester.tryRequestFocus()
                                                    }
                                                }.focusRequester(focusRequester)
                                                .focusable(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailsHeader(
    focusedItem: BaseItem?,
    onClickPlay: (shuffle: Boolean) -> Unit,
    playButtonFocusRequester: FocusRequester,
    focusRequester: FocusRequester,
    filterAndSort: FilterAndSort,
    onFilterAndSortChange: (GetItemsFilter, SortAndDirection) -> Unit,
    getPossibleFilterValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier,
        ) {
            ExpandablePlayButton(
                title = R.string.play,
                resume = Duration.ZERO,
                icon = Icons.Default.PlayArrow,
                onClick = { onClickPlay.invoke(false) },
                modifier = Modifier.focusRequester(playButtonFocusRequester),
            )
            ExpandableFaButton(
                title = R.string.shuffle,
                iconStringRes = R.string.fa_shuffle,
                onClick = { onClickPlay.invoke(true) },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier,
        ) {
            FilterByButton(
                filterOptions = DefaultPlaylistItemsOptions,
                current = filterAndSort.filter,
                onFilterChange = {
                    onFilterAndSortChange.invoke(
                        it,
                        filterAndSort.sortAndDirection,
                    )
                },
                getPossibleValues = getPossibleFilterValues,
                modifier = Modifier.focusRequester(focusRequester),
            )
            SortByButton(
                sortOptions = BoxSetSortOptions,
                current = filterAndSort.sortAndDirection,
                onSortChange = { onFilterAndSortChange.invoke(filterAndSort.filter, it) },
            )
        }
        Text(
            text = focusedItem?.title ?: "",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = focusedItem?.subtitle ?: "",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        if (focusedItem?.type == BaseItemKind.EPISODE && focusedItem.data.premiereDate != null) {
            Text(
                text = formatDateTime(focusedItem.data.premiereDate!!),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        OverviewText(
            overview = focusedItem?.data?.overview ?: "",
            maxLines = 10,
            onClick = {},
            enabled = false,
        )
    }
}

@Composable
fun PlaylistItem(
    item: BaseItem?,
    index: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focused by interactionSource.collectIsFocusedAsState()
    ListItem(
        selected = false,
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interactionSource,
        headlineContent = {
            Text(
                text = item?.title ?: "",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.enableMarquee(focused),
            )
        },
        supportingContent = {
            Text(
                text = item?.subtitle ?: "",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.enableMarquee(focused),
            )
        },
        trailingContent = {
            item?.data?.runTimeTicks?.ticks?.roundMinutes?.let { duration ->
                val now by LocalClock.current.now
                val endTimeStr =
                    remember(item, now) {
                        val endTime = now.toLocalTime().plusSeconds(duration.inWholeSeconds)
                        TimeFormatter.format(endTime)
                    }
                Column {
                    Text(
                        text = duration.toString(),
                    )
                    Text(
                        text = stringResource(R.string.ends_at, endTimeStr),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        leadingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.labelLarge,
                )
                ItemCardImage(
                    item = item,
                    name = item?.name,
                    showOverlay = true,
                    favorite = item?.data?.userData?.isFavorite ?: false,
                    watched = item?.data?.userData?.played ?: false,
                    unwatchedCount = item?.data?.userData?.unplayedItemCount ?: -1,
                    watchedPercent = 0.0,
                    numberOfVersions = item?.data?.mediaSourceCount ?: 0,
                    modifier = Modifier.width(160.dp),
                    useFallbackText = false,
                )
            }
        },
        modifier = modifier,
    )
}
