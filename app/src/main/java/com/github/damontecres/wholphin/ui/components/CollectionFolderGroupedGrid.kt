package com.github.damontecres.wholphin.ui.components

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.detail.GridItemDetails
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.main.HomePageHeader
import com.github.damontecres.wholphin.ui.playback.isBackwardButton
import com.github.damontecres.wholphin.ui.playback.isForwardButton
import com.github.damontecres.wholphin.ui.playback.isPlayKeyUp
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.ScrollToTopBringIntoViewSpec
import com.github.damontecres.wholphin.util.ExceptionHandler
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
 * A position in a [CollectionFolderGroupedGrid]: which group, and which entry in that group
 */
private data class GroupPosition(
    val group: Int,
    val entry: Int,
)

/**
 * A grid where each [ItemGroup] has a heading and starts on a new row, so a group is never split
 * across rows with another group. A list of the group names on the side jumps to each group.
 *
 * Supports the same remote controls as [com.github.damontecres.wholphin.ui.detail.CardGrid]:
 * - Back jumps to the top, long press Back returns to the previous position
 * - Fast forward/rewind jump to the next/previous group
 * - Typing a letter jumps to the first group starting with that letter
 * - Play plays the focused item
 *
 * An item can be in more than one group. Indexes passed to the callbacks are always the item's
 * index in the full, ungrouped list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionFolderViewScope.CollectionFolderGroupedGrid(
    preferences: UserPreferences,
    focusedItem: BaseItem?,
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

    // Which group and which entry in it has focus (an item can be in several groups)
    var focusedGroup by rememberSaveable {
        mutableIntStateOf(
            groups.indexOfFirst { group -> group.entries.any { it.index == initialPosition } }.coerceAtLeast(0),
        )
    }
    var focusedEntry by rememberSaveable {
        mutableIntStateOf(
            groups
                .getOrNull(focusedGroup)
                ?.entries
                ?.indexOfFirst { it.index == initialPosition }
                ?.coerceAtLeast(0) ?: 0,
        )
    }
    // Where focus was before the most recent move, for long press Back
    var previousPosition by remember { mutableStateOf<GroupPosition?>(null) }

    val gridState =
        rememberLazyGridState(
            initialFirstVisibleItemIndex = headerPositions.getOrElse(focusedGroup) { 0 },
        )
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }

    // Wait for a recomposition after scrolling before focusing the target
    val jumpFocusRequester = remember { FocusRequester() }
    var jumpTarget by remember { mutableStateOf<GroupPosition?>(null) }
    LaunchedEffect(jumpTarget) {
        if (jumpTarget != null) {
            jumpFocusRequester.tryRequestFocus()
            jumpTarget = null
        }
    }

    fun jumpTo(
        group: Int,
        entry: Int,
    ) {
        if (groups.isEmpty()) return
        val targetGroup = group.coerceIn(0, groups.lastIndex)
        val targetEntry = entry.coerceIn(0, groups[targetGroup].entries.lastIndex)
        scope.launch(ExceptionHandler()) {
            // Show the group's heading when jumping to the first row of a group
            val gridPosition =
                if (targetEntry < columns) {
                    headerPositions[targetGroup]
                } else {
                    headerPositions[targetGroup] + 1 + targetEntry
                }
            gridState.scrollToItem(gridPosition)
            jumpTarget = GroupPosition(targetGroup, targetEntry)
        }
    }

    var longPressing by remember { mutableStateOf(false) }
    val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
    val density = LocalDensity.current
    var cardWidthPx by remember { mutableIntStateOf(0) }

    Column(modifier = modifier) {
        AnimatedVisibility(viewOptions.showDetails) {
            HomePageHeader(
                item = focusedItem,
                showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(HeaderUtils.padding),
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onKeyEvent {
                        if (it.key == Key.Back && it.nativeKeyEvent.isLongPress) {
                            longPressing = true
                            previousPosition?.let { previous -> jumpTo(previous.group, previous.entry) }
                            return@onKeyEvent true
                        } else if (it.type == KeyEventType.KeyUp) {
                            if (longPressing && it.key == Key.Back) {
                                longPressing = false
                                return@onKeyEvent true
                            }
                            longPressing = false
                        }
                        if (it.type != KeyEventType.KeyUp) {
                            false
                        } else if (it.key == Key.Back && (focusedGroup > 0 || focusedEntry > 0)) {
                            jumpTo(0, 0)
                            true
                        } else if (isPlayKeyUp(it)) {
                            groups
                                .getOrNull(focusedGroup)
                                ?.entries
                                ?.getOrNull(focusedEntry)
                                ?.let { entry ->
                                    if (entry.item.playable) onClickPlay.invoke(entry.index, entry.item)
                                }
                            true
                        } else if (isForwardButton(it)) {
                            jumpTo(focusedGroup + 1, 0)
                            true
                        } else if (isBackwardButton(it)) {
                            jumpTo(focusedGroup - 1, 0)
                            true
                        } else if (it.nativeKeyEvent.keyCode in (KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z)) {
                            val letter = it.nativeKeyEvent.unicodeChar.toChar().uppercaseChar()
                            val group =
                                groups.indexOfFirst { g ->
                                    g.title?.firstOrNull()?.uppercaseChar() == letter
                                }
                            if (group >= 0) jumpTo(group, 0)
                            true
                        } else {
                            false
                        }
                    },
        ) {
            Box(modifier = Modifier.weight(1f)) {
                CompositionLocalProvider(
                    LocalBringIntoViewSpec provides
                        remember(viewOptions, density) {
                            if (viewOptions.showDetails) {
                                ScrollToTopBringIntoViewSpec(with(density) { spacing.toPx() })
                            } else {
                                defaultBringIntoViewSpec
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
                                .fillMaxSize()
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
                                            groupIndex == focusedGroup && entryIndex == focusedEntry,
                                            Modifier
                                                .focusRequester(firstFocus)
                                                .focusRequester(gridFocusRequester),
                                        ).ifElse(
                                            jumpTarget == GroupPosition(groupIndex, entryIndex),
                                            Modifier.focusRequester(jumpFocusRequester),
                                        ).onFocusChanged {
                                            if (it.isFocused) {
                                                if (groupIndex != focusedGroup || entryIndex != focusedEntry) {
                                                    previousPosition = GroupPosition(focusedGroup, focusedEntry)
                                                }
                                                focusedGroup = groupIndex
                                                focusedEntry = entryIndex
                                                // Callers treat position < columns as "in the first row"
                                                val inFirstRow = groupIndex == 0 && entryIndex < columns
                                                positionCallback?.invoke(
                                                    if (inFirstRow) entry.index + 1 else 0,
                                                    entry.index,
                                                )
                                            }
                                        }
                                cardContent.invoke(
                                    this@CollectionFolderGroupedGrid,
                                    GridItemDetails(
                                        item = entry.item,
                                        index = entry.index,
                                        onClick = { onClickItem.invoke(entry.index, entry.item) },
                                        onLongClick = { onLongClickItem.invoke(entry.index, entry.item) },
                                        widthPx = cardWidthPx,
                                        mod = mod,
                                    ),
                                )
                            }
                        }
                    }
                }
                // Footer, like the regular grid: the focused group and the position in it
                groups.getOrNull(focusedGroup)?.let { group ->
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .background(AppColors.TransparentBlack50),
                    ) {
                        Text(
                            text =
                                (group.title ?: stringResource(R.string.playlist_group_other)) +
                                    " · ${focusedEntry + 1} / ${group.entries.size}",
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                }
            }
            GroupJumpList(
                groups = groups,
                currentGroup = focusedGroup,
                onClickGroup = { jumpTo(it, 0) },
                modifier =
                    Modifier
                        .align(Alignment.CenterVertically)
                        .padding(start = 16.dp)
                        .width(160.dp),
            )
        }
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
