package com.github.damontecres.wholphin.ui

import android.os.Build
import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

val TimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

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

@get:StringRes
val MediaSegmentType.skipStringRes: Int
    get() =
        when (this) {
            MediaSegmentType.UNKNOWN -> R.string.skip_segment_unknown
            MediaSegmentType.COMMERCIAL -> R.string.skip_segment_commercial
            MediaSegmentType.PREVIEW -> R.string.skip_segment_preview
            MediaSegmentType.RECAP -> R.string.skip_segment_recap
            MediaSegmentType.OUTRO -> R.string.skip_segment_outro
            MediaSegmentType.INTRO -> R.string.skip_segment_intro
        }
