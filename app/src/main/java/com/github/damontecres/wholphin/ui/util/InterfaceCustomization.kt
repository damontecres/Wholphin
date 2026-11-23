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
    val enabledDisplayToggles: EnumSet<DisplayToggle> = allOf(),
    val watchedIndicator: EnumSet<ItemCardImageType> = allOf(),
    val watchProgress: EnumSet<ItemCardImageType> = allOf(),
    val favoriteIndicator: Boolean = true,
    val versionsIndicator: Boolean = true,
    val unwatchedCount: Boolean = true,
    val episodeNum: Boolean = true,
    val seasonNum: Boolean = false,
) {
    companion object {
        fun from(prefs: AppPreferences): InterfaceCustomization =
            prefs.interfacePreferences.let { p ->
                val enabledDisplayToggles = p.displayTogglesList.toEnumSet()

                val c = p.cardInterfacePreferences
                val watchedIndicator = c.watchedIndicatorList.map { ItemCardImageType.from(it) }.toEnumSet()
                val watchProgress = c.watchedProgressList.map { ItemCardImageType.from(it) }.toEnumSet()

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

/**
 * Return an [EnumSet] that contains all enums in this [Collection]
 */
inline fun <reified T : Enum<T>> Collection<T>.toEnumSet(): EnumSet<T> =
    if (isEmpty()) EnumSet.noneOf(T::class.java) else EnumSet.copyOf(this)

/**
 * Return an [EnumSet] that contains all enums for the enum type
 */
inline fun <reified T : Enum<T>> allOf(): EnumSet<T> = EnumSet.allOf(T::class.java)
