package com.github.damontecres.wholphin.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
fun TabRow(
    selectedTabIndex: Int,
    tabs: List<String>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    LaunchedEffect(selectedTabIndex) {
        state.animateScrollToItem(selectedTabIndex, -(state.layoutInfo.viewportSize.width / 3.5).toInt())
    }
    val focusRequesters = remember(tabs) { List(tabs.size) { FocusRequester() } }
    var rowHasFocus by remember { mutableStateOf(false) }
    LazyRow(
        state = state,
        modifier =
            modifier
                .onFocusChanged {
                    rowHasFocus = it.hasFocus
                }.focusGroup()
                .focusProperties {
                    onEnter = {
                        // If entering from left or right, use last or first tab
                        // Otherwise use the selected tab
                        val focusRequester =
                            if (requestedFocusDirection == FocusDirection.Left) {
                                focusRequesters.lastOrNull()
                            } else if (requestedFocusDirection == FocusDirection.Right) {
                                focusRequesters.firstOrNull()
                            } else {
                                focusRequesters.getOrNull(selectedTabIndex)
                            }
                        (focusRequester ?: FocusRequester.Default).tryRequestFocus()
                    }
                },
    ) {
        itemsIndexed(tabs) { index, tabTitle ->
            val interactionSource = remember { MutableInteractionSource() }
            Tab(
                title = tabTitle,
                selected = index == selectedTabIndex,
                rowActive = rowHasFocus,
                interactionSource = interactionSource,
                onClick = {
                    onClick.invoke(index)
                },
                modifier = Modifier.focusRequester(focusRequesters[index]),
            )
        }
    }
}

@Composable
fun Tab(
    title: String,
    selected: Boolean,
    rowActive: Boolean,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    var tabWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    val focused by interactionSource.collectIsFocusedAsState()
    val contentColor =
        if (rowActive || selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)
        }
    Box(
        modifier =
            modifier
                .clickable(
                    enabled = true,
                    interactionSource = interactionSource,
                    onClick = onClick,
                    indication = null,
                ).onGloballyPositioned {
                    tabWidth = with(density) { it.size.width.toDp() }
                },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier,
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = contentColor,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            TabIndicator(
                selected = selected,
                rowActive = rowActive,
                focused = focused,
                tabWidth = tabWidth,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
fun TabIndicator(
    selected: Boolean,
    rowActive: Boolean,
    focused: Boolean,
    tabWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val width by animateDpAsState(if (rowActive && focused) tabWidth else tabWidth * .25f)
    val backgroundColor =
        if (rowActive && focused) {
            MaterialTheme.colorScheme.border
        } else if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Color.Transparent
        }
    Box(
        modifier =
            modifier
                .height(2.dp)
                .fillMaxWidth()
                .width(width)
                .background(backgroundColor),
    )
}

@PreviewTvSpec
@Composable
private fun TabRowPreview() {
    WholphinTheme {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            TabRow(
                selectedTabIndex = 1,
                tabs = listOf("Tab 1", "Tab 2", "Tab 3"),
                onClick = {},
            )
            Tab(
                title = "This is a Tab",
                selected = true,
                rowActive = true,
                onClick = {},
                interactionSource = remember { MutableInteractionSource() },
                modifier = Modifier.width(120.dp),
            )
        }
    }
}
