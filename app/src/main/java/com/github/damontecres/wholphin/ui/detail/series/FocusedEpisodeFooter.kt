package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButtons
import com.github.damontecres.wholphin.ui.components.TitleValueText
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.util.getAudioDisplay
import com.github.damontecres.wholphin.util.getSubtitleDisplay
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration

@Composable
fun FocusedEpisodeFooter(
    ep: BaseItem,
    chosenStreams: ChosenStreams?,
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
        ExpandablePlayButtons(
            resumePosition = resumePosition,
            watched = dto.userData?.played ?: false,
            playOnClick = playOnClick,
            moreOnClick = moreOnClick,
            watchOnClick = watchOnClick,
            buttonOnFocusChanged = {},
            modifier = Modifier,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            dto.mediaStreams
                ?.firstOrNull { it.type == MediaStreamType.VIDEO }
                ?.displayTitle
                ?.let {
                    TitleValueText(
                        stringResource(R.string.video),
                        it,
                    )
                }
            val audioDisplay =
                remember(ep.id, chosenStreams) { getAudioDisplay(ep.data, chosenStreams) }
            audioDisplay?.let {
                TitleValueText(
                    stringResource(R.string.audio),
                    it,
                )
            }

            val subtitleText = getSubtitleDisplay(ep.data, chosenStreams)
            subtitleText
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
