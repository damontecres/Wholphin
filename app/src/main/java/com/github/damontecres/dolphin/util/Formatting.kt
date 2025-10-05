package com.github.damontecres.dolphin.util

import android.os.Build
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
