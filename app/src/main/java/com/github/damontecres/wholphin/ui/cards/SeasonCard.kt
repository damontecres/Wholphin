package com.github.damontecres.wholphin.ui.cards

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.enableMarquee
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.ImageType

/**
 * A Card for a TV Show Season, but can generically show most items
 */
@Composable
fun SeasonCard(
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageHeight: Dp = Dp.Unspecified,
    imageWidth: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    showImageOverlay: Boolean = false,
    aspectRatio: Float = item?.aspectRatio ?: AspectRatios.TALL,
) {
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val imageUrl =
        remember(item, imageHeight, imageWidth) {
            if (item != null) {
                val fillHeight =
                    if (imageHeight != Dp.Unspecified) {
                        with(density) {
                            imageHeight.roundToPx()
                        }
                    } else {
                        null
                    }
                val fillWidth =
                    if (imageWidth != Dp.Unspecified) {
                        with(density) {
                            imageWidth.roundToPx()
                        }
                    } else {
                        null
                    }
                imageUrlService.getItemImageUrl(
                    item,
                    ImageType.PRIMARY,
                    fillWidth = fillWidth,
                    fillHeight = fillHeight,
                )
            } else {
                null
            }
        }
    SeasonCard(
        title = item?.title,
        subtitle = item?.subtitle,
        name = item?.name,
        imageUrl = imageUrl,
        isFavorite = item?.data?.userData?.isFavorite ?: false,
        isPlayed = item?.data?.userData?.played ?: false,
        unplayedItemCount = item?.data?.userData?.unplayedItemCount ?: 0,
        playedPercentage = item?.data?.userData?.playedPercentage ?: 0.0,
        numberOfVersions = item?.data?.mediaSourceCount ?: 0,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        imageHeight = imageHeight,
        imageWidth = imageWidth,
        interactionSource = interactionSource,
        showImageOverlay = showImageOverlay,
        aspectRatio = aspectRatio,
    )
}

/**
 * A Card for a TV Show Season, but can generically show most items
 */
@Composable
fun SeasonCard(
    title: String?,
    subtitle: String?,
    name: String?,
    imageUrl: String?,
    isFavorite: Boolean,
    isPlayed: Boolean,
    unplayedItemCount: Int,
    playedPercentage: Double,
    numberOfVersions: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageHeight: Dp = Dp.Unspecified,
    imageWidth: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    showImageOverlay: Boolean = false,
    aspectRatio: Float = AspectRatios.TALL,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val spaceBetween by animateDpAsState(if (focused) 12.dp else 4.dp)
    val spaceBelow by animateDpAsState(if (focused) 4.dp else 12.dp)
    var focusedAfterDelay by remember { mutableStateOf(false) }

    val hideOverlayDelay = 500L
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
    val aspectRationToUse = aspectRatio.coerceAtLeast(AspectRatios.MIN)
    val width = imageHeight * aspectRationToUse
    val height = imageWidth * (1f / aspectRationToUse)
    Column(
        verticalArrangement = Arrangement.spacedBy(spaceBetween),
        modifier = modifier.size(width, height),
    ) {
        Card(
            modifier =
                Modifier
                    .size(imageWidth, imageHeight)
                    .aspectRatio(aspectRationToUse),
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            colors =
                CardDefaults.colors(
                    containerColor = Color.Transparent,
                ),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                ItemCardImage(
                    imageUrl = imageUrl,
                    name = name,
                    showOverlay = showImageOverlay,
                    favorite = isFavorite,
                    watched = isPlayed,
                    unwatchedCount = unplayedItemCount,
                    watchedPercent = playedPercentage,
                    numberOfVersions = numberOfVersions,
                    useFallbackText = false,
                    modifier =
                        Modifier
                            .fillMaxSize(),
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier =
                Modifier
                    .padding(bottom = spaceBelow)
                    .fillMaxWidth(),
        ) {
            Text(
                text = title ?: "",
                maxLines = 1,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .enableMarquee(focusedAfterDelay),
            )
            Text(
                text = subtitle ?: "",
                maxLines = 1,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Normal,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .enableMarquee(focusedAfterDelay),
            )
        }
    }
}
