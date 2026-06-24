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

    val VideoTunneling =
        AppSwitchPreference<AppPreferences>(
            title = R.string.video_tunneling,
            defaultValue = false,
            getter = { it.experimentalPreferences.videoTunnelingEnabled },
            setter = { prefs, value ->
                prefs.updateExperimentalPreferences { videoTunnelingEnabled = value }
            },
            summaryOn = R.string.enabled,
            summaryOff = R.string.disabled,
        )
}

val experimentalPreferences =
    buildList {
        add(
            PreferenceGroup(
                title = R.string.experimental_settings,
                preferences =
                    listOf(
                        ExperimentalPreference.VideoTunneling,
                    ),
            ),
        )
    }

/**
 * Get a value from [ExperimentalPreference] or null if not enabled
 */
fun <T> ExperimentalPreferences.get(block: ExperimentalPreferences.() -> T): T? = if (enabled) block.invoke(this) else null

fun ExperimentalPreferences.enabled(block: ExperimentalPreferences.() -> Boolean): Boolean = enabled && block.invoke(this)
