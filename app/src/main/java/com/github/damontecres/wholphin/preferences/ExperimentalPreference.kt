package com.github.damontecres.wholphin.preferences

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.PreferenceGroup
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.wholphin.ui.preferences.PreferenceValidation

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

    val PreferAc3ForSurround =
        AppSwitchPreference<AppPreferences>(
            title = R.string.prefer_ac3_for_surround,
            defaultValue = true,
            getter = { it.experimentalPreferences.preferAc3Surround },
            setter = { prefs, value ->
                prefs.updateExperimentalPreferences {
                    preferAc3Surround = value
                }
            },
            summary = R.string.prefer_ac3_for_surround_summary,
            validator = { prefs, value ->
                prefs.playbackPreferences.overrides.let {
                    if (value && !it.ac3Supported) {
                        PreferenceValidation.Invalid("AC3 support is not enabled")
                    } else if (value && it.downmixStereo) {
                        PreferenceValidation.Invalid("Always downmixing to stereo")
                    } else {
                        PreferenceValidation.Valid
                    }
                }
            },
        )

    val DisableAudioOffload =
        AppSwitchPreference<AppPreferences>(
            title = R.string.disable_audio_offload,
            defaultValue = false,
            getter = { it.experimentalPreferences.disableAudioOffload },
            setter = { prefs, value ->
                prefs.updateExperimentalPreferences { disableAudioOffload = value }
            },
            summary = R.string.disable_audio_offload_summary,
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
                        ExperimentalPreference.PreferAc3ForSurround,
                        ExperimentalPreference.DisableAudioOffload,
                    ),
            ),
        )
    }

/**
 * Get a value from [ExperimentalPreference] or null if not enabled
 */
fun <T> ExperimentalPreferences.get(block: ExperimentalPreferences.() -> T): T? = if (enabled) block.invoke(this) else null

fun ExperimentalPreferences.enabled(block: ExperimentalPreferences.() -> Boolean): Boolean = enabled && block.invoke(this)
