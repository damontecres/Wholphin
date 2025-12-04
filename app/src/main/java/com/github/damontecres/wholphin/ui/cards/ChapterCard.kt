package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.roundSeconds
import kotlin.time.Duration

/**
 * Card for a [com.github.damontecres.wholphin.data.model.Chapter]
 */
@Composable
fun ChapterCard(
    name: String?,
    position: Duration,
    imageUrl: String?,
    onClick: () -> Unit,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    cardHeight: Dp = 120.dp,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    Card(
        modifier = modifier.size(cardHeight * aspectRatio, cardHeight),
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interactionSource,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(AppColors.TransparentBlack50),
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    name?.let {
                        Text(
                            text = it,
                        )
                    }
                    Text(
                        text = position.roundSeconds.toString(),
                    )
                }
            }
        }
    }
}
