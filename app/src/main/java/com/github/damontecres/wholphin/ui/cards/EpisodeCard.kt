package com.github.damontecres.wholphin.ui.cards

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.enableMarquee
import com.github.damontecres.wholphin.ui.seasonEpisode
import kotlinx.coroutines.delay

@Composable
fun EpisodeCard(
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageHeight: Dp = Dp.Unspecified,
    imageWidth: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val dto = item?.data
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
    val aspectRatio = item?.aspectRatio?.coerceAtLeast(AspectRatios.MIN) ?: AspectRatios.MIN
    val width = imageHeight * aspectRatio
    val height = imageWidth * (1f / aspectRatio)
    Column(
        verticalArrangement = Arrangement.spacedBy(spaceBetween),
        modifier = modifier.size(width, height),
    ) {
        Card(
            modifier =
                Modifier
                    .size(imageWidth, imageHeight)
                    .aspectRatio(aspectRatio),
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
                    item = item,
                    name = item?.name,
                    showOverlay = false,
                    favorite = dto?.userData?.isFavorite ?: false,
                    watched = dto?.userData?.played ?: false,
                    unwatchedCount = dto?.userData?.unplayedItemCount ?: -1,
                    watchedPercent = dto?.userData?.playedPercentage,
                    numberOfVersions = dto?.mediaSourceCount ?: 0,
                    useFallbackText = false,
                    modifier =
                        Modifier
                            .fillMaxSize(),
                )
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .background(
                                AppColors.TransparentBlack50,
                                shape = RoundedCornerShape(8.dp),
                            ),
                ) {
                    Text(
                        text = dto?.seasonEpisode ?: "",
                        modifier = Modifier.padding(4.dp),
                    )
                }
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
                text = dto?.seriesName ?: "",
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
                text = item?.name ?: "",
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
