package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun <T> ItemRow(
    title: String,
    items: List<T?>,
    onClickItem: (Int, T) -> Unit,
    onLongClickItem: (Int, T) -> Unit,
    cardContent: @Composable (
        index: Int,
        item: T?,
        modifier: Modifier,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
) {
    val state = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(horizontalPadding),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRestorer(firstFocus),
        ) {
            itemsIndexed(items) { index, item ->
                val cardModifier =
                    if (index == 0) {
                        Modifier.focusRequester(firstFocus)
                    } else {
                        Modifier
                    }
                cardContent.invoke(
                    index,
                    item,
                    cardModifier,
                    { if (item != null) onClickItem.invoke(index, item) },
                    { if (item != null) onLongClickItem.invoke(index, item) },
                )
            }
        }
    }
}
