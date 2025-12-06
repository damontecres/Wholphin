package com.github.damontecres.wholphin.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.transformations
import com.github.damontecres.wholphin.ui.CoilTrickplayTransformation
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import org.jellyfin.sdk.model.api.TrickplayInfoDto

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
    duration: Long,
    seekProgressMs: Long,
    videoWidth: Int?,
    videoHeight: Int?,
    trickPlayInfo: TrickplayInfoDto,
    modifier: Modifier = Modifier,
    placeHolder: Painter? = null,
) {
    val context = LocalContext.current

    if (previewImageUrl.isNotNullOrBlank() &&
        videoWidth != null &&
        videoHeight != null
    ) {
        val height = 160.dp
        val width = height * (videoWidth.toFloat() / videoHeight)
        val heightPx = with(LocalDensity.current) { height.toPx().toInt() }
        val widthPx = with(LocalDensity.current) { width.toPx().toInt() }

        val index = (seekProgressMs.toDouble() / trickPlayInfo.interval).toInt() // Which tile
        val numberOfTitlesPerImage = trickPlayInfo.tileHeight * trickPlayInfo.tileWidth
        val imageIndex = index % numberOfTitlesPerImage

        AsyncImage(
            modifier =
                modifier
                    .width(width)
                    .height(height)
                    .background(Color.Black)
                    .border(1.5.dp, color = MaterialTheme.colorScheme.border),
            model =
                ImageRequest
                    .Builder(context)
                    .data(previewImageUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .transformations(
                        CoilTrickplayTransformation(
                            widthPx,
                            heightPx,
                            trickPlayInfo.tileHeight,
                            trickPlayInfo.tileWidth,
                            imageIndex,
                            index,
                        ),
                    ).build(),
            contentScale = ContentScale.None,
            contentDescription = null,
            placeholder = placeHolder,
        )
    }
}
