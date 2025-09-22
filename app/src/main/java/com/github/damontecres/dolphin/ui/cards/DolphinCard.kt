package com.github.damontecres.dolphin.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.damontecres.dolphin.data.model.DolphinModel
import com.github.damontecres.dolphin.data.model.Library
import com.github.damontecres.dolphin.data.model.Video

@Composable
fun DolphinCard(
    item: DolphinModel?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is Video ->
            VideoCard(
                item = item,
                onClick = onClick,
                modifier = modifier,
            )

        is Library -> TODO()
        null -> TODO()
    }
}
