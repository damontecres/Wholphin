package com.github.damontecres.wholphin.preferences

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.PreferenceGroup
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption

object ExperimentalPreference {
    val Enable =
        AppSwitchPreference<AppPreferences>(
            title = R.string.experimental_settings,
            defaultValue = false,
            getter = { it.experimentalPreferences.enabled },
            setter = { prefs, value ->
                prefs.updateExperimentalPreferences { enabled = value }
            },
            summaryOn = R.string.enabled,
            summaryOff = R.string.disabled,
        )

    val ExperimentalSettings =
        AppDestinationPreference<AppPreferences>(
            title = R.string.experimental_settings,
            destination = Destination.Settings(PreferenceScreenOption.EXPERIMENTAL),
        )
}

val experimentalPreferences =
    buildList {
        add(
            PreferenceGroup(
                title = R.string.experimental_settings,
                preferences =
                    listOf(
                        // TODO
                        AppPreference.ShowClock,
                    ),
            ),
        )
    }

fun <T> ExperimentalPreferences.get(block: ExperimentalPreferences.() -> T): T? = if (enabled) block.invoke(this) else null

fun test() {
    ExperimentalPreferences.newBuilder().build().get { }
}
