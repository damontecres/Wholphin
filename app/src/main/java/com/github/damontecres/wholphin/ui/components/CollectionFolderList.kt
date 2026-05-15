package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.RequestOrRestoreFocus
import com.github.damontecres.wholphin.ui.cards.WatchedIcon
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.extensions.ticks

@Composable
fun CollectionFolderList(
    preferences: UserPreferences,
    item: BaseItem?,
    title: String,
    loadingState: DataLoadingState<List<BaseItem?>>,
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
    initialPosition: Int,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    currentFilter: GetItemsFilter = GetItemsFilter(),
    filterOptions: List<ItemFilterBy<*>> = listOf(),
    onFilterChange: (GetItemsFilter) -> Unit = {},
    focusRequesterOnEmpty: FocusRequester? = null,
) {
    val context = LocalContext.current

    val pager = (loadingState as? DataLoadingState.Success)?.data
    var showHeader by rememberSaveable { mutableStateOf(true) }
    var showDetails by rememberSaveable { mutableStateOf(true) }
    var showViewOptions by rememberSaveable { mutableStateOf(false) }
    var viewOptions by remember { mutableStateOf(viewOptions) }
    val headerRowFocusRequester = remember { FocusRequester() }

    val gridFocusRequester = remember { FocusRequester() }
    if (pager?.isNotEmpty() == true) {
        RequestOrRestoreFocus(gridFocusRequester)
    } else {
        LaunchedEffect(Unit) {
            (focusRequesterOnEmpty ?: headerRowFocusRequester).tryRequestFocus()
        }
    }

    var position by rememberInt(initialPosition)
    val focusedItem = pager?.getOrNull(position)
    if (viewOptions.showDetails || true) {
        LaunchedEffect(focusedItem) {
            focusedItem?.let(onChangeBackdrop)
        }
    }
    Column(modifier = modifier) {
        CollectionFolderHeader(
            showHeader = showHeader || loadingState !is DataLoadingState.Success,
            showTitle = showTitle,
            playEnabled = playEnabled && pager?.isNotEmpty() == true,
            title = title,
            sortAndDirection = sortAndDirection,
            onSortChange = onSortChange,
            sortOptions = sortOptions,
            getPossibleFilterValues = getPossibleFilterValues,
            onClickShowViewOptions = { showViewOptions = true },
            onClickPlayAll = onClickPlayAll,
            currentFilter = currentFilter,
            filterOptions = filterOptions,
            onFilterChange = onFilterChange,
            modifier = Modifier.focusRequester(headerRowFocusRequester),
        )
        when (val state = loadingState) {
            DataLoadingState.Pending,
            DataLoadingState.Loading,
            -> {
                // This shouldn't happen, so just show placeholder
                Text(stringResource(R.string.loading))
            }

            is DataLoadingState.Error -> {
                ErrorMessage(state.message, state.exception)
            }

            is DataLoadingState.Success<List<BaseItem?>> -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier,
                ) {
                    CollectionFolderListDetails(
                        item = focusedItem,
                        showLogo = true,
                        modifier =
                            Modifier
                                .padding(8.dp)
                                .fillMaxWidth(.33f)
                                .fillMaxHeight()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                                    shape = RoundedCornerShape(16.dp),
                                ).padding(8.dp),
                    )
                    CollectionFolderContent(
                        items = state.data,
                        onPositionChange = {
                            showHeader = it < 1
                            positionCallback?.invoke(1, it)
                            position = it
                        },
                        onClickItem = onClickItem,
                        onLongClickItem = onLongClickItem,
                        modifier =
                            Modifier
                                .weight(1f)
                                .focusRequester(gridFocusRequester),
                    )
                }
            }
        }
    }
}

@Composable
fun CollectionFolderListDetails(
    item: BaseItem?,
    showLogo: Boolean,
    modifier: Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val imageUrl =
        remember(item) {
            item?.imageUrlOverride ?: imageUrlService.getItemImageUrl(item, ImageType.PRIMARY, useSeriesForPrimary = true)
        }
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        TitleOrLogo(item, showLogo, Modifier.fillMaxWidth())

        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier =
                Modifier
                    .clip(shape = RoundedCornerShape(8.dp))
                    .weight(1f),
        )

        OverviewText(
            overview = item?.data?.overview ?: "",
            maxLines = 4,
            onClick = {},
            enabled = false,
            modifier = Modifier,
        )

        // index?
    }
}

@Composable
fun CollectionFolderContent(
    items: List<BaseItem?>,
    onClickItem: (Int, BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    onPositionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier,
    ) {
        itemsIndexed(items) { index, item ->
            CollectionFolderListItem(
                item = item,
                onClick = { item?.let { onClickItem.invoke(index, item) } },
                onLongClick = { item?.let { onClickItem.invoke(index, item) } },
                modifier =
                    Modifier.onFocusChanged {
                        if (it.isFocused) onPositionChange.invoke(index)
                    },
            )
        }
    }
}

@Composable
fun CollectionFolderListItem(
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (item?.type) {
        BaseItemKind.MOVIE -> ListItemMovie(item, onClick, onLongClick, modifier)
        else -> ListItemGeneric(item, onClick, onLongClick, modifier)
    }
}

@Composable
fun ListItemGeneric(
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DenseListItem(
        selected = false,
        enabled = true,
        onClick = onClick,
        onLongClick = onLongClick,
        leadingContent = {
        },
        headlineContent = {
            Text(
                text = item?.title ?: "",
            )
        },
        supportingContent =
            item?.subtitle?.let { subtitle ->
                {
                    Text(
                        text = subtitle,
                    )
                }
            },
        trailingContent = {
            Text(
                text =
                    item
                        ?.data
                        ?.runTimeTicks
                        ?.ticks
                        ?.roundMinutes
                        ?.toString() ?: "",
            )
        },
        scale = ListItemDefaults.scale(focusedScale = 1f, pressedScale = .95f),
        colors =
            ListItemDefaults.colors(
                containerColor =
                    MaterialTheme.colorScheme
                        .surfaceColorAtElevation(1.dp)
                        .copy(alpha = .5f),
            ),
        modifier = modifier,
    )
}

@Composable
fun ListItemMovie(
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title =
        remember(item) {
            if (item != null && item.title != null && item.data.productionYear != null) {
                "${item.title} (${ item.data.productionYear})"
            } else {
                item?.title ?: ""
            }
        }
    DenseListItem(
        selected = false,
        enabled = true,
        onClick = onClick,
        onLongClick = onLongClick,
        leadingContent = {
            if (item?.played == true) {
                WatchedIcon()
            }
        },
        headlineContent = {
            Text(
                text = title,
            )
        },
        supportingContent = null,
        trailingContent = {
            Text(
                text =
                    item
                        ?.data
                        ?.runTimeTicks
                        ?.ticks
                        ?.roundMinutes
                        ?.toString() ?: "",
            )
        },
        scale = ListItemDefaults.scale(focusedScale = 1f, pressedScale = .95f),
        colors =
            ListItemDefaults.colors(
                containerColor =
                    MaterialTheme.colorScheme
                        .surfaceColorAtElevation(1.dp)
                        .copy(alpha = .5f),
            ),
        modifier = modifier,
    )
}
