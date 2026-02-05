package com.github.damontecres.wholphin.ui.preferences

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.preferences.AppPreference
import kotlinx.serialization.Serializable

/**
 * A group of preferences
 */
data class PreferenceGroup<T>(
    @param:StringRes val title: Int,
    val preferences: List<AppPreference<T, out Any?>>,
    val conditionalPreferences: List<ConditionalPreferences<T>> = listOf(),
)

data class ConditionalPreferences<T>(
    val condition: (T) -> Boolean,
    val preferences: List<AppPreference<T, out Any?>>,
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
    EXO_PLAYER,
    MPV,
    ;

    companion object {
        fun fromString(name: String?) = entries.firstOrNull { it.name == name } ?: BASIC
    }
}
