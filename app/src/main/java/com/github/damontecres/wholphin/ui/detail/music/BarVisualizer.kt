package com.github.damontecres.wholphin.ui.detail.music

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

@Composable
fun BoxScope.BarVisualizer(
    data: IntArray,
    modifier: Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.onSizeChanged { size = it },
    ) {
        val size = with(LocalDensity.current) { DpSize(size.width.toDp(), size.height.toDp()) }
        val padding = 1.dp
        val width = size.width / data.size

        data.forEachIndexed { index, data ->
            val height by animateDpAsState(
                targetValue = size.height * data / 128f,
                animationSpec = tween(easing = LinearEasing),
            )
            Box(
                Modifier
                    .height(height)
                    .width(width)
                    .padding(start = if (index == 0) 0.dp else padding)
                    .background(MaterialTheme.colorScheme.border),
            )
        }
    }
}
