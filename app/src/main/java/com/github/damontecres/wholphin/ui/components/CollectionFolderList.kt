package com.github.damontecres.wholphin.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.useExistingImageAsPlaceholder
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.RequestOrRestoreFocus
import com.github.damontecres.wholphin.ui.cards.FavoriteIndicator
import com.github.damontecres.wholphin.ui.cards.WatchedIcon
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.detail.AlphabetButtons
import com.github.damontecres.wholphin.ui.enableMarquee
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.extensions.ticks

@Composable
fun CollectionFolderList(
    preferences: UserPreferences,
    item: BaseItem?,
    title: String,
    items: DataLoadingState<List<BaseItem?>>,
    sortAndDirection: SortAndDirection,
    onClickItem: (Int, BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    onSortChange: (SortAndDirection) -> Unit,
    letterPosition: suspend (Char) -> Int,
    sortOptions: List<ItemSortBy>,
    playEnabled: Boolean,
    getPossibleFilterValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
    viewOptions: ViewOptions,
    onClickPlayAll: (shuffle: Boolean) -> Unit,
    onClickPlay: (Int, BaseItem) -> Unit,
    onChangeBackdrop: (BaseItem) -> Unit,
    onClickShowViewOptions: () -> Unit,
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
    val scope = rememberCoroutineScope()

    val pager = (items as? DataLoadingState.Success)?.data
    var showHeader by rememberSaveable { mutableStateOf(true) }
    val headerRowFocusRequester = remember { FocusRequester() }
    val showLetterButtons = sortAndDirection.sort == ItemSortBy.SORT_NAME

    val gridFocusRequester = remember { FocusRequester() }
    if (pager?.isNotEmpty() == true) {
        RequestOrRestoreFocus(gridFocusRequester)
    } else {
        LaunchedEffect(Unit) {
            (focusRequesterOnEmpty ?: headerRowFocusRequester).tryRequestFocus()
        }
    }
    val listState = rememberLazyListState()

    var contentHashFocus by remember { mutableStateOf(false) }
    var position by rememberInt(initialPosition)
    val positionFocusRequester = remember { FocusRequester() }
    val focusedItem = pager?.getOrNull(position)
    if (viewOptions.showBackdrop) {
        LaunchedEffect(focusedItem) {
            focusedItem?.let(onChangeBackdrop)
        }
    }

    fun jumpTo(newPosition: Int) {
        scope.launch {
            position = newPosition
            listState.animateScrollToItem(newPosition)
            positionFocusRequester.tryRequestFocus()
        }
    }

    BackHandler(contentHashFocus && position > 0) {
        jumpTo(0)
    }

    Column(modifier = modifier) {
        CollectionFolderHeader(
            showHeader = showHeader || items !is DataLoadingState.Success,
            showTitle = showTitle,
            playEnabled = playEnabled && pager?.isNotEmpty() == true,
            title = title,
            sortAndDirection = sortAndDirection,
            onSortChange = onSortChange,
            sortOptions = sortOptions,
            getPossibleFilterValues = getPossibleFilterValues,
            onClickShowViewOptions = onClickShowViewOptions,
            onClickPlayAll = onClickPlayAll,
            currentFilter = currentFilter,
            filterOptions = filterOptions,
            onFilterChange = onFilterChange,
            modifier = Modifier.focusRequester(headerRowFocusRequester),
        )
        when (val state = items) {
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
                val pager = state.data
                val topPadding by animateDpAsState(
                    targetValue =
                        when {
                            showHeader -> 8.dp

                            // showClock-> TODO()
                            else -> 48.dp
                        },
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .padding(top = topPadding, bottom = 16.dp)
                            .onFocusChanged {
                                contentHashFocus = it.hasFocus
                            },
                ) {
                    AnimatedVisibility(
                        visible = viewOptions.showDetails,
                        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
                    ) {
                        CollectionFolderListDetails(
                            item = focusedItem,
                            showLogo = true,
                            modifier =
                                Modifier
                                    .fillMaxWidth(.33f)
                                    .fillMaxHeight()
                                    .background(
                                        color = Color.Transparent, // MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                                        shape = RoundedCornerShape(16.dp),
                                    ).padding(8.dp),
                        )
                    }
                    CollectionFolderContent(
                        items = state.data,
                        viewOptions = viewOptions,
                        collectionType = item?.data?.collectionType,
                        listState = listState,
                        position = position,
                        positionFocusRequester = positionFocusRequester,
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
                    val letters = stringResource(R.string.jump_letters)
                    // Letters
                    val currentLetter =
                        remember(focusedItem) {
                            focusedItem
                                ?.sortName
                                ?.firstOrNull()
                                ?.uppercaseChar()
                                ?.let {
                                    when (it) {
                                        in '0'..'9' -> '#'
                                        in 'A'..'Z' -> it
                                        else -> null
                                    }
                                }
                                ?: letters[0]
                        }
                    if (showLetterButtons && pager.isNotEmpty()) {
                        AlphabetButtons(
                            letters = letters,
                            currentLetter = currentLetter,
                            modifier =
                                Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(start = 16.dp),
                            letterClicked = {
                                scope.launchIO {
                                    val position = letterPosition.invoke(it)
                                    jumpTo(position)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalCoilApi::class)
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        SimpleTitleOrLogo(
            item,
            showLogo,
            Modifier
                .height(HeaderUtils.logoHeight)
                .fillMaxWidth(),
        )

        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(300)
                    .useExistingImageAsPlaceholder(true)
                    .build(),
            contentDescription = null,
            modifier =
                Modifier
                    .clip(shape = RoundedCornerShape(8.dp))
                    .weight(1f),
        )

        item?.let {
            QuickDetails(item.ui.quickDetails, null)
        }

        OverviewText(
            overview = item?.data?.overview ?: "",
            maxLines = 5,
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
    viewOptions: ViewOptions,
    collectionType: CollectionType?,
    listState: LazyListState,
    position: Int,
    positionFocusRequester: FocusRequester,
    onClickItem: (Int, BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    onPositionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(viewOptions.spacing.dp),
        modifier = modifier.focusRestorer(positionFocusRequester),
    ) {
        itemsIndexed(items) { index, item ->
            val onClick: () -> Unit =
                remember(index, item) {
                    { item?.let { onClickItem.invoke(index, item) } }
                }
            val onLongClick: () -> Unit =
                remember(index, item) {
                    { item?.let { onLongClickItem.invoke(index, item) } }
                }
            CollectionFolderListItem(
                item = item,
                dense = viewOptions.type == ViewOptionsType.DENSE_LIST,
                collectionType = collectionType,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier =
                    Modifier
                        .onFocusChanged {
                            if (it.isFocused) onPositionChange.invoke(index)
                        }.ifElse(index == position, Modifier.focusRequester(positionFocusRequester)),
            )
        }
    }
}

@Composable
fun CollectionFolderListItem(
    item: BaseItem?,
    dense: Boolean,
    collectionType: CollectionType?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (collectionType) {
        CollectionType.MOVIES -> ListItemMovie(dense, item, onClick, onLongClick, modifier)
        CollectionType.TVSHOWS -> ListItemSeries(dense, item, onClick, onLongClick, modifier)
        CollectionType.MUSIC -> ListItemAlbum(dense, item, onClick, onLongClick, modifier)
        else -> ListItemGeneric(dense, item, onClick, onLongClick, modifier)
    }
}

@Composable
fun ListItemGeneric(
    dense: Boolean,
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    ListItemWrapper(
        dense = dense,
        selected = false,
        enabled = true,
        onClick = onClick,
        onLongClick = onLongClick,
        leadingContent = {
            if (item?.favorite == true) {
                FavoriteIndicator()
            }
        },
        headlineContent = { TitleContent(item?.title, interactionSource) },
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
        scale = scale,
        colors = listItemColors(),
        interactionSource = interactionSource,
        modifier = modifier,
    )
}

@Composable
fun ListItemMovie(
    dense: Boolean,
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val title =
        remember(item, dense) {
            if (dense && item != null && item.title != null && item.data.productionYear != null) {
                "${item.title} (${ item.data.productionYear})"
            } else {
                item?.title ?: ""
            }
        }
    val supportingContent =
        remember(item, dense) {
            if (!dense) {
                @Composable {
                    Text(item?.subtitle ?: "")
                }
            } else {
                null
            }
        }
    ListItemWrapper(
        dense = dense,
        selected = false,
        enabled = true,
        onClick = onClick,
        onLongClick = onLongClick,
        leadingContent = {
            if (item?.favorite == true) {
                FavoriteIndicator()
            }
        },
        headlineContent = { TitleContent(title, interactionSource) },
        supportingContent = supportingContent,
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        item
                            ?.data
                            ?.runTimeTicks
                            ?.ticks
                            ?.roundMinutes
                            ?.toString() ?: "",
                )
                if (item?.played == true) {
                    WatchedIcon(padding = 0.dp)
                }
            }
        },
        scale = scale,
        colors = listItemColors(),
        interactionSource = interactionSource,
        modifier = modifier,
    )
}

@Composable
fun ListItemSeries(
    dense: Boolean,
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val title =
        remember(item, dense) {
            if (item != null) {
                if (dense) {
                    "${item.title} (${item.subtitle})"
                } else {
                    item.title ?: ""
                }
            } else {
                ""
            }
        }
    val supportingContent =
        remember(item, dense) {
            if (!dense) {
                @Composable {
                    Text(item?.subtitle ?: "")
                }
            } else {
                null
            }
        }
    val trailingContent =
        remember(item) {
            val unplayedItemCount = item?.data?.userData?.unplayedItemCount
            if (unplayedItemCount != null && unplayedItemCount > 0) {
                @Composable { Text(unplayedItemCount.toString()) }
            } else if (item?.played == true) {
                @Composable { WatchedIcon(padding = 0.dp) }
            } else {
                null
            }
        }
    ListItemWrapper(
        dense = dense,
        selected = false,
        enabled = true,
        onClick = onClick,
        onLongClick = onLongClick,
        leadingContent = {
            if (item?.favorite == true) {
                FavoriteIndicator()
            }
        },
        headlineContent = {
            TitleContent(title, interactionSource)
        },
        supportingContent = supportingContent,
        trailingContent = trailingContent,
        scale = scale,
        colors = listItemColors(),
        interactionSource = interactionSource,
        modifier = modifier,
    )
}

@Composable
fun ListItemAlbum(
    dense: Boolean,
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val title =
        remember(item, dense) {
            if (dense && item != null && item.title != null && item.data.productionYear != null) {
                "${item.title} (${item.data.productionYear})"
            } else {
                item?.title ?: ""
            }
        }
    val supportingContent =
        remember(item, dense) {
            if (!dense && item?.subtitle != null) {
                @Composable {
                    Text(item.subtitle ?: "")
                }
            } else {
                null
            }
        }
    val overlineContent =
        remember(item, dense) {
            if (!dense && item?.data?.albumArtist != null) {
                @Composable {
                    Text(item.data.albumArtist ?: "")
                }
            } else {
                null
            }
        }
    val trailingContent =
        remember(item) {
            if (item?.type == BaseItemKind.MUSIC_ALBUM) {
                @Composable {
                    Text(
                        text =
                            item
                                .data
                                .runTimeTicks
                                ?.ticks
                                ?.roundMinutes
                                ?.toString() ?: "",
                    )
                }
            } else {
                null
            }
        }
    ListItemWrapper(
        dense = dense,
        selected = false,
        enabled = true,
        onClick = onClick,
        onLongClick = onLongClick,
        leadingContent = {
            if (item?.favorite == true) {
                FavoriteIndicator()
            }
        },
        headlineContent = { TitleContent(title, interactionSource) },
        supportingContent = supportingContent,
        trailingContent = trailingContent,
        overlineContent = overlineContent,
        scale = scale,
        colors = listItemColors(),
        interactionSource = interactionSource,
        modifier = modifier,
    )
}

@Composable
private fun TitleContent(
    title: String?,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    var focusedAfterDelay by remember { mutableStateOf(false) }

    val hideOverlayDelay = 500L
    if (focused) {
        LaunchedEffect(Unit) {
            delay(hideOverlayDelay)
            if (focused) {
                focusedAfterDelay = true
            } else {
                focusedAfterDelay = false
            }
        }
    } else {
        focusedAfterDelay = false
    }
    Text(
        text = title ?: "",
        modifier = modifier.enableMarquee(focusedAfterDelay),
    )
}

@Composable
fun listItemColors() =
    ListItemDefaults.colors(
        containerColor =
            MaterialTheme.colorScheme
                .surfaceColorAtElevation(1.dp)
                .copy(alpha = .5f),
    )

private val scale = ListItemDefaults.scale(focusedScale = 1f, pressedScale = .95f)
