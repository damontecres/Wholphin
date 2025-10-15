package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.isNotNullOrBlank

/**
 * Displays an image as a card. If no image is available, the name will be shown instead
 */
@Composable
fun BannerCard(
    name: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerText: String? = null,
    played: Boolean = false,
    playPercent: Double = 0.0,
    cardHeight: Dp = 140.dp * .85f,
    aspectRatio: Float = 16f / 9,
    interactionSource: MutableInteractionSource? = null,
) {
    var imageError by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.size(cardHeight * aspectRatio, cardHeight),
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interactionSource,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (!imageError && imageUrl.isNotNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onError = { imageError = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = name ?: "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .padding(16.dp)
                            .align(Alignment.Center),
                )
            }
            if (played || cornerText.isNotNullOrBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                ) {
                    if (played && (playPercent <= 0 || playPercent >= 100)) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.border.copy(alpha = 1f),
                            modifier =
                                Modifier
                                    .size(24.dp),
                        )
                    }
                    if (cornerText.isNotNullOrBlank()) {
                        Box(
                            modifier =
                                Modifier
                                    .background(
                                        AppColors.TransparentBlack50,
                                        shape = RoundedCornerShape(25),
                                    ),
                        ) {
                            Text(
                                text = cornerText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(4.dp),
                            )
                        }
                    }
                }
            }
            if (playPercent > 0 && playPercent < 100) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .background(
                                MaterialTheme.colorScheme.tertiary,
                            ).clip(RectangleShape)
                            .height(Cards.playedPercentHeight)
                            .fillMaxWidth((playPercent / 100).toFloat()),
                )
            }
        }
    }
}
