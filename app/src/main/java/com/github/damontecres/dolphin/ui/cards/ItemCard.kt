package com.github.damontecres.dolphin.ui.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.dolphin.R
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.ui.FontAwesome
import com.github.damontecres.dolphin.ui.enableMarquee
import com.github.damontecres.dolphin.ui.ifElse
import com.github.damontecres.dolphin.util.seasonEpisode
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

@Composable
fun ItemCard(
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp? = null,
    cardHeight: Dp? = 200.dp * .85f,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val hideOverlayDelay = 750L

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

    if (item == null) {
        NullCard(modifier, cardWidth, cardHeight, interactionSource)
    } else {
        val dto = item.data
        // TODO better aspect ratio handling
//        val height =
//            if (dto.primaryImageAspectRatio != null && dto.primaryImageAspectRatio!! > 1) cardWidth else cardHeight
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
                modifier = Modifier.ifElse(cardWidth != null, { Modifier.width(cardWidth!!) }),
            ) {
                ItemCardImage(
                    imageUrl = item.imageUrl,
                    name = item.name,
                    showOverlay = !focusedAfterDelay,
                    favorite = dto.userData?.isFavorite ?: false,
                    watched = dto.userData?.played ?: false,
                    unwatchedCount = dto.userData?.unplayedItemCount ?: -1,
                    watchedPercent = dto.userData?.playedPercentage,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .ifElse(cardHeight != null, { Modifier.height(cardHeight!!) }),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.padding(bottom = 4.dp),
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
                    Text(
                        text = item.data.productionYear?.toString() ?: "",
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                                .enableMarquee(focusedAfterDelay),
                    )
                }
                if (dto.type == BaseItemKind.EPISODE) {
                    dto.seasonEpisode?.let {
                        Text(
                            text = it,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ItemCardImage(
    imageUrl: String?,
    name: String?,
    showOverlay: Boolean,
    favorite: Boolean,
    watched: Boolean,
    unwatchedCount: Int,
    watchedPercent: Double?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AsyncImage(
            model = imageUrl,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
            // TODO error/fallback images
            error = null,
            fallback = null,
            onError = {
                Timber.e(it.result.throwable, "Error loading image: $imageUrl")
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
        )
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (favorite) {
                    Text(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp),
                        color = colorResource(android.R.color.holo_red_light),
                        text = stringResource(R.string.fa_heart),
                        fontSize = 20.sp,
                        fontFamily = FontAwesome,
                    )
                }
                if (watched && watchedPercent?.let { it <= 0 || it >= 100 } == true) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.border.copy(alpha = 1f),
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(48.dp)
                                .padding(8.dp),
                    )
                }
                if (unwatchedCount > 0) {
                    Box(
                        modifier =
                            Modifier
                                .padding(8.dp)
                                .align(Alignment.TopEnd),
                    ) {
                        Text(
                            modifier =
                                Modifier
                                    .background(
                                        MaterialTheme.colorScheme.border,
                                        shape = CircleShape,
                                    ),
                            color = MaterialTheme.colorScheme.onSurface,
                            text = unwatchedCount.toString(),
                            fontSize = 20.sp,
                        )
                    }
                }

                watchedPercent?.let { percent ->
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .background(
                                    MaterialTheme.colorScheme.tertiary,
                                ).clip(RectangleShape)
                                .height(4.dp)
                                .fillMaxWidth((percent / 100.0).toFloat()),
                    )
                }
            }
        }
    }
}
