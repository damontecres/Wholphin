package com.github.damontecres.wholphin.preferences

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinUserPreferences
import com.github.damontecres.wholphin.ui.preferences.PreferenceGroup

/**
 * Represents settings from the user's profile on the server that may be overridden within the app
 */
object UserProfileSettings {
    /**
     * Special value that means the preferred language should be taken from the user's profile on the server
     */
    const val USE_USER_PROFILE = ""

    /**
     * Special value that means the user has no preferred language
     */
    const val PREFER_ANY_LANGUAGE = "_any-language"

    val PreferredAudioLang =
        AppClickablePreference<JellyfinUserPreferences>(
            title = R.string.preferred_audio_language,
            getter = { },
            setter = { prefs, _ -> prefs },
        )

    val PreferredSubtitleLang =
        AppClickablePreference<JellyfinUserPreferences>(
            title = R.string.preferred_subtitle_language,
            summary = null,
            getter = { },
            setter = { prefs, _ -> prefs },
        )

    val SubtitleModePref =
        AppChoicePreference<JellyfinUserPreferences, SubtitleModePreference>(
            title = R.string.subtitle_mode,
            defaultValue = SubtitleModePreference.USE_USER_PROFILE,
            getter = { it.subtitleMode },
            setter = { prefs, value ->
                prefs.copy(subtitleMode = value)
            },
            displayValues = R.array.subtitle_mode_options,
            indexToValue = { SubtitleModePreference.entries[it] },
            valueToIndex = { it.ordinal },
        )

    val Preferences =
        listOf(
            PreferenceGroup(
                title = R.string.profile_specific_settings_from_server,
                preferences =
                    listOf(
                        PreferredAudioLang,
                        PreferredSubtitleLang,
                        SubtitleModePref,
                    ),
            ),
        )
}

enum class SubtitleModePreference {
    USE_USER_PROFILE,
    DEFAULT,
    SMART,
    ONLY_FORCED,
    ALWAYS,
    NONE,
}
