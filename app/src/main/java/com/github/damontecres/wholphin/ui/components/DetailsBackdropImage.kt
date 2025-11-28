package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.isNotNullOrBlank

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
