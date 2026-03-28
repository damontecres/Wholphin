package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object HeaderUtils {
    val topPadding = 48.dp
    val bottomPadding = 32.dp
    val startPadding = 8.dp

    val padding = PaddingValues(top = topPadding, bottom = bottomPadding, start = startPadding)

    val height = 180.dp

    val modifier =
        Modifier
            .padding(padding)
            .height(height)
}
