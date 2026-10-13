package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.ui.theme.FluidLayout

object HeaderUtils {
    val topPadding = 48.dp
    val bottomPadding = 32.dp

    // Left indent of the title and details on every page; follows the screen's shape
    val startPadding: Dp
        @Composable
        get() = FluidLayout.headlineInset()

    val padding: PaddingValues
        @Composable
        get() = PaddingValues(top = topPadding, bottom = bottomPadding, start = startPadding)

    val height = 180.dp

    val logoHeight = 60.dp

    val modifier: Modifier
        @Composable
        get() =
            Modifier
                .padding(padding)
                .height(height)
}
