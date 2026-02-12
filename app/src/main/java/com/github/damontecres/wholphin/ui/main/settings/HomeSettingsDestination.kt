package com.github.damontecres.wholphin.ui.main.settings

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Tracking the pages for selecting and configuring rows
 */
@Serializable
sealed interface HomeSettingsDestination : NavKey {
    @Serializable
    data object RowList : HomeSettingsDestination

    @Serializable
    data object AddRow : HomeSettingsDestination

    @Serializable
    data class ChooseRowType(
        val library: Library,
    ) : HomeSettingsDestination

    @Serializable
    data class RowSettings(
        val rowId: Int,
    ) : HomeSettingsDestination

    @Serializable
    data object ChooseFavorite : HomeSettingsDestination

    @Serializable
    data object ChooseDiscover : HomeSettingsDestination

    @Serializable
    data object GlobalSettings : HomeSettingsDestination

    @Serializable
    data object Presets : HomeSettingsDestination
}
