package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.PlaylistProgress
import com.github.damontecres.wholphin.ui.components.ViewOptionImageType
import org.jellyfin.sdk.model.api.ImageType

/**
 * Large tile for a playlist on the playlists library page
 *
 * Shows the playlist's own wide image if it has one. Otherwise shows a stylized version of its
 * primary image, so nothing from inside the playlist is revealed. The full name is shown below,
 * wrapping instead of scrolling.
 */
@Composable
fun PlaylistTile(
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    progress: PlaylistProgress,
    modifier: Modifier = Modifier,
    imageAspectRatio: Float = AspectRatios.WIDE,
    imageContentScale: ContentScale = ContentScale.Crop,
    imageType: ViewOptionImageType = ViewOptionImageType.THUMB,
    showTitle: Boolean = true,
    fillWidth: Int? = null,
) {
    val dto = item?.data
    val imageUrlService = LocalImageUrlService.current
    val imageUrl =
        remember(item, imageType, fillWidth) {
            val imageTags = dto?.imageTags.orEmpty()
            val hasImage =
                when (imageType) {
                    ViewOptionImageType.THUMB -> {
                        ImageType.THUMB in imageTags || !dto?.backdropImageTags.isNullOrEmpty()
                    }

                    ViewOptionImageType.PRIMARY -> {
                        ImageType.PRIMARY in imageTags
                    }
                }
            if (hasImage) {
                imageUrlService.getItemImageUrl(item, imageType.imageType, fillWidth = fillWidth)
            } else {
                null
            }
        }
    val unwatchedCount = dto?.userData?.unplayedItemCount
    val playedPercent = dto?.userData?.playedPercentage

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            colors = CardDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(imageAspectRatio.coerceAtLeast(AspectRatios.MIN))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                ItemCardImage(
                    imageUrl = imageUrl,
                    name = item?.name,
                    showOverlay = true,
                    favorite = dto?.userData?.isFavorite ?: false,
                    // Progress is shown by this tile based on the user's choice instead
                    watched = false,
                    unwatchedCount = -1,
                    watchedPercent = null,
                    numberOfVersions = 0,
                    contentScale = imageContentScale,
                    fallback = { StylizedPlaylistArt(item, Modifier.matchParentSize()) },
                    modifier = Modifier.matchParentSize(),
                )
                if (progress == PlaylistProgress.BAR && playedPercent != null) {
                    GlowProgressBar(playedPercent)
                }
            }
        }
        if (showTitle) {
            Text(
                text = item?.title ?: "",
                maxLines = 2,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
            )
        }
        if (progress == PlaylistProgress.COUNT && unwatchedCount != null) {
            Text(
                text =
                    if (unwatchedCount > 0) {
                        pluralStringResource(R.plurals.playlist_unwatched_count, unwatchedCount, unwatchedCount)
                    } else {
                        stringResource(R.string.playlist_all_watched)
                    },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The playlist's primary image (usually a collage) shown sharp over a blurred, darkened copy of
 * itself. Blur only works on Android 12+, older devices show the copy dimmed but not blurred.
 */
@Composable
private fun StylizedPlaylistArt(
    item: BaseItem?,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val imageUrl =
        remember(item) {
            item
                ?.takeIf { ImageType.PRIMARY in it.data.imageTags.orEmpty() }
                ?.let { imageUrlService.getItemImageUrl(it, ImageType.PRIMARY) }
        }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .matchParentSize()
                        .blur(24.dp)
                        .drawWithContent {
                            drawContent()
                            drawRect(Color.Black.copy(alpha = .45f))
                        },
            )
            AsyncImage(
                model = imageUrl,
                contentDescription = item?.name,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxHeight(.8f)
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(6.dp)),
            )
        }
    }
}

/**
 * A thin progress bar along the bottom of the tile with a soft glow above it
 */
@Composable
private fun BoxScope.GlowProgressBar(playedPercent: Double) {
    val fraction = (playedPercent / 100).toFloat().coerceIn(0f, 1f)
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier =
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.Black.copy(alpha = .4f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .drawBehind {
                        val glowHeight = size.height * 4
                        drawRect(
                            brush =
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, color.copy(alpha = .6f)),
                                    startY = size.height - glowHeight,
                                    endY = size.height,
                                ),
                            topLeft = Offset(0f, size.height - glowHeight),
                            size = Size(size.width, glowHeight),
                        )
                    }.background(color),
        )
    }
}
