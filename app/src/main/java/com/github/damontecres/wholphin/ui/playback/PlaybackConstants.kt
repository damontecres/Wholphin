package com.github.damontecres.wholphin.ui.playback

import androidx.compose.ui.layout.ContentScale
import com.github.damontecres.wholphin.preferences.PrefContentScale

val playbackSpeedOptions = listOf(".25", ".5", ".75", "1.0", "1.25", "1.5", "1.75", "2.0")

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

val PrefContentScale.scale: ContentScale
    get() =
        when (this) {
            PrefContentScale.FIT -> ContentScale.Fit
            PrefContentScale.NONE -> ContentScale.None
            PrefContentScale.CROP -> ContentScale.Crop
            PrefContentScale.FILL -> ContentScale.FillBounds
            PrefContentScale.Fill_WIDTH -> ContentScale.FillWidth
            PrefContentScale.FILL_HEIGHT -> ContentScale.FillHeight
            PrefContentScale.UNRECOGNIZED -> ContentScale.Fit
        }
