package com.github.damontecres.dolphin.ui.cards

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.data.model.Person
import com.github.damontecres.dolphin.ui.enableMarquee
import kotlinx.coroutines.delay

/**
 * A Card for a [Person] such as an actor or director
 */
@Composable
fun PersonCard(
    item: Person,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
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
    val spaceBetween by animateDpAsState(if (focused) 12.dp else 4.dp)
    val spaceBelow by animateDpAsState(if (focused) 4.dp else 12.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(spaceBetween),
        modifier = modifier,
    ) {
        Card(
            modifier = Modifier,
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            colors =
                CardDefaults.colors(
                    containerColor = Color.Transparent,
                ),
        ) {
            ItemCardImage(
                imageUrl = item.imageUrl,
                name = item.name,
                showOverlay = false,
                favorite = false,
                watched = false,
                unwatchedCount = -1,
                watchedPercent = null,
                useFallbackText = false,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f), // TODO,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier =
                Modifier
                    .padding(bottom = spaceBelow)
                    .fillMaxWidth(),
        ) {
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
