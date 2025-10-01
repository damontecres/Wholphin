package com.github.damontecres.dolphin.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.transformations
import com.github.damontecres.dolphin.ui.CoilTrickplayTransformation
import com.github.damontecres.dolphin.ui.isNotNullOrBlank
import org.jellyfin.sdk.model.api.TrickplayInfo
import kotlin.time.Duration.Companion.seconds

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

@Composable
fun SeekPreviewImage(
    previewImageUrl: String,
    duration: Long,
    seekProgressMs: Long,
    videoWidth: Int?,
    videoHeight: Int?,
    trickPlayInfo: TrickplayInfo,
    modifier: Modifier = Modifier,
    placeHolder: Painter? = null,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
                    Modifier
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
        Text(
            text = (seekProgressMs / 1000L).seconds.toString(),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
