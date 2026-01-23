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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import org.jellyfin.sdk.model.api.ImageType

/**
 * Displays an image as a card. If no image is available, the name will be shown instead
 */
@Composable
fun BannerCard(
    name: String?,
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerText: String? = null,
    played: Boolean = false,
    favorite: Boolean = false,
    playPercent: Double = 0.0,
    cardHeight: Dp = 120.dp,
    aspectRatio: Float = AspectRatios.WIDE,
    interactionSource: MutableInteractionSource? = null,
) {
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val fillHeight =
        remember(cardHeight) {
            if (cardHeight.isSpecified) {
                with(density) {
                    cardHeight.roundToPx()
                }
            } else {
                null
            }
        }
    val imageUrl =
        remember(item, fillHeight) {
            if (item != null) {
                imageUrlService.getItemImageUrl(
                    item,
                    ImageType.PRIMARY,
                    fillWidth = null,
                    fillHeight = fillHeight,
                )
            } else {
                null
            }
        }
    var imageError by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.size(cardHeight * aspectRatio, cardHeight),
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interactionSource,
        colors =
            CardDefaults.colors(
//                containerColor = Color.Transparent,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize(),
//                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (!imageError && imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
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
            if (played || cornerText != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                ) {
                    if (played && (playPercent <= 0 || playPercent >= 100)) {
                        WatchedIcon(Modifier.size(24.dp))
                    }
                    if (cornerText != null) {
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
            if (favorite) {
                Text(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                    color = colorResource(android.R.color.holo_red_light),
                    text = stringResource(R.string.fa_heart),
                    fontSize = 16.sp,
                    fontFamily = FontAwesome,
                )
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
