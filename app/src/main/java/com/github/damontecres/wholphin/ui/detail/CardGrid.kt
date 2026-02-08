package com.github.damontecres.wholphin.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.playback.isBackwardButton
import com.github.damontecres.wholphin.ui.playback.isForwardButton
import com.github.damontecres.wholphin.ui.playback.isPlayKeyUp
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val DEBUG = false

interface CardGridItem {
    val gridId: String
    val playable: Boolean
    val sortName: String
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T : CardGridItem> CardGrid(
    pager: List<T?>,
    onClickItem: (Int, T) -> Unit,
    onLongClickItem: (Int, T) -> Unit,
    onClickPlay: (Int, T) -> Unit,
    letterPosition: suspend (Char) -> Int,
    gridFocusRequester: FocusRequester,
    showJumpButtons: Boolean,
    showLetterButtons: Boolean,
    modifier: Modifier = Modifier,
    initialPosition: Int = 0,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    cardContent: @Composable (
        item: T?,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        mod: Modifier,
    ) -> Unit = { item, onClick, onLongClick, mod ->
        GridCard(
            item = item as BaseItem?,
            onClick = onClick,
            onLongClick = onLongClick,
            imageContentScale = ContentScale.FillBounds,
            modifier = mod,
        )
    },
    columns: Int = 6,
    spacing: Dp = 16.dp,
) {
    val startPosition = initialPosition.coerceIn(0, (pager.size - 1).coerceAtLeast(0))

    val fractionCacheWindow = LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 0.5f)
    var focusedIndex by rememberSaveable { mutableIntStateOf(initialPosition) }
    val gridState =
        rememberLazyGridState(
            cacheWindow = fractionCacheWindow,
            initialFirstVisibleItemIndex = focusedIndex,
        )
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val zeroFocus = remember { FocusRequester() }
    var previouslyFocusedIndex by rememberSaveable { mutableIntStateOf(0) }

    var alphabetFocus by remember { mutableStateOf(false) }
    val focusOn = { index: Int ->
        if (DEBUG) Timber.v("focusOn: focusedIndex=$focusedIndex, index=$index")
        if (index != focusedIndex) {
            previouslyFocusedIndex = focusedIndex
        }
        focusedIndex = index
    }

    // Wait for a recomposition to focus
    val alphabetFocusRequester = remember { FocusRequester() }
    LaunchedEffect(alphabetFocus) {
        if (alphabetFocus) {
            alphabetFocusRequester.tryRequestFocus()
        }
        alphabetFocus = false
    }

    val useBackToJump = true // uiConfig.preferences.interfacePreferences.scrollTopOnBack
    val showFooter = true // uiConfig.preferences.interfacePreferences.showPositionFooter
    val useJumpRemoteButtons = true // uiConfig.preferences.interfacePreferences.pageWithRemoteButtons
    val jump2 =
        remember {
            if (pager.size >= 25_000) {
                columns * 2000
            } else if (pager.size >= 7_000) {
                columns * 200
            } else if (pager.size >= 2_000) {
                columns * 50
            } else {
                columns * 20
            }
        }
    val jump1 =
        remember {
            if (pager.size >= 25_000) {
                columns * 500
            } else if (pager.size >= 7_000) {
                columns * 50
            } else if (pager.size >= 2_000) {
                columns * 15
            } else {
                columns * 6
            }
        }

    val jump = { jump: Int ->
        scope.launch(ExceptionHandler()) {
            val newPosition =
                (gridState.firstVisibleItemIndex + jump).coerceIn(0..<pager.size)
            if (DEBUG) Timber.d("newPosition=$newPosition")
            focusOn(newPosition)
            gridState.scrollToItem(newPosition, 0)
        }
    }
    val jumpToTop = {
        scope.launch(ExceptionHandler()) {
            if (focusedIndex < (columns * 6)) {
                // If close, animate the scroll
                gridState.animateScrollToItem(0, 0)
            } else {
                gridState.scrollToItem(0, 0)
            }
            focusOn(0)
            zeroFocus.tryRequestFocus()
        }
    }

    if (pager.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.fillMaxSize(),
        ) {
            Text(
                text = stringResource(R.string.no_results),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        var longPressing by remember { mutableStateOf(false) }
        Row(
//        horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                modifier
                    .fillMaxSize()
                    .onKeyEvent {
                        if (DEBUG) Timber.d("onKeyEvent: ${it.nativeKeyEvent}")
                        if (useBackToJump && it.key == Key.Back && it.nativeKeyEvent.isLongPress) {
                            longPressing = true
                            val newPosition = previouslyFocusedIndex
                            if (DEBUG) Timber.d("Back long pressed: newPosition=$newPosition")
                            if (newPosition > 0) {
                                focusOn(newPosition)
                                scope.launch(ExceptionHandler()) {
                                    gridState.scrollToItem(newPosition, -columns)
                                    firstFocus.tryRequestFocus()
                                }
                            }
                            return@onKeyEvent true
                        } else if (it.type == KeyEventType.KeyUp) {
                            if (longPressing && it.key == Key.Back) {
                                longPressing = false
                                return@onKeyEvent true
                            }
                            longPressing = false
                        }
                        if (it.type != KeyEventType.KeyUp) {
                            return@onKeyEvent false
                        } else if (useBackToJump && it.key == Key.Back && focusedIndex > 0) {
                            jumpToTop()
                            return@onKeyEvent true
                        } else if (isPlayKeyUp(it)) {
                            val item = pager.getOrNull(focusedIndex)
                            if (item?.playable == true) {
                                Timber.v("Clicked play on ${item.gridId}")
                                onClickPlay.invoke(focusedIndex, item)
                            }
                            return@onKeyEvent true
                        } else if (useJumpRemoteButtons && isForwardButton(it)) {
                            jump(jump1)
                            return@onKeyEvent true
                        } else if (useJumpRemoteButtons && isBackwardButton(it)) {
                            jump(-jump1)
                            return@onKeyEvent true
                        } else {
                            return@onKeyEvent false
                        }
                    },
        ) {
            if (showJumpButtons && pager.isNotEmpty()) {
                JumpButtons(
                    jump1 = jump1,
                    jump2 = jump2,
                    jumpClick = { jump(it) },
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            Box(
                modifier = Modifier.weight(1f),
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
                            .focusProperties {
                                onExit = {
                                    // Leaving the grid, so "forget" the position
//                                focusedIndex = -1
                                }
                                onEnter = {
                                    if (focusedIndex < 0 && gridState.firstVisibleItemIndex <= startPosition) {
                                        focusedIndex = startPosition
                                    }
                                }
                            },
                ) {
                    items(pager.size) { index ->
                        val mod =
                            if ((index == focusedIndex) or (focusedIndex < 0 && index == 0)) {
                                if (DEBUG) Timber.d("Adding firstFocus to focusedIndex $index")
                                Modifier
                                    .focusRequester(firstFocus)
                                    .focusRequester(gridFocusRequester)
                                    .focusRequester(alphabetFocusRequester)
                            } else {
                                Modifier
                            }
                        val item = pager[index]
                        cardContent(
                            item,
                            {
                                if (item != null) {
                                    focusedIndex = index
                                    onClickItem.invoke(index, item)
                                }
                            },
                            { if (item != null) onLongClickItem.invoke(index, item) },
                            mod
                                .ifElse(index == 0, Modifier.focusRequester(zeroFocus))
                                .onFocusChanged { focusState ->
                                    if (DEBUG) {
                                        Timber.v(
                                            "$index isFocused=${focusState.isFocused}",
                                        )
                                    }
                                    if (focusState.isFocused) {
                                        // Focused, so set that up
                                        focusOn(index)
                                        positionCallback?.invoke(columns, index)
                                    } else if (focusedIndex == index) {
//                                        savedFocusedIndex = index
//                                        // Was focused on this, so mark unfocused
//                                        focusedIndex = -1
                                    }
                                },
                        )
                    }
                }
                if (pager.isEmpty()) {
//                focusedIndex = -1
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = stringResource(R.string.no_results),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                if (showFooter) {
                    // Footer
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .background(AppColors.TransparentBlack50),
                    ) {
                        val index = (focusedIndex + 1).takeIf { it > 0 } ?: "?"
//                        if (focusedIndex >= 0) {
//                            focusedIndex + 1
//                        } else {
//                            max(savedFocusedIndex, focusedIndexOnExit) + 1
//                        }
                        Text(
                            modifier = Modifier.padding(4.dp),
                            color = MaterialTheme.colorScheme.onBackground,
                            text = "$index / ${pager.size}",
                        )
                    }
                }
            }
            val context = LocalContext.current
            val letters = context.getString(R.string.jump_letters)
            // Letters
            val currentLetter =
                remember(focusedIndex) {
                    pager
                        .getOrNull(focusedIndex)
                        ?.sortName
                        ?.firstOrNull()
                        ?.uppercaseChar()
                        ?.let {
                            if (it >= '0' && it <= '9') {
                                '#'
                            } else if (it >= 'A' && it <= 'Z') {
                                it
                            } else {
                                null
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
                    // Add end padding to push away from edge
                    letterClicked = { letter ->
                        scope.launch(ExceptionHandler()) {
                            val jumpPosition =
                                withContext(Dispatchers.IO) {
                                    letterPosition.invoke(letter)
                                }
                            Timber.d("Alphabet jump to $jumpPosition")
                            if (jumpPosition >= 0) {
                                pager.getOrNull(jumpPosition)
                                gridState.scrollToItem(jumpPosition)
                                focusOn(jumpPosition)
                                alphabetFocus = true
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun JumpButtons(
    jump1: Int,
    jump2: Int,
    jumpClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        JumpButton(R.string.fa_angles_up, -jump2, jumpClick)
        JumpButton(R.string.fa_angle_up, -jump1, jumpClick)
        JumpButton(R.string.fa_angle_down, jump1, jumpClick)
        JumpButton(R.string.fa_angles_down, jump2, jumpClick)
    }
}

@Composable
fun JumpButton(
    @StringRes stringRes: Int,
    jumpAmount: Int,
    jumpClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier.width(40.dp),
        contentPadding = PaddingValues(4.dp),
        onClick = {
            jumpClick.invoke(jumpAmount)
        },
    ) {
        Text(text = stringResource(stringRes), fontFamily = FontAwesome)
    }
}

@Composable
fun AlphabetButtons(
    letters: String,
    currentLetter: Char,
    letterClicked: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val index = letters.indexOf(currentLetter)
    LaunchedEffect(currentLetter) {
        scope.launch(ExceptionHandler()) {
            val firstVisibleItemIndex = listState.firstVisibleItemIndex
            val lastVisibleItemIndex =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: -1
            if (index !in firstVisibleItemIndex..lastVisibleItemIndex) {
                listState.animateScrollToItem(index)
            }
        }
    }
    // Focus & interaction states for each letter button
    val focusRequesters = remember { List(letters.length) { FocusRequester() } }
    val interactionSources = remember { List(letters.length) { MutableInteractionSource() } }

    // Track if the entire alphabet picker component has focus
    var alphabetPickerFocused by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 1.1.dp, horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(1.1.dp),
        state = listState,
        modifier =
            modifier
                .onFocusChanged { focusState ->
                    alphabetPickerFocused = focusState.hasFocus
                }.focusProperties {
                    onEnter = {
                        focusRequesters[index.coerceIn(0, letters.length - 1)].tryRequestFocus()
                    }
                },
    ) {
        items(
            letters.length,
            key = { letters[it] },
        ) { index ->
            val interactionSource = interactionSources[index]
            val focused by interactionSource.collectIsFocusedAsState()

            val isCurrentLetter = letters[index] == currentLetter
            // Apply alpha to individual items, but keep selected letter fully visible when picker is unfocused
            val itemAlpha =
                when {
                    isCurrentLetter && !alphabetPickerFocused -> 1f
                    alphabetPickerFocused -> .85f
                    else -> .25f
                }

            // Only show circle background for the current letter (or when focused)
            // Wrap in Box with clipping to prevent focus indicator from overflowing
            Box(
                modifier =
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .alpha(itemAlpha),
            ) {
                Button(
                    modifier =
                        Modifier
                            .size(14.dp)
                            .focusRequester(focusRequesters[index]),
                    contentPadding = PaddingValues(0.dp), // No padding to maximize text space
                    interactionSource = interactionSource,
                    onClick = {
                        letterClicked.invoke(letters[index])
                    },
                    colors =
                        if (isCurrentLetter || focused) {
                            // Use default button colors for current letter or focused
                            ButtonDefaults.colors()
                        } else {
                            // Transparent background for non-current letters (no circle)
                            ButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                ) {
                    // Use border color for selected letter when focused, tertiary for unfocused-selected
                    val color =
                        when {
                            isCurrentLetter && focused -> MaterialTheme.colorScheme.border
                            isCurrentLetter -> MaterialTheme.colorScheme.tertiary
                            focused -> LocalContentColor.current
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    Text(
                        text = letters[index].toString(),
                        color = color,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
