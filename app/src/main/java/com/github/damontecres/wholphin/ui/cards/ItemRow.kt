package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.util.FocusPair

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
    focusPair: FocusPair? = null,
    cardOnFocus: ((isFocused: Boolean, index: Int) -> Unit)? = null,
    horizontalPadding: Dp = 16.dp,
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
            horizontalArrangement = Arrangement.spacedBy(horizontalPadding),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
            modifier =
                Modifier
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
                    { if (item != null) onClickItem.invoke(index, item) },
                    { if (item != null) onLongClickItem.invoke(index, item) },
                )
            }
        }
    }
}

@Composable
fun BannerItemRow(
    title: String,
    items: List<BaseItem?>,
    onClickItem: (Int, BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
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
            name = title,
            item = item,
            aspectRatio =
                aspectRatioOverride ?: item?.data?.primaryImageAspectRatio?.toFloat()
                    ?: AspectRatios.WIDE,
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
