package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import timber.log.Timber

@Composable
fun TabRow(
    selectedTabIndex: Int,
    tabs: List<String>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember(tabs) { List(tabs.size) { FocusRequester() } }
    var rowHasFocus by remember { mutableStateOf(false) }
    LazyRow(
        modifier =
            modifier
                .onFocusChanged {
                    rowHasFocus = it.hasFocus
                }.focusProperties {
                    onEnter = {
                        focusRequesters.getOrNull(selectedTabIndex)?.tryRequestFocus()
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
                    Timber.v("Clicked $index")
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
    val focused by interactionSource.collectIsFocusedAsState()
    val backgroundColor =
        if (rowActive && focused) {
            MaterialTheme.colorScheme.border.copy(alpha = .33f)
        } else {
            Color.Transparent
        }
    val contentColor =
        if (rowActive || selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)
        }
    Box(
        modifier =
            modifier
                .clickable(enabled = true, interactionSource = interactionSource, onClick = onClick)
                .background(backgroundColor, shape = RoundedCornerShape(8.dp)),
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            color = contentColor,
            textDecoration = if (selected) TextDecoration.Underline else null,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@PreviewTvSpec
@Composable
private fun TabRowPreview() {
    WholphinTheme {
        TabRow(
            selectedTabIndex = 1,
            tabs = listOf("Tab 1", "Tab 2", "Tab 3"),
            onClick = {},
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
    }
}
