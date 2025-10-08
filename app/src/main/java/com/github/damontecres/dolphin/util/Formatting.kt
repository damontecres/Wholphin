package com.github.damontecres.dolphin.util

import android.os.Build
import org.jellyfin.sdk.model.api.BaseItemDto
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun formatDateTime(dateTime: LocalDateTime): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
        formatter.format(dateTime)
    } else if (dateTime.toString().length >= 10) {
        dateTime.toString().substring(0, 10)
    } else {
        dateTime.toString()
    }

val BaseItemDto.seasonEpisode: String?
    get() =
        if (parentIndexNumber != null && indexNumber != null && indexNumberEnd != null) {
            "S$parentIndexNumber E$indexNumber-E$indexNumberEnd"
        } else if (parentIndexNumber != null && indexNumber != null) {
            "S$parentIndexNumber E$indexNumber"
        } else {
            null
        }

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
