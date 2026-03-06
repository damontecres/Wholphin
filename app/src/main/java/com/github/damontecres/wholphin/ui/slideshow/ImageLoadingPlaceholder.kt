package com.github.damontecres.wholphin.ui.slideshow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.isNotNullOrBlank

@Composable
fun ImageLoadingPlaceholder(
    thumbnailUrl: String?,
    showThumbnail: Boolean,
    colorFilter: ColorFilter?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (showThumbnail && thumbnailUrl.isNotNullOrBlank()) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(thumbnailUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                        .alpha(.75f)
                        .blur(4.dp),
            )
        }
        CircularProgress(
            Modifier
                .size(80.dp)
                .align(Alignment.Center),
        )
    }
}
