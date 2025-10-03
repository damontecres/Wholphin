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
    cardContent: @Composable (
        index: Int,
        item: BaseItem?,
        modifier: Modifier,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
    ) -> Unit = { index, item, modifier, onClick, onLongClick ->
        ItemCard(
            item = item,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
        )
    },
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
                cardContent.invoke(
                    index,
                    item,
                    cardModifier,
                    { if (item != null) onClickItem.invoke(item) },
                    { if (item != null) onLongClickItem.invoke(item) },
                )
            }
        }
    }
}

@Composable
fun BannerItemRow(
    title: String,
    items: List<BaseItem?>,
    onClickItem: (BaseItem) -> Unit,
    onLongClickItem: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    focusPair: FocusPair? = null,
    cardOnFocus: ((isFocused: Boolean, index: Int) -> Unit)? = null,
    aspectRatioOverride: Float? = null,
) = ItemRow(
    title = title,
    items = items,
    onClickItem = onClickItem,
    onLongClickItem = onLongClickItem,
    modifier = modifier,
    cardContent = { index, item, modifier, onClick, onLongClick ->
        BannerCard(
            imageUrl = item?.imageUrl,
            aspectRatio =
                aspectRatioOverride ?: item?.data?.primaryImageAspectRatio?.toFloat() ?: (16f / 9),
            cornerText = item?.data?.indexNumber?.let { "E$it" },
            played = item?.data?.userData?.played ?: false,
            playPercent = item?.data?.userData?.playedPercentage ?: 0.0,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
            interactionSource = null,
        )
    },
    focusPair = focusPair,
    cardOnFocus = cardOnFocus,
)
