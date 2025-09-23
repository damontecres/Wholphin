package com.github.damontecres.dolphin.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.data.model.Library
import com.github.damontecres.dolphin.data.model.Video
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
fun DolphinCard(
    item: Any?,
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
        is BaseItemDto -> {
            Text(item.id.toString())
        }

        null -> NullCard(modifier = modifier)
    }
}
