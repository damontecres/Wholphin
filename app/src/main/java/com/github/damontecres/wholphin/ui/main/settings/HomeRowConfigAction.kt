package com.github.damontecres.wholphin.ui.main.settings

sealed interface HomeRowConfigAction {
    data object Combine : HomeRowConfigAction

    data object Split : HomeRowConfigAction
}
