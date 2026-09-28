package com.github.damontecres.wholphin.preferences

import org.jellyfin.sdk.model.api.VideoRangeType

// Adapted from https://github.com/jellyfin/jellyfin-androidtv/blob/master/app/src/main/java/org/jellyfin/androidtv/preference/constant/HdrFormat.kt

/**
 * HDR formats that the user can override in the device profile.
 */
enum class HdrFormat(
    val getter: (AppPreferences) -> HdrOverrideMode,
    val rangeTypeNames: (jellyfinTenEleven: Boolean) -> Set<String>,
) {
    HDR10(
        getter = { it.playbackPreferences.overrides.hdr10Override },
        rangeTypeNames = { setOf(VideoRangeType.HDR10.serialName) },
    ),
    HDR10_PLUS(
        getter = { it.playbackPreferences.overrides.hdr10PlusOverride },
        rangeTypeNames = { setOf(VideoRangeType.HDR10_PLUS.serialName) },
    ),
    DOVI_PROFILE_5(
        getter = { it.playbackPreferences.overrides.doviProfile5Override },
        rangeTypeNames = { setOf(VideoRangeType.DOVI.serialName) },
    ),
    DOVI_PROFILE_7(
        getter = { it.playbackPreferences.overrides.doviProfile7Override },
        // TODO Use VideoRangeType enum with Jellyfin 10.11 based SDK
        rangeTypeNames = { jellyfinTenEleven ->
            if (jellyfinTenEleven) setOf("DOVIWithEL", "DOVIWithELHDR10Plus") else emptySet()
        },
    ),
    DOVI_PROFILE_8(
        getter = { it.playbackPreferences.overrides.doviProfile8Override },
        rangeTypeNames = { jellyfinTenEleven ->
            buildSet {
                add(VideoRangeType.DOVI_WITH_HDR10.serialName)
                // TODO Use VideoRangeType enum with Jellyfin 10.11 based SDK
                if (jellyfinTenEleven) add("DOVIWithHDR10Plus")
            }
        },
    ),
}

/**
 * Collects the video range type names for every [HdrFormat] whose preference is currently set to [mode]
 */
fun AppPreferences.getHdrRangeTypesFor(
    mode: HdrOverrideMode,
    jellyfinTenEleven: Boolean,
): Set<String> =
    HdrFormat.entries
        .filter { it.getter(this) == mode }
        .flatMapTo(mutableSetOf()) { it.rangeTypeNames(jellyfinTenEleven) }
