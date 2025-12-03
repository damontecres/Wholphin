package com.github.damontecres.wholphin.ui.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.logCoilError

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
    useFallbackText: Boolean = true,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var imageError by remember(imageUrl) { mutableStateOf(false) }
    Box(modifier = modifier) {
        if (!imageError && imageUrl.isNotNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = contentScale,
                alignment = Alignment.Center,
                onError = {
                    logCoilError(imageUrl, it.result)
                    imageError = true
                },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .align(Alignment.TopCenter),
            )
        } else {
            // TODO options for overriding fallback
            Box(
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .fillMaxSize()
                        .align(Alignment.TopCenter),
            ) {
                if (useFallbackText && name.isNotNullOrBlank()) {
                    Text(
                        text = name,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier =
                            Modifier
                                .padding(8.dp)
                                .align(Alignment.Center),
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.video_solid),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter =
                            ColorFilter.tint(
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                BlendMode.SrcIn,
                            ),
                        modifier =
                            Modifier
                                .fillMaxSize(.4f)
                                .align(Alignment.Center),
                    )
                }
            }
        }
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .padding(4.dp)
                            .align(Alignment.TopEnd),
                ) {
                    if (watched && (watchedPercent == null || watchedPercent <= 0.0 || watchedPercent >= 100.0)) {
                        WatchedIcon(Modifier.size(24.dp))
                    }
                    if (unwatchedCount > 0) {
                        Box(
                            modifier =
                                Modifier
                                    .background(
                                        AppColors.TransparentBlack50,
                                        shape = RoundedCornerShape(25),
                                    ),
                        ) {
                            Text(
                                text = unwatchedCount.toString(),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
//                            fontSize = 16.sp,
                                modifier = Modifier.padding(4.dp),
                            )
                        }
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
                                .height(Cards.playedPercentHeight)
                                .fillMaxWidth((percent / 100.0).toFloat()),
                    )
                }
            }
        }
    }
}
