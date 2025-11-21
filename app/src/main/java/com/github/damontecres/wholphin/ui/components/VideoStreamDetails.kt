package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.data.model.chooseSource
import com.github.damontecres.wholphin.data.model.chooseStream
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.playback.audioStreamCount
import com.github.damontecres.wholphin.ui.playback.embeddedSubtitleCount
import com.github.damontecres.wholphin.ui.playback.externalSubtitlesCount
import com.github.damontecres.wholphin.util.languageName
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRange

@Composable
fun VideoStreamDetails(
    preferences: UserPreferences,
    dto: BaseItemDto,
    itemPlayback: ItemPlayback?,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        val source = remember(dto, itemPlayback) { chooseSource(dto, itemPlayback) }

        val videoStream = remember(dto, itemPlayback) { chooseStream(dto, itemPlayback, MediaStreamType.VIDEO, preferences) }
        val video =
            remember(videoStream) {
                videoStream?.let {
                    val width = it.width
                    val height = it.height
                    var resName =
                        if (width != null && height != null) resolutionString(width, height) else null
                    resName = resName?.let { it + if (videoStream.isInterlaced) "i" else "p" }
                    listOfNotNull(
                        resName,
                        it.codec?.uppercase(),
                        it.videoDoViTitle,
                        it.videoRange.takeIf { it == VideoRange.HDR },
                    ).joinToString(" ")
                }
            } ?: stringResource(R.string.none)
        TitleValueText(
            stringResource(R.string.video),
            video,
            modifier = Modifier.widthIn(max = 160.dp),
        )

        val audioStream = remember(dto, itemPlayback) { chooseStream(dto, itemPlayback, MediaStreamType.AUDIO, preferences) }
        val audioCount = remember(source) { source?.audioStreamCount ?: 0 }
        val audio =
            if (audioCount == 0 || audioStream == null) {
                stringResource(R.string.none)
            } else {
                val title =
                    listOfNotNull(
                        languageName(audioStream.language),
                        audioStream.codec?.uppercase(),
                        audioStream.channelLayout,
                    ).joinToString(" ")
                listOfNotNull(title, "(+${audioCount - 1})".takeIf { audioCount > 1 }).joinToString(" ")
            }
        TitleValueText(
            stringResource(R.string.audio),
            audio,
            modifier = Modifier.widthIn(max = 200.dp),
        )

        val subtitleStream = remember(dto, itemPlayback) { chooseStream(dto, itemPlayback, MediaStreamType.SUBTITLE, preferences) }
        val subtitleCount = remember(source) { (source?.embeddedSubtitleCount ?: 0) + (source?.externalSubtitlesCount ?: 0) }
        val subtitle =
            if (itemPlayback?.subtitleIndex == TrackIndex.DISABLED) {
                stringResource(R.string.disabled) + " (+$subtitleCount)"
            } else if (subtitleCount == 0 || subtitleStream == null) {
                null
            } else {
                listOfNotNull(
                    languageName(subtitleStream.language),
                    subtitleStream.codec?.uppercase(),
                    "(+${subtitleCount - 1})".takeIf { subtitleCount > 1 },
                ).joinToString(" ")
            }
        subtitle?.let {
            TitleValueText(
                stringResource(R.string.subtitles),
                it,
                valueTextOverflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
        }
    }
}

// Adapted from https://github.com/jellyfin/jellyfin/blob/aa4ddd139a7c01889a99561fc314121ba198dd70/MediaBrowser.Model/Entities/MediaStream.cs#L714
fun resolutionString(
    width: Int,
    height: Int,
): String =
    if (height > width) {
        // Vertical video
        resolutionString(height, width)
    } else {
        when {
            width <= 256 && height <= 144 -> "144"
            width <= 426 && height <= 240 -> "240"
            width <= 640 && height <= 360 -> "360"
            width <= 682 && height <= 384 -> "384"
            width <= 720 && height <= 404 -> "404"
            width <= 854 && height <= 480 -> "480"
            width <= 960 && height <= 544 -> "540"
            width <= 1024 && height <= 576 -> "576"
            width <= 1280 && height <= 962 -> "720"
            width <= 2560 && height <= 1440 -> "1080"
            width <= 4096 && height <= 3072 -> "4K"
            width <= 8192 && height <= 6144 -> "8K"
            else -> height.toString()
        }
    }
