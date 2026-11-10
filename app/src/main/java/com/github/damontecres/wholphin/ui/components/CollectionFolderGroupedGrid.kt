package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.detail.GridItemDetails
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.playback.isPlayKeyUp
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.launch

/**
 * A titled group of items for [CollectionFolderGroupedGrid]. A null title is shown as "Other".
 */
data class ItemGroup(
    val title: String?,
    val entries: List<GroupEntry>,
)

/**
 * An item and its index in the full, ungrouped list of items
 */
data class GroupEntry(
    val index: Int,
    val item: BaseItem,
)

/**
 * A grid where each [ItemGroup] has a heading and starts on a new row, so a group is never split
 * across rows with another group. A list of the group names on the side jumps to each group.
 *
 * An item can be in more than one group. Indexes passed to the callbacks are always the item's
 * index in the full, ungrouped list.
 */
@Composable
fun CollectionFolderViewScope.CollectionFolderGroupedGrid(
    groups: List<ItemGroup>,
    onClickItem: (Int, BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    onClickPlay: (Int, BaseItem) -> Unit,
    initialPosition: Int,
    gridFocusRequester: FocusRequester,
    cardContent: @Composable CollectionFolderViewScope.(GridItemDetails<BaseItem>) -> Unit,
    modifier: Modifier = Modifier,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
) {
    val columns = viewOptions.columns
    val spacing = viewOptions.spacing.dp
    // The grid position of each group's heading
    val headerPositions =
        remember(groups) {
            groups.runningFold(0) { position, group -> position + 1 + group.entries.size }.dropLast(1)
        }

    // Which item and which copy of it (by group) has focus
    var focusedIndex by rememberSaveable { mutableIntStateOf(initialPosition) }
    var focusedGroup by rememberSaveable {
        mutableIntStateOf(
            groups.indexOfFirst { group -> group.entries.any { it.index == initialPosition } }.coerceAtLeast(0),
        )
    }
    val gridState =
        rememberLazyGridState(
            initialFirstVisibleItemIndex = headerPositions.getOrElse(focusedGroup) { 0 },
        )
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val groupFocusRequesters = remember(groups.size) { List(groups.size) { FocusRequester() } }

    // Wait for a recomposition after scrolling before focusing a group's first item
    var pendingGroupFocus by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pendingGroupFocus) {
        pendingGroupFocus?.let { groupFocusRequesters.getOrNull(it)?.tryRequestFocus() }
        pendingGroupFocus = null
    }

    val density = LocalDensity.current
    var cardWidthPx by remember { mutableIntStateOf(0) }

    Row(
        modifier =
            modifier
                .fillMaxSize()
                .onKeyEvent {
                    if (isPlayKeyUp(it)) {
                        groups
                            .getOrNull(focusedGroup)
                            ?.entries
                            ?.firstOrNull { entry -> entry.index == focusedIndex }
                            ?.let { entry ->
                                if (entry.item.playable) onClickPlay.invoke(entry.index, entry.item)
                            }
                        true
                    } else {
                        false
                    }
                },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            state = gridState,
            contentPadding = PaddingValues(vertical = 16.dp),
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusGroup()
                    .focusRestorer(firstFocus)
                    .onSizeChanged {
                        val spacingPx = with(density) { spacing.toPx() }
                        cardWidthPx = ((it.width - spacingPx * (columns - 1)) / columns).toInt()
                    },
        ) {
            groups.forEachIndexed { groupIndex, group ->
                item(
                    key = "header_$groupIndex",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    Text(
                        text = group.title ?: stringResource(R.string.playlist_group_other),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
                itemsIndexed(
                    items = group.entries,
                    key = { _, entry -> "${groupIndex}_${entry.index}" },
                ) { entryIndex, entry ->
                    val mod =
                        Modifier
                            .ifElse(
                                entry.index == focusedIndex && groupIndex == focusedGroup,
                                Modifier
                                    .focusRequester(firstFocus)
                                    .focusRequester(gridFocusRequester),
                            ).ifElse(
                                entryIndex == 0,
                                Modifier.focusRequester(groupFocusRequesters[groupIndex]),
                            ).onFocusChanged {
                                if (it.isFocused) {
                                    focusedIndex = entry.index
                                    focusedGroup = groupIndex
                                    // Callers treat position < columns as "in the first row"
                                    val inFirstRow = groupIndex == 0 && entryIndex < columns
                                    positionCallback?.invoke(if (inFirstRow) entry.index + 1 else 0, entry.index)
                                }
                            }
                    cardContent.invoke(
                        this@CollectionFolderGroupedGrid,
                        GridItemDetails(
                            item = entry.item,
                            index = entry.index,
                            onClick = {
                                focusedIndex = entry.index
                                focusedGroup = groupIndex
                                onClickItem.invoke(entry.index, entry.item)
                            },
                            onLongClick = { onLongClickItem.invoke(entry.index, entry.item) },
                            widthPx = cardWidthPx,
                            mod = mod,
                        ),
                    )
                }
            }
        }
        GroupJumpList(
            groups = groups,
            currentGroup = focusedGroup,
            onClickGroup = { groupIndex ->
                scope.launch {
                    gridState.scrollToItem(headerPositions[groupIndex])
                    pendingGroupFocus = groupIndex
                }
            },
            modifier =
                Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = 16.dp)
                    .width(160.dp),
        )
    }
}

/**
 * The list of group names used to jump to a group, similar to the alphabet buttons
 */
@Composable
private fun GroupJumpList(
    groups: List<ItemGroup>,
    currentGroup: Int,
    onClickGroup: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier,
    ) {
        itemsIndexed(groups) { groupIndex, group ->
            DenseListItem(
                selected = groupIndex == currentGroup,
                onClick = { onClickGroup.invoke(groupIndex) },
                headlineContent = {
                    Text(
                        text = group.title ?: stringResource(R.string.playlist_group_other),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}
