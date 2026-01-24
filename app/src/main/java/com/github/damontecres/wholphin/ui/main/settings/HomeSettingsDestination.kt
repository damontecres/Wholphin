package com.github.damontecres.wholphin.ui.main.settings

import androidx.navigation3.runtime.NavKey

/**
 * Tracking the pages for selecting and configuring rows
 */
sealed interface HomeSettingsDestination : NavKey {
    data object RowList : HomeSettingsDestination

    data object ChooseLibrary : HomeSettingsDestination

    data class ChooseRowType(
        val library: Library,
    ) : HomeSettingsDestination

    data class RowSettings(
        val rowId: Int,
    ) : HomeSettingsDestination
}
