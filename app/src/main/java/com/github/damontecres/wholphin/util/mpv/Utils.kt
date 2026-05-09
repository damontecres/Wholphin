package com.github.damontecres.wholphin.util.mpv

import androidx.compose.ui.graphics.Color
import com.github.damontecres.wholphin.mpv.MPVLib

fun MPVLib.setPropertyColor(
    property: String,
    color: Color,
) = setPropertyString(property, color.mpvFormat)

private val Color.mpvFormat: String get() = "$red/$green/$blue/$alpha"
