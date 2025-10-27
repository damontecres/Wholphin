package com.github.damontecres.wholphin.ui.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopupContent
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.RemoteSubtitleInfo

@Composable
fun DownloadSubtitlesContent(
    state: SubtitleSearch,
    onClickDownload: (RemoteSubtitleInfo) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val s = state) {
        SubtitleSearch.Searching -> {
            Wrapper {
                Text(
                    text = "Searching...",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        SubtitleSearch.Downloading -> {
            Wrapper {
                Text(
                    text = "Downloading...",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        is SubtitleSearch.Error -> Wrapper { ErrorMessage(null, s.ex, modifier) }

        is SubtitleSearch.Success -> {
            val dialogItems = convertRemoteSubtitles(s.options, onClickDownload)
            if (dialogItems.isEmpty()) {
                Wrapper {
                    Text(
                        text = "No remote subtitles were found",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    focusRequester.tryRequestFocus()
                }
                DialogPopupContent(
                    title = "Download remote subtitles",
                    dialogItems = dialogItems,
                    waiting = false,
                    onDismissRequest = onDismissRequest,
                    modifier = modifier.focusRequester(focusRequester),
                    dismissOnClick = false,
                )
            }
        }
    }
}

@Composable
private fun Wrapper(content: @Composable BoxScope.() -> Unit) {
    val elevatedContainerColor =
        MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    Box(
        modifier =
            Modifier
                .graphicsLayer {
                    this.clip = true
                    this.shape = RoundedCornerShape(28.0.dp)
                }.drawBehind { drawRect(color = elevatedContainerColor) }
                .padding(PaddingValues(24.dp)),
        content = content,
    )
}

fun convertRemoteSubtitles(
    options: List<RemoteSubtitleInfo>,
    onClick: (RemoteSubtitleInfo) -> Unit,
) = options.map { op ->
    DialogItem(
        onClick = { onClick.invoke(op) },
        headlineContent = {
            Text(
                text = op.name ?: "",
            )
        },
        supportingContent = {
            val downloads = op.downloadCount ?: 0
            val provider = op.providerName?.let { "$it - " }
            Text(
                text = "$provider$downloads downloads",
            )
        },
        trailingContent = {
            op.communityRating?.let { rating ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = rating.toString(),
                    )
                    Icon(
                        imageVector = Icons.Default.Star,
                        tint = AppColors.GoldenYellow,
                        contentDescription = null,
                    )
                }
            }
        },
    )
}
