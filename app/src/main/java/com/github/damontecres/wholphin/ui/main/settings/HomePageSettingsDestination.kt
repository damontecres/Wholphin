package com.github.damontecres.wholphin.ui.main.settings

import java.util.UUID

sealed interface HomePageSettingsDestination {
    data object RowList : HomePageSettingsDestination

    data object ChooseLibrary : HomePageSettingsDestination

    data class ChooseRowType(
        val library: Library,
    ) : HomePageSettingsDestination

    data class RowSettings(
        val rowId: UUID,
    ) : HomePageSettingsDestination
}
