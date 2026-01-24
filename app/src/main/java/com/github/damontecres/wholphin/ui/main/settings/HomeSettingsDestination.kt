package com.github.damontecres.wholphin.ui.main.settings

/**
 * Tracking the pages for selecting and configuring rows
 */
sealed interface HomeSettingsDestination {
    data object RowList : HomeSettingsDestination

    data object ChooseLibrary : HomeSettingsDestination

    data class ChooseRowType(
        val library: Library,
    ) : HomeSettingsDestination

    data class RowSettings(
        val rowId: Int,
    ) : HomeSettingsDestination
}
