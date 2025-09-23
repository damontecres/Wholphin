package com.github.damontecres.dolphin.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.width(cardWidth),
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(cardHeight),
            )
            Text(
                text = item.name ?: "",
            )
        }
    }
}
