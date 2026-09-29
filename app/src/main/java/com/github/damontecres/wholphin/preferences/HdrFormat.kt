package com.github.damontecres.wholphin.preferences

import com.github.damontecres.wholphin.util.profile.WholphinVideoRangeType

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
        rangeTypeNames = { setOf(WholphinVideoRangeType.HDR10.serialName) },
    ),
    HDR10_PLUS(
        getter = { it.playbackPreferences.overrides.hdr10PlusOverride },
        rangeTypeNames = { setOf(WholphinVideoRangeType.HDR10_PLUS.serialName) },
    ),
    DOVI_PROFILE_5(
        getter = { it.playbackPreferences.overrides.doviProfile5Override },
        rangeTypeNames = { setOf(WholphinVideoRangeType.DOVI.serialName) },
    ),
    DOVI_PROFILE_7(
        getter = { it.playbackPreferences.overrides.doviProfile7Override },
        rangeTypeNames = { jellyfinTenEleven ->
            if (jellyfinTenEleven) {
                setOf(WholphinVideoRangeType.DOVI_WITH_EL.serialName, WholphinVideoRangeType.DOVI_WITH_EL_HDR10_PLUS.serialName)
            } else {
                emptySet()
            }
        },
    ),
    DOVI_PROFILE_8(
        getter = { it.playbackPreferences.overrides.doviProfile8Override },
        rangeTypeNames = { jellyfinTenEleven ->
            buildSet {
                add(WholphinVideoRangeType.DOVI_WITH_HDR10.serialName)
                if (jellyfinTenEleven) add(WholphinVideoRangeType.DOVI_WITH_HDR10_PLUS.serialName)
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
