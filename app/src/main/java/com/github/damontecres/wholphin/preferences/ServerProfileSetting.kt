package com.github.damontecres.wholphin.preferences

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.preferences.PreferenceGroup

object ServerProfileSetting {
    const val PREFER_ANY_LANGUAGE = "_any-language"

    val PreferredAudioLang =
        AppClickablePreference<AppPreferences>(
            title = R.string.preferred_audio_language,
            getter = { },
            setter = { prefs, _ -> prefs },
        )

    val PreferredSubtitleLang =
        AppClickablePreference<AppPreferences>(
            title = R.string.preferred_subtitle_language,
            summary = null,
            getter = { },
            setter = { prefs, _ -> prefs },
        )

    val SubtitleModePref =
        AppChoicePreference<AppPreferences, SubtitleMode>(
            title = R.string.subtitle_mode,
            defaultValue = SubtitleMode.SUBTITLE_MODE_SERVER_VALUE,
            getter = { it.serverProfileOverrides.preferredSubtitleMode },
            setter = { prefs, value ->
                prefs.updateServerProfileOverrides { preferredSubtitleMode = value }
            },
            displayValues = R.array.subtitle_mode_options,
            indexToValue = { SubtitleMode.forNumber(it) },
            valueToIndex = { if (it != SubtitleMode.UNRECOGNIZED) it.number else 0 },
        )

    val Preferences =
        listOf(
            PreferenceGroup(
                title = R.string.profile_specific_settings,
                preferences =
                    listOf(
                        PreferredAudioLang,
                        PreferredSubtitleLang,
                        SubtitleModePref,
                    ),
            ),
        )
}
