package com.github.damontecres.dolphin.ui.playback

import androidx.compose.ui.layout.ContentScale

val playbackScaleOptions =
    mapOf(
        ContentScale.Fit to "Fit",
        ContentScale.None to "None",
        ContentScale.Crop to "Crop",
//        ContentScale.Inside to "Inside",
        ContentScale.FillBounds to "Fill",
        ContentScale.FillWidth to "Fill Width",
        ContentScale.FillHeight to "Fill Height",
    )
