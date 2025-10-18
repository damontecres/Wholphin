package com.github.damontecres.wholphin.util

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.choseStream
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Format a [LocalDateTime] as `Aug 24, 2000`
 */
fun formatDateTime(dateTime: LocalDateTime): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // TODO server returns in UTC, but sdk converts to local time
        // eg 2020-02-14T00:00:00.0000000Z => 2020-02-13T17:00:00 PT => Feb 13, 2020
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
        formatter.format(dateTime)
    } else if (dateTime.toString().length >= 10) {
        dateTime.toString().substring(0, 10)
    } else {
        dateTime.toString()
    }

/**
 * If the item has season & episode info, format as `S# E#`
 */
val BaseItemDto.seasonEpisode: String?
    get() =
        if (parentIndexNumber != null && indexNumber != null && indexNumberEnd != null) {
            "S$parentIndexNumber E$indexNumber-E$indexNumberEnd"
        } else if (parentIndexNumber != null && indexNumber != null) {
            "S$parentIndexNumber E$indexNumber"
        } else {
            null
        }

/**
 * If the item has season & episode info, format padded as `S## E##`
 */
val BaseItemDto.seasonEpisodePadded: String?
    get() =
        if (parentIndexNumber != null && indexNumber != null) {
            val season = parentIndexNumber?.toString()?.padStart(2, '0')
            val episode = indexNumber?.toString()?.padStart(2, '0')
            val endEpisode = indexNumberEnd?.toString()?.padStart(2, '0')
            if (endEpisode != null) {
                "S${season}E$episode-E$endEpisode"
            } else {
                "S${season}E$episode"
            }
        } else {
            null
        }

fun formatSubtitleLang(mediaStreams: List<MediaStream>?): String? =
    mediaStreams
        ?.filter { it.type == MediaStreamType.SUBTITLE && it.language.isNotNullOrBlank() }
        ?.mapNotNull { it.language }
        ?.distinct()
        ?.joinToString(", ") { languageName(it) }

fun getAudioDisplay(
    item: BaseItemDto,
    chosenStreams: ChosenStreams?,
) = getAudioDisplay(item, chosenStreams?.itemPlayback)

fun getAudioDisplay(
    item: BaseItemDto,
    itemPlayback: ItemPlayback?,
) = choseStream(item, itemPlayback, MediaStreamType.AUDIO)
    ?.displayTitle
    ?.replace(" - Default", "")
    ?.ifBlank { null }

@Composable
fun getSubtitleDisplay(
    item: BaseItemDto,
    chosenStreams: ChosenStreams?,
) = if (chosenStreams?.subtitlesDisabled == true) {
    stringResource(R.string.disabled)
} else if (chosenStreams?.subtitleStream != null) {
    languageName(chosenStreams.subtitleStream.language)
} else {
    formatSubtitleLang(item.mediaStreams)
}
