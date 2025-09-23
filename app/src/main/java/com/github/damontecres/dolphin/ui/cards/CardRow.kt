package com.github.damontecres.dolphin.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.data.model.BaseItem

@Composable
fun CardRow(
    title: String,
    items: List<BaseItem?>,
    onClickItem: (BaseItem) -> Unit,
    onLongClickItem: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = title,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(8.dp),
            modifier =
                Modifier
                    .padding(start = 16.dp)
                    .fillMaxWidth(),
        ) {
            items(items) { item ->
                ItemCard(
                    item = item,
                    onClick = { if (item != null) onClickItem.invoke(item) },
                    onLongClick = { if (item != null) onLongClickItem.invoke(item) },
                    modifier = Modifier,
                )
            }
        }
    }
}
