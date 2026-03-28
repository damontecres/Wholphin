package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object HeaderUtils {
    val topPadding = 48.dp
    val bottomPadding = 32.dp
    val startPadding = 8.dp

    val padding = PaddingValues(top = topPadding, bottom = bottomPadding, start = startPadding)

    const val FILL_MAX_HEIGHT = .33f

    val modifier =
        Modifier
            .padding(padding)
            .fillMaxHeight(FILL_MAX_HEIGHT)
}
