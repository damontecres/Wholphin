package com.github.damontecres.dolphin.ui.cards

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.Text

@Composable
fun NullCard(
    modifier: Modifier = Modifier,
    cardWidth: Dp = 150.dp,
    cardHeight: Dp = 200.dp,
    interactionSource: MutableInteractionSource? = null,
) {
    Card(
        modifier = modifier,
        onClick = {},
        interactionSource = interactionSource,
    ) {
        Column(
            modifier = Modifier.size(cardWidth, cardHeight),
        ) {
            Text(
                text = "Loading...",
            )
        }
    }
}
