package com.github.damontecres.dolphin.ui.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.dolphin.data.model.Video

@Composable
fun VideoCard(
    item: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 150.dp,
    cardHeight: Dp = 200.dp,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.size(cardWidth, cardHeight),
        ) {
            Text(
                text = item.name ?: "",
            )
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
            )
        }
    }
}
