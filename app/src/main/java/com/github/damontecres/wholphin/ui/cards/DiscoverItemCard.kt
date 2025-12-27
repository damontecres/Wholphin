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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.enableMarquee
import kotlinx.coroutines.delay

@Composable
@NonRestartableComposable
fun DiscoverItemCard(
    item: DiscoverItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showOverlay: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
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
    val width = Cards.height2x3 * AspectRatios.TALL
    val height = Dp.Unspecified * (1f / AspectRatios.TALL)
    Column(
        verticalArrangement = Arrangement.spacedBy(spaceBetween),
        modifier = modifier.size(width, height),
    ) {
        Card(
            modifier =
                Modifier
                    .size(Dp.Unspecified, Cards.height2x3)
                    .aspectRatio(AspectRatios.TALL),
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
                    imageUrl = item?.posterUrl,
                    name = item?.title,
                    showOverlay = false,
                    favorite = false,
                    watched = false,
                    unwatchedCount = 0,
                    watchedPercent = null,
                    useFallbackText = false,
                    contentScale = ContentScale.FillBounds,
                    modifier =
                        Modifier
                            .fillMaxSize(),
                )
                val availabilityColor =
                    when (item?.availability) {
                        SeerrAvailability.PENDING,
                        SeerrAvailability.PROCESSING,
                        -> Color.Yellow

                        SeerrAvailability.PARTIALLY_AVAILABLE,
                        SeerrAvailability.AVAILABLE,
                        -> Color.Green

                        else -> null
                    }
                availabilityColor?.let {
                    Box(
                        modifier =
                            Modifier
                                .padding(4.dp)
                                .background(
                                    color = it,
                                    shape = CircleShape,
                                ).size(12.dp)
                                .align(Alignment.TopEnd),
                    )
                }
                if (showOverlay) {
                    // TODO better colors
                    val color =
                        when (item?.type) {
                            SeerrItemType.MOVIE -> Color.Blue
                            SeerrItemType.TV -> Color.Red
                            SeerrItemType.PERSON -> Color.Green
                            SeerrItemType.UNKNOWN -> Color.Black
                            null -> Color.Black
                        }.copy(alpha = .5f)
                    when (item?.type) {
                        SeerrItemType.MOVIE -> R.string.movies
                        SeerrItemType.TV -> R.string.tv_shows
                        SeerrItemType.PERSON -> R.string.people
                        SeerrItemType.UNKNOWN -> null
                        null -> null
                    }?.let {
                        Text(
                            text = stringResource(it),
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .background(
                                        color = color,
                                        shape = CircleShape,
                                    ).padding(4.dp),
                        )
                    }
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
                text = item?.title ?: "",
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .enableMarquee(focusedAfterDelay),
            )
            Text(
                text = item?.releaseDate ?: "",
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
