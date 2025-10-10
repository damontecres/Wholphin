package com.github.damontecres.dolphin.ui.cards

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.ui.ifElse

@Composable
@Deprecated("Cards should handle nulls natively")
fun NullCard(
    modifier: Modifier = Modifier,
    cardWidth: Dp? = null,
    cardHeight: Dp? = 200.dp * .75f,
    interactionSource: MutableInteractionSource? = null,
) {
    Card(
        modifier = modifier,
        onClick = {},
        interactionSource = interactionSource,
    ) {
        Column(
            modifier =
                Modifier
                    .ifElse(cardHeight != null, { Modifier.height(cardHeight!!) })
                    .ifElse(cardWidth != null, { Modifier.width(cardWidth!!) }),
        ) {
            Text(
                text = "Loading...",
            )
        }
    }
}
