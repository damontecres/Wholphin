package com.github.damontecres.wholphin.ui.playback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.ifElse
import kotlin.math.abs

@Composable
fun SkipIndicator(
    durationMs: Long,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backward = durationMs < 0
    var currentRotation by remember { mutableFloatStateOf(0f) }
    val rotation = remember { Animatable(currentRotation) }
    LaunchedEffect(durationMs, backward, onFinish) {
        rotation.animateTo(
            targetValue = currentRotation + if (backward) -270f else 270f,
            animationSpec =
                tween(
                    durationMillis = 800,
                ),
        ) {
            currentRotation = value
        }
        onFinish()
    }

    Box(modifier = modifier.size(55.dp, 55.dp)) {
        Image(
            modifier =
                Modifier
                    .fillMaxSize()
                    .rotate(currentRotation)
                    .ifElse(backward, Modifier.scale(scaleX = -1f, scaleY = 1f)),
            painter = painterResource(id = R.drawable.circular_arrow_right),
            contentDescription = null,
        )
        Text(
            modifier = Modifier.align(Alignment.Center),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            style = MaterialTheme.typography.bodySmall,
            text = abs(durationMs / 1000).toString(),
        )
    }
}
