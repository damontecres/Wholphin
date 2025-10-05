package com.github.damontecres.dolphin.ui.cards

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.data.model.Person
import com.github.damontecres.dolphin.ui.enableMarquee
import kotlinx.coroutines.delay

@Composable
fun PersonCard(
    item: Person,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 150.dp * .75f,
    cardHeight: Dp = 200.dp * .75f,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val hideOverlayDelay = 1_000L

    val focused = interactionSource.collectIsFocusedAsState().value
    var focusedAfterDelay by remember { mutableStateOf(false) }

    if (focused) {
        LaunchedEffect(Unit) {
            delay(hideOverlayDelay)
            if (focused) {
                focusedAfterDelay = true
            } else {
                focusedAfterDelay = false
            }
        }
    } else {
        focusedAfterDelay = false
    }

    Card(
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interactionSource,
        colors =
            CardDefaults.colors(
                containerColor = Color.Transparent,
            ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.width(cardWidth),
        ) {
            ItemCardImage(
                imageUrl = item.imageUrl,
                name = item.name,
                showOverlay = true,
                favorite = false,
                watched = false,
                unwatchedCount = -1,
                watchedPercent = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(cardHeight),
            )
            Text(
                text = item.name ?: "",
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .enableMarquee(focusedAfterDelay),
            )
            item.role?.let {
                Text(
                    text = item.role,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .enableMarquee(focusedAfterDelay),
                )
            }
        }
    }
}
