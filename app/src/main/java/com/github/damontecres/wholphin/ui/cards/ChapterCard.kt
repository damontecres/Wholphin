package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.roundSeconds
import org.jellyfin.sdk.model.api.ImageType

/**
 * Card for a [com.github.damontecres.wholphin.data.model.Chapter]
 */
@Composable
fun ChapterCard(
    chapter: Chapter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardHeight: Dp = 120.dp,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val density = LocalDensity.current
    val imageUrlService = LocalImageUrlService.current
    val imageUrl =
        remember(chapter, cardHeight) {
            imageUrlService.getItemImageUrl(
                itemId = chapter.itemId,
                imageType = ImageType.CHAPTER,
                tag = chapter.tag,
                imageIndex = chapter.index,
                fillHeight = with(density) { cardHeight.roundToPx() },
            )
        }
    var width by remember { mutableStateOf(AspectRatios.WIDE * cardHeight) }
    Card(
        modifier = modifier.height(cardHeight),
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
                onSuccess = {
                    width =
                        with(density) {
                            it.painter.intrinsicSize.width
                                .toDp()
                        }
                },
            )
            Box(
                modifier =
                    Modifier
                        .width(width)
                        .align(Alignment.BottomStart)
                        .background(AppColors.TransparentBlack50),
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    chapter.name?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    val resources = LocalResources.current
                    val positionText =
                        remember(chapter.position) { resources.formatDuration(chapter.position.roundSeconds) }
                    Text(
                        text = positionText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
    }
}
