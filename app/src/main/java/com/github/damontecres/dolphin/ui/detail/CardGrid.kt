package com.github.damontecres.dolphin.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.R
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.ui.AppColors
import com.github.damontecres.dolphin.ui.FontAwesome
import com.github.damontecres.dolphin.ui.cards.ItemCard
import com.github.damontecres.dolphin.ui.ifElse
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.ui.playback.isBackwardButton
import com.github.damontecres.dolphin.ui.playback.isForwardButton
import com.github.damontecres.dolphin.ui.playback.isPlayKeyUp
import com.github.damontecres.dolphin.ui.tryRequestFocus
import com.github.damontecres.dolphin.util.ItemPager
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.max

private const val DEBUG = false

@Composable
fun CardGrid(
    pager: ItemPager,
    itemOnClick: (BaseItem) -> Unit,
    longClicker: (BaseItem) -> Unit,
    letterPosition: suspend (Char) -> Int,
    requestFocus: Boolean,
    gridFocusRequester: FocusRequester,
    navigationManager: NavigationManager,
    modifier: Modifier = Modifier,
    initialPosition: Int = 0,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
) {
    val startPosition = initialPosition.coerceIn(0, (pager.size - 1).coerceAtLeast(0))
    val columns = 5

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val zeroFocus = remember { FocusRequester() }
    var previouslyFocusedIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusedIndex by rememberSaveable { mutableIntStateOf(initialPosition) }
    var focusedIndexOnExit by rememberSaveable { mutableIntStateOf(-1) }

    // Tracks whether the very first requestFocus has run, if the caller isn't requesting focus,
    // then the first time will never run
    var hasRequestFocusRun by rememberSaveable { mutableStateOf(!requestFocus) }
    var savedFocusedIndex by rememberSaveable { mutableIntStateOf(-1) }

    if (DEBUG) {
        Timber.d(
            "Grid: hasRun=$hasRequestFocusRun, requestFocus=$requestFocus, initialPosition=$initialPosition, focusedIndex=$focusedIndex",
        )
    }

    LaunchedEffect(Unit) {
        if (!hasRequestFocusRun) {
            // On very first composition, if parent wants to focus on the grid, scroll to the item
            if (requestFocus && initialPosition >= 0) {
                if (DEBUG) {
                    Timber.d(
                        "focus on startPosition=$startPosition, from initialPosition=$initialPosition",
                    )
                }
                focusedIndex = startPosition
                gridState.scrollToItem(startPosition, 0)
                firstFocus.tryRequestFocus()
            }
        } else {
            val index = savedFocusedIndex
            if (DEBUG) Timber.d("savedFocusedIndex=$index")
            if (index in 0..<pager.size) {
                // If this is a recomposition, but not the first
                // focus on the restored index
                // gridState.scrollToItem(index, -columns)
                firstFocus.tryRequestFocus()
            }
            savedFocusedIndex = -1
        }
//        hasRun = true
    }

    val context = LocalContext.current
    val showJumpButtons = true

    var alphabetFocus by remember { mutableStateOf(false) }
    val focusOn = { index: Int ->
        if (DEBUG) Timber.v("focusOn: focusedIndex=$focusedIndex, index=$index")
        if (index != focusedIndex) {
            previouslyFocusedIndex = focusedIndex
        }
        focusedIndex = index
    }

    // Wait for a recomposition to focus
    LaunchedEffect(alphabetFocus) {
        if (alphabetFocus) {
            firstFocus.tryRequestFocus()
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
        scope.launch {
            val newPosition =
                (gridState.firstVisibleItemIndex + jump).coerceIn(0..<pager.size)
            if (DEBUG) Timber.d("newPosition=$newPosition")
            savedFocusedIndex = newPosition
            focusOn(newPosition)
            gridState.scrollToItem(newPosition, 0)
        }
    }
    val jumpToTop = {
        scope.launch {
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
                            scope.launch {
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
        if (showJumpButtons && pager.size > 0) {
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
                        .focusRequester(gridFocusRequester)
                        .focusProperties {
                            onExit = {
                                // Leaving the grid, so "forget" the position
                                focusedIndexOnExit = focusedIndex
                                focusedIndex = -1
                                savedFocusedIndex = -1
                            }
                            onEnter = {
                                focusedIndexOnExit = -1
                                if (focusedIndex < 0 && gridState.firstVisibleItemIndex <= startPosition) {
                                    focusedIndex = startPosition
                                    firstFocus.tryRequestFocus()
                                }
                            }
                        },
            ) {
                items(pager.size) { index ->
                    val mod =
                        if (index == savedFocusedIndex) {
                            if (DEBUG) Timber.d("Adding firstFocus to itemClickedIndex $index")
                            Modifier.focusRequester(firstFocus)
                        } else if ((index == focusedIndex) or (focusedIndex < 0 && index == 0)) {
                            if (DEBUG) Timber.d("Adding firstFocus to focusedIndex $index")
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
                        }
                    // TODO
                    val item = pager[index] // ?.let { convertModel(it, api) }
                    if (!hasRequestFocusRun && requestFocus && initialPosition >= 0) {
                        // On very first composition, if parent wants to focus on the grid, do so
                        LaunchedEffect(Unit) {
                            if (DEBUG) {
                                Timber.d(
                                    "non-null focus on startPosition=$startPosition, from initialPosition=$initialPosition",
                                )
                            }
                            // focus on startPosition
                            gridState.scrollToItem(startPosition, 0)
                            firstFocus.tryRequestFocus()
                            hasRequestFocusRun = true
                        }
                    }
                    ItemCard(
                        modifier =
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
                                        savedFocusedIndex = index
                                        // Was focused on this, so mark unfocused
                                        focusedIndex = -1
                                    }
                                },
                        item = item,
                        onClick = { if (item != null) itemOnClick.invoke(item) },
                        onLongClick = { if (item != null) longClicker.invoke(item) },
                    )
                }
            }
            if (pager.size == 0) {
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
                    val index =
                        if (focusedIndex >= 0) {
                            focusedIndex + 1
                        } else {
                            max(savedFocusedIndex, focusedIndexOnExit) + 1
                        }
                    Text(
                        modifier = Modifier.padding(4.dp),
                        color = MaterialTheme.colorScheme.onBackground,
                        text = "$index / ${pager.size}",
                    )
                }
            }
        }
        // Letters
        if (pager.isNotEmpty() && false) {
            // TODO
            AlphabetButtons(
                modifier = Modifier.align(Alignment.CenterVertically),
                letterClicked = { letter ->
                    scope.launch {
                        val jumpPosition = letterPosition.invoke(letter)
                        Timber.d("Alphabet jump to $jumpPosition")
                        gridState.scrollToItem(jumpPosition)
                        focusOn(jumpPosition)
                        alphabetFocus = true
//                        firstFocus.tryRequestFocus()
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

@Composable
fun AlphabetButtons(
    letterClicked: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
//    LazyColumn(modifier = modifier) {
//        items(
//            AlphabetSearchUtils.LETTERS.length,
//            key = { AlphabetSearchUtils.LETTERS[it] },
//        ) { index ->
//            Button(
//                modifier =
//                    Modifier.size(24.dp),
//                contentPadding = PaddingValues(2.dp),
//                onClick = {
//                    letterClicked.invoke(AlphabetSearchUtils.LETTERS[index])
//                },
//            ) {
//                Text(text = AlphabetSearchUtils.LETTERS[index].toString())
//            }
//        }
//    }
}
