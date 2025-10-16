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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
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
import kotlinx.coroutines.launch
import timber.log.Timber

private const val DEBUG = false

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardGrid(
    pager: List<BaseItem?>,
    onClickItem: (BaseItem) -> Unit,
    onLongClickItem: (BaseItem) -> Unit,
    letterPosition: suspend (Char) -> Int,
    gridFocusRequester: FocusRequester,
    showJumpButtons: Boolean,
    showLetterButtons: Boolean,
    modifier: Modifier = Modifier,
    initialPosition: Int = 0,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    cardContent: @Composable (
        item: BaseItem?,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        mod: Modifier,
    ) -> Unit = { item, onClick, onLongClick, mod ->
        GridCard(
            item = item,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = mod,
        )
    },
) {
    val startPosition = initialPosition.coerceIn(0, (pager.size - 1).coerceAtLeast(0))
    val columns = 6

    val fractionCacheWindow = LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 0.5f)
    val gridState = rememberLazyGridState(cacheWindow = fractionCacheWindow)
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val zeroFocus = remember { FocusRequester() }
    var previouslyFocusedIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusedIndex by rememberSaveable { mutableIntStateOf(initialPosition) }

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
                        // TODO play the focused item
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
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                state = gridState,
                contentPadding = PaddingValues(16.dp),
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
                                onClickItem.invoke(item)
                            }
                        },
                        { if (item != null) onLongClickItem.invoke(item) },
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
                        text = "No results",
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
        // Letters
        val currentLetter =
            remember(focusedIndex) {
                pager
                    .getOrNull(focusedIndex)
                    ?.data
                    ?.sortName
                    ?.first()
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
                    ?: LETTERS[0]
            }
        if (showLetterButtons && pager.isNotEmpty()) {
            AlphabetButtons(
                currentLetter = currentLetter,
                modifier = Modifier.align(Alignment.CenterVertically),
                letterClicked = { letter ->
                    scope.launch(ExceptionHandler()) {
                        val jumpPosition = letterPosition.invoke(letter)
                        Timber.d("Alphabet jump to $jumpPosition")
                        if (jumpPosition >= 0) {
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

private const val LETTERS = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ"

@Composable
fun AlphabetButtons(
    currentLetter: Char,
    letterClicked: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val index = LETTERS.indexOf(currentLetter)
    LaunchedEffect(currentLetter) {
        scope.launch(ExceptionHandler()) {
            val firstVisibleItemIndex = listState.firstVisibleItemIndex
            val lastVisibleItemIndex =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: -1
            if (index < firstVisibleItemIndex || index > lastVisibleItemIndex) {
                listState.animateScrollToItem(index)
            }
        }
    }
    val focusRequesters = remember { List(LETTERS.length) { FocusRequester() } }
    LazyColumn(
        state = listState,
        modifier =
            modifier.focusProperties {
                onEnter = {
                    focusRequesters[index.coerceIn(0, LETTERS.length - 1)].tryRequestFocus()
                }
            },
    ) {
        items(
            LETTERS.length,
            key = { LETTERS[it] },
        ) { index ->
            val interactionSource = remember { MutableInteractionSource() }
            val focused by interactionSource.collectIsFocusedAsState()
            Button(
                modifier =
                    Modifier
                        .size(24.dp)
                        .focusRequester(focusRequesters[index]),
                contentPadding = PaddingValues(2.dp),
                interactionSource = interactionSource,
                onClick = {
                    letterClicked.invoke(LETTERS[index])
                },
            ) {
                val color =
                    if (!focused && LETTERS[index] == currentLetter) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        LocalContentColor.current
                    }
                Text(
                    text = LETTERS[index].toString(),
                    color = color,
                )
            }
        }
    }
}
