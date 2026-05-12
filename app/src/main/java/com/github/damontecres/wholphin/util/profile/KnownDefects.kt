package com.github.damontecres.wholphin.util.profile

// Copied from https://github.com/jellyfin/jellyfin-androidtv/blob/v0.19.4/app/src/main/java/org/jellyfin/androidtv/util/profile/KnownDefects.kt

import android.os.Build

/**
 * List of device models with known HEVC DoVi/HDR10+ playback issues.
 */
private val modelsWithDoViHdr10PlusBug =
    listOf(
        "AFTKRT", // Amazon Fire TV 4K Max (2nd Gen)
        "AFTKA", // Amazon Fire TV 4K Max (1st Gen)
        "AFTKM", // Amazon Fire TV 4K (2nd Gen)
    )

/**
 * List of device models that support H264 Hi10P 5.2, but don't advertise it
 *
 * Amazon devices from https://developer.amazon.com/docs/device-specs/device-specifications-fire-tv-streaming-media-player.html
 */
private val modelsWithHi10P52Support =
    listOf(
        "AFTMA08C15", // Fire TV Stick 4K Plus (2025)
        "AFTKRT", // Amazon Fire TV 4K Max (2nd Gen)
        "AFTKA", // Amazon Fire TV 4K Max (1st Gen)
        "AFTKM", // Amazon Fire TV 4K (2nd Gen)
        "AFTMM", // Fire TV Stick 4K - 1st Gen (2018)
    )

object KnownDefects {
    val hevcDoviHdr10PlusBug = Build.MODEL in modelsWithDoViHdr10PlusBug
    val supportsHi10P52 = Build.MODEL in modelsWithHi10P52Support
}
