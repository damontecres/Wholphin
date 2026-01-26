package com.github.damontecres.wholphin.ui.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.components.ViewOptionImageType
import com.github.damontecres.wholphin.ui.enableMarquee
import kotlinx.coroutines.delay

/**
 * Card for use in [com.github.damontecres.wholphin.ui.detail.CardGrid]
 */
@Composable
fun GridCard(
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    imageAspectRatio: Float = AspectRatios.TALL,
    imageContentScale: ContentScale = ContentScale.Fit,
    imageType: ViewOptionImageType = ViewOptionImageType.PRIMARY,
    showTitle: Boolean = true,
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

    Column(
        verticalArrangement = Arrangement.spacedBy(spaceBetween),
        modifier = modifier,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            colors =
                CardDefaults.colors(
                    containerColor = Color.Transparent,
                ),
        ) {
            ItemCardImage(
                item = item,
                imageType = imageType.imageType,
                name = item?.name,
//                        showOverlay = !focusedAfterDelay,
                showOverlay = true,
                favorite = dto?.userData?.isFavorite ?: false,
                watched = dto?.userData?.played ?: false,
                unwatchedCount = dto?.userData?.unplayedItemCount ?: -1,
                watchedPercent = dto?.userData?.playedPercentage,
                numberOfVersions = dto?.mediaSourceCount ?: 0,
                useFallbackText = false,
                contentScale = imageContentScale,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(imageAspectRatio.coerceAtLeast(AspectRatios.MIN))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        AnimatedVisibility(showTitle) {
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier =
                    Modifier
                        .padding(bottom = spaceBelow)
                        .fillMaxWidth(),
            ) {
                Text(
                    text = item?.title ?: "",
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .enableMarquee(focusedAfterDelay),
                )
                Text(
                    text = item?.subtitle ?: "",
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
}
