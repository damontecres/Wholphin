package com.github.damontecres.wholphin.preferences

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.preferences.PreferenceGroup

object CardPreferences {
    val ShowWatchIndicator =
        AppSwitchPreference<CardInterfacePreferences>(
            title = R.string.unknown,
            defaultValue = true,
            getter = { it.watchedIndicator },
            setter = { prefs, value ->
                prefs.update { watchedIndicator = value }
            },
            summaryOn = R.string.show,
            summaryOff = R.string.hide,
        )

    val ShowWatchProgress =
        AppSwitchPreference<CardInterfacePreferences>(
            title = R.string.unknown,
            defaultValue = true,
            getter = { it.watchedProgress },
            setter = { prefs, value ->
                prefs.update { watchedProgress = value }
            },
            summaryOn = R.string.show,
            summaryOff = R.string.hide,
        )

    val Preferences =
        listOf(
            PreferenceGroup(
                title = R.string.ui_interface,
                preferences =
                    listOf(
                        ShowWatchIndicator,
                        ShowWatchProgress,
                    ),
            ),
        )
}

inline fun CardInterfacePreferences.update(block: CardInterfacePreferences.Builder.() -> Unit): CardInterfacePreferences =
    toBuilder().apply(block).build()
