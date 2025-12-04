package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transitionFactory
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.CrossFadeFactory
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.ImageType
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun BoxScope.DetailsBackdropImage(
    item: BaseItem?,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val backdropImageUrl =
        remember(item) {
            if (item != null) {
                imageUrlService.getItemImageUrl(item, ImageType.BACKDROP)
            } else {
                null
            }
        }
    DetailsBackdropImage(backdropImageUrl, modifier)
}

@Composable
fun BoxScope.DetailsBackdropImage(
    backdropImageUrl: String?,
    modifier: Modifier = Modifier,
) {
    if (backdropImageUrl.isNotNullOrBlank()) {
        val gradientColor = MaterialTheme.colorScheme.background
        AsyncImage(
            model = backdropImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.TopEnd,
            modifier =
                modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight(.85f)
                    .alpha(.75f)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, gradientColor),
                                startY = size.height * .5f,
                            ),
                        )
                        drawRect(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, gradientColor),
                                endX = 0f,
                                startX = size.width * .75f,
                            ),
                        )
                    },
        )
    }
}

@Composable
fun BoxScope.DelayedDetailsBackdropImage(
    item: BaseItem?,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val backdropImageUrl =
        remember(item) {
            if (item != null) {
                imageUrlService.getItemImageUrl(item, ImageType.BACKDROP)
            } else {
                null
            }
        }
    DelayedDetailsBackdropImage(backdropImageUrl, modifier)
}

/**
 * Shows a backdrop image, but with a crossfade & delay
 *
 * Used for change backdrops when change items frequently
 */
@Composable
fun BoxScope.DelayedDetailsBackdropImage(
    focusedBackdropImageUrl: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var backdropImageUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusedBackdropImageUrl) {
        backdropImageUrl = null
        delay(150)
        backdropImageUrl = focusedBackdropImageUrl
    }
    val gradientColor = MaterialTheme.colorScheme.background
    AsyncImage(
        model =
            ImageRequest
                .Builder(context)
                .data(backdropImageUrl)
                .transitionFactory(CrossFadeFactory(250.milliseconds))
                .build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alignment = Alignment.TopEnd,
        modifier =
            modifier
                .fillMaxHeight(.7f)
                .fillMaxWidth(.7f)
                .alpha(.75f)
                .align(Alignment.TopEnd)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, gradientColor),
                            startY = size.height * .33f,
                        ),
                    )
                    drawRect(
                        Brush.horizontalGradient(
                            colors = listOf(gradientColor, Color.Transparent),
                            startX = 0f,
                            endX = size.width * .5f,
                        ),
                    )
                },
    )
}
