package com.github.damontecres.wholphin.ui.util

import androidx.compose.runtime.staticCompositionLocalOf
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.DisplayToggle
import com.github.damontecres.wholphin.ui.cards.ItemCardImageType
import java.util.EnumSet

/**
 * Represents various UI preferences made available via [LocalInterfaceCustomization]
 */
data class InterfaceCustomization(
    val enabledDisplayToggles: EnumSet<DisplayToggle> = EnumSet.allOf(DisplayToggle::class.java),
    val watchedIndicator: EnumSet<ItemCardImageType> = EnumSet.allOf(ItemCardImageType::class.java),
    val watchProgress: EnumSet<ItemCardImageType> = EnumSet.allOf(ItemCardImageType::class.java),
    val favoriteIndicator: Boolean = true,
    val versionsIndicator: Boolean = true,
    val unwatchedCount: Boolean = true,
    val episodeNum: Boolean = true,
    val seasonNum: Boolean = false,
) {
    companion object {
        fun from(prefs: AppPreferences): InterfaceCustomization =
            prefs.interfacePreferences.let { p ->
                val enabledDisplayToggles =
                    if (p.displayTogglesList.isEmpty()) {
                        EnumSet.noneOf(DisplayToggle::class.java)
                    } else {
                        EnumSet.copyOf(p.displayTogglesList)
                    }
                val c = p.cardInterfacePreferences
                val watchedIndicator =
                    c.watchedIndicatorList.map { ItemCardImageType.from(it) }.let { EnumSet.copyOf(it) }
                val watchProgress =
                    c.watchedProgressList.map { ItemCardImageType.from(it) }.let { EnumSet.copyOf(it) }

                InterfaceCustomization(
                    enabledDisplayToggles = enabledDisplayToggles,
                    watchProgress = watchProgress,
                    watchedIndicator = watchedIndicator,
                    favoriteIndicator = c.favoriteIndicator,
                    unwatchedCount = c.unwatchedCount,
                    episodeNum = c.episodeEpisodeNumber,
                    seasonNum = c.episodeSeasonNumber,
                    versionsIndicator = c.versionsIndicator,
                )
            }
    }
}

val LocalInterfaceCustomization = staticCompositionLocalOf<InterfaceCustomization> { InterfaceCustomization() }
