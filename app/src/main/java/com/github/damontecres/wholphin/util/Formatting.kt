package com.github.damontecres.wholphin.util

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.chooseSource
import com.github.damontecres.wholphin.data.model.chooseStream
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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

fun formatDate(dateTime: LocalDate): String =
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

/**
 * Gets the selected audio display title for the given item & chosen streams
 */
fun getAudioDisplay(
    item: BaseItemDto,
    chosenStreams: ChosenStreams?,
    preferences: UserPreferences,
) = getAudioDisplay(item, chosenStreams?.itemPlayback, preferences)

/**
 * Gets the selected audio display title for the given item & chosen streams
 */
fun getAudioDisplay(
    item: BaseItemDto,
    itemPlayback: ItemPlayback?,
    preferences: UserPreferences,
) = chooseStream(item, itemPlayback, MediaStreamType.AUDIO, preferences)
    ?.displayTitle
    ?.replace(" - Default", "")
    ?.ifBlank { null }

/**
 * Gets the selected subtitle language for the given item & chosen streams
 *
 * If none are chosen, returns a concatenated list of languages available
 */
@Composable
fun getSubtitleDisplay(
    item: BaseItemDto,
    chosenStreams: ChosenStreams?,
) = if (chosenStreams?.subtitlesDisabled == true) {
    stringResource(R.string.disabled)
} else if (chosenStreams?.subtitleStream != null) {
    languageName(chosenStreams.subtitleStream.language)
} else {
    chooseSource(item, chosenStreams?.itemPlayback)?.let {
        formatSubtitleLang(it.mediaStreams)
    }
}

private val abbrevSuffixes = listOf("", "K", "M", "B")

/**
 * Format a number by abbreviation, eg 5533 => 5.5K
 */
fun abbreviateNumber(number: Int): String {
    if (number < 1000) {
        return number.toString()
    }
    var unit = 0
    var count = number.toDouble()
    while (count >= 1000 && unit + 1 < abbrevSuffixes.size) {
        count /= 1000
        unit++
    }
    return String.format(Locale.getDefault(), "%.1f%s", count, abbrevSuffixes[unit])
}

val byteSuffixes = listOf("B", "KB", "MB", "GB", "TB")
val byteRateSuffixes = listOf("bps", "kbps", "mbps", "gbps", "tbps")

/**
 * Format bytes
 */
fun formatBytes(
    bytes: Int,
    suffixes: List<String> = byteSuffixes,
) = formatBytes(bytes.toLong(), suffixes)

fun formatBytes(
    bytes: Long,
    suffixes: List<String> = byteSuffixes,
): String {
    var unit = 0
    var count = bytes.toDouble()
    while (count >= 1024 && unit + 1 < suffixes.size) {
        count /= 1024
        unit++
    }
    return String.format(Locale.getDefault(), "%.2f%s", count, suffixes[unit])
}

@get:StringRes
val MediaSegmentType.stringRes: Int
    get() =
        when (this) {
            MediaSegmentType.UNKNOWN -> R.string.unknown
            MediaSegmentType.COMMERCIAL -> R.string.commercial
            MediaSegmentType.PREVIEW -> R.string.preview
            MediaSegmentType.RECAP -> R.string.recap
            MediaSegmentType.OUTRO -> R.string.outro
            MediaSegmentType.INTRO -> R.string.intro
        }
