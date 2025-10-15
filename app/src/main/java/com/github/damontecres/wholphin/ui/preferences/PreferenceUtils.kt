package com.github.damontecres.wholphin.ui.preferences

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.preferences.AppPreference
import kotlinx.serialization.Serializable

/**
 * A group of preferences
 */
data class PreferenceGroup(
    @param:StringRes val title: Int,
    val preferences: List<AppPreference<out Any?>>,
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
    ;

    companion object {
        fun fromString(name: String?) = entries.firstOrNull { it.name == name } ?: BASIC
    }
}
