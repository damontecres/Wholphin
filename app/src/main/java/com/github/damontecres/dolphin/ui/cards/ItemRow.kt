package com.github.damontecres.dolphin.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.util.FocusPair

@Composable
fun ItemRow(
    title: String,
    items: List<BaseItem?>,
    onClickItem: (BaseItem) -> Unit,
    onLongClickItem: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    focusPair: FocusPair? = null,
    cardOnFocus: ((isFocused: Boolean, index: Int) -> Unit)? = null,
) {
    val state = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(focusPair?.column ?: 0) }
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
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(8.dp),
            modifier =
                Modifier
                    .padding(start = 16.dp)
                    .fillMaxWidth()
                    .focusRestorer(focusPair?.focusRequester ?: firstFocus),
        ) {
            itemsIndexed(items) { index, item ->
                val cardModifier =
                    if (index == 0 && focusPair == null) {
                        Modifier.focusRequester(firstFocus)
                    } else {
                        if (focusPair != null && focusPair.column == index) {
                            Modifier.focusRequester(focusPair.focusRequester)
                        } else {
                            Modifier
                        }
                    }.onFocusChanged {
                        if (it.isFocused) {
                            focusedIndex = index
                        }
                        cardOnFocus?.invoke(it.isFocused, index)
                    }
                ItemCard(
                    item = item,
                    onClick = { if (item != null) onClickItem.invoke(item) },
                    onLongClick = { if (item != null) onLongClickItem.invoke(item) },
                    modifier = cardModifier,
                )
            }
        }
    }
}
