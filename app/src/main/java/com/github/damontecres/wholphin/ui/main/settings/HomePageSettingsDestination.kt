package com.github.damontecres.wholphin.ui.main.settings

import com.github.damontecres.wholphin.data.model.HomeRowConfigDisplay

sealed interface HomePageSettingsDestination {
    data object RowList : HomePageSettingsDestination

    data object ChooseLibrary : HomePageSettingsDestination

    data class ChooseRowType(
        val library: Library,
    ) : HomePageSettingsDestination

    data class RowSettings(
        val row: HomeRowConfigDisplay,
    ) : HomePageSettingsDestination
}
