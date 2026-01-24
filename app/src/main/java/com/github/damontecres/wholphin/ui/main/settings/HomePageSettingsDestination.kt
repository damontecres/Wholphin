package com.github.damontecres.wholphin.ui.main.settings

sealed interface HomePageSettingsDestination {
    data object RowList : HomePageSettingsDestination

    data object ChooseLibrary : HomePageSettingsDestination

    data class ChooseRowType(
        val library: Library,
    ) : HomePageSettingsDestination

    data class RowSettings(
        val rowId: Int,
    ) : HomePageSettingsDestination
}
