package com.github.damontecres.wholphin.ui.playback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import org.jellyfin.sdk.model.api.TrickplayInfo

fun Modifier.offsetByPercent(
    xPercentage: Float,
    yOffset: Int,
) = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(
                x =
                    ((constraints.maxWidth * xPercentage).toInt() - placeable.width / 2)
                        .coerceIn(0, constraints.maxWidth - placeable.width),
                y = constraints.maxHeight - yOffset, // (constraints.maxHeight * yPercentage).toInt() - (placeable.height / 1.33f).toInt(),
            )
        }
    },
)

/**
 * Offset the composable by a percentage of the available x direction
 *
 * This will account for the composable actual width so it won't be pushed off screen.
 * In other words, 0% means the left edge of the composable will be at the left end of the x-axis.
 *
 * @param xPercentage percent offset between 0 inclusive and 1 inclusive
 */
fun Modifier.offsetByPercent(xPercentage: Float) =
    this.then(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(
                    x =
                        ((constraints.maxWidth * xPercentage).toInt() - placeable.width / 2)
                            .coerceIn(0, constraints.maxWidth - placeable.width),
                    y = 0,
                )
            }
        },
    )

/**
 * Show trickplay preview image. This composable assumes the provided URL is for the correct index.
 *
 * If no trickplay image is available, just the timestamp will be shown.
 */
@Composable
fun SeekPreviewImage(
    previewImageUrl: String,
    seekProgressMs: Long,
    trickPlayInfo: TrickplayInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    if (previewImageUrl.isNotNullOrBlank()) {
        val height = 160.dp
        val width = height * (trickPlayInfo.width.toFloat() / trickPlayInfo.height)
        val scale = LocalDensity.current.density

        val model =
            remember(previewImageUrl) {
                ImageRequest
                    .Builder(context)
                    .data(previewImageUrl)
                    .size(coil3.size.Size.ORIGINAL)
                    .build()
            }
        val painter =
            rememberAsyncImagePainter(
                model = model,
                contentScale = ContentScale.None,
            )
        val index =
            (seekProgressMs.toDouble() / trickPlayInfo.interval).toInt() // Index of tile across images
        val numberOfTilesPerImage = trickPlayInfo.tileHeight * trickPlayInfo.tileWidth
        val tileIndex =
            index % numberOfTilesPerImage // Index of tile within the current image
        val x = (tileIndex % trickPlayInfo.tileWidth) // x position within tile grid
        val y = (tileIndex / trickPlayInfo.tileHeight) // y position
        Box(
            modifier =
                modifier
                    .border(1.5.dp, color = MaterialTheme.colorScheme.border)
                    .background(Color.Black)
                    .height(height)
                    .width(width),
        ) {
            Canvas(
                modifier =
                    Modifier
                        .height(height)
                        .width(width)
                        .clip(RectangleShape),
            ) {
                with(painter) {
                    // Scale and translate to the right position in the tile grid
                    scale(scale, scale, pivot = Offset.Zero) {
                        translate(
                            left = -x.toFloat() * trickPlayInfo.width,
                            top = -y.toFloat() * trickPlayInfo.height,
                        ) {
                            draw(
                                size =
                                    Size(
                                        trickPlayInfo.width * trickPlayInfo.tileWidth.toFloat(),
                                        trickPlayInfo.height * trickPlayInfo.tileHeight.toFloat(),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}
