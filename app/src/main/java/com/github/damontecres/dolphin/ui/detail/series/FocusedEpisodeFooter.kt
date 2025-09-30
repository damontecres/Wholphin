package com.github.damontecres.dolphin.ui.detail.series

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.github.damontecres.dolphin.R
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.ui.components.ExpandableFaButton
import com.github.damontecres.dolphin.ui.components.ExpandablePlayButton
import com.github.damontecres.dolphin.ui.components.TitleValueText
import com.github.damontecres.dolphin.ui.isNotNullOrBlank
import com.github.damontecres.dolphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration

@Composable
fun FocusedEpisodeFooter(
    ep: BaseItem,
    playOnClick: (Duration) -> Unit,
    moreOnClick: () -> Unit,
    watchOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dto = ep.data
    val resumePosition = dto.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO
    val firstFocus = remember { FocusRequester() }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(8.dp),
            modifier =
                Modifier
                    .focusGroup()
                    .focusProperties {
                        onEnter = {
                            firstFocus.tryRequestFocus()
                        }
                    },
        ) {
            if (resumePosition > Duration.ZERO) {
                item {
//                LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
                    ExpandablePlayButton(
                        R.string.resume,
                        resumePosition,
                        Icons.Default.PlayArrow,
                        playOnClick,
                        Modifier.focusRequester(firstFocus),
                        // .onFocusChanged(buttonOnFocusChanged),
                    )
                }
                item {
                    ExpandablePlayButton(
                        R.string.restart,
                        Duration.ZERO,
                        Icons.Default.Refresh,
                        playOnClick,
                        Modifier,
                        // .onFocusChanged(buttonOnFocusChanged),
                    )
                }
            } else {
                item {
                    ExpandablePlayButton(
                        R.string.play,
                        Duration.ZERO,
                        Icons.Default.PlayArrow,
                        playOnClick,
                        Modifier.focusRequester(firstFocus),
                        // .onFocusChanged(buttonOnFocusChanged)
                    )
                }
            }

            val played = dto.userData?.played ?: false
            // Played button
            item {
                ExpandableFaButton(
                    title = if (played) R.string.mark_unwatched else R.string.mark_watched,
                    iconStringRes = if (played) R.string.fa_eye else R.string.fa_eye_slash,
                    onClick = watchOnClick,
                    modifier = Modifier,
                )
            }

            // More button
            item {
                ExpandablePlayButton(
                    R.string.more,
                    Duration.ZERO,
                    Icons.Default.MoreVert,
                    { moreOnClick.invoke() },
                    Modifier,
                    // .onFocusChanged(buttonOnFocusChanged)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            dto.mediaStreams
                ?.firstOrNull { it.type == MediaStreamType.VIDEO }
                ?.let { stream ->
                    stream.displayTitle?.let {
                        TitleValueText(
                            "Video",
                            it,
                        )
                    }
                }

            dto.mediaStreams
                ?.firstOrNull { it.type == MediaStreamType.AUDIO }
                ?.let { stream ->
                    stream.displayTitle?.let {
                        TitleValueText(
                            "Audio",
                            it,
                        )
                    }
                }

            dto.mediaStreams
                ?.filter { it.type == MediaStreamType.SUBTITLE && it.language.isNotNullOrBlank() }
                ?.mapNotNull { it.language }
                ?.joinToString(", ")
                ?.let {
                    if (it.isNotNullOrBlank()) {
                        TitleValueText(
                            "Subtitles",
                            it,
                            modifier = Modifier.widthIn(max = 64.dp),
                        )
                    }
                }
        }
    }
}
