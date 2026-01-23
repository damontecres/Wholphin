package com.github.damontecres.wholphin.ui.preferences

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import kotlinx.serialization.Serializable

/**
 * A group of preferences
 */
data class PreferenceGroup(
    @param:StringRes val title: Int,
    val preferences: List<AppPreference<AppPreferences, out Any?>>,
    val conditionalPreferences: List<ConditionalPreferences> = listOf(),
)

data class ConditionalPreferences(
    val condition: (AppPreferences) -> Boolean,
    val preferences: List<AppPreference<AppPreferences, out Any?>>,
)

/**
 * Results when validating a preference value.
 */
sealed interface PreferenceValidation {
    data object Valid : PreferenceValidation

    data class Invalid(
        val message: String,
    ) : PreferenceValidation
}

@Serializable
enum class PreferenceScreenOption {
    BASIC,
    ADVANCED,
    USER_INTERFACE,
    SUBTITLES,
    EXO_PLAYER,
    MPV,
    ;

    companion object {
        fun fromString(name: String?) = entries.firstOrNull { it.name == name } ?: BASIC
    }
}
