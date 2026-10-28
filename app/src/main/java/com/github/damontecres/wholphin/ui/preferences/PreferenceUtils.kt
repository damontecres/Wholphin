package com.github.damontecres.wholphin.ui.preferences

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
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
    SCREENSAVER,
    SKIP_SEGMENTS,
    EXPERIMENTAL,
    HDR_FORMATS,
    ;

    companion object {
        fun fromString(name: String?) = entries.firstOrNull { it.name == name } ?: BASIC
    }
}

@Composable
fun rememberSeerrDialogBody(
    user: SeerrUser,
    server: SeerrServer,
    pluginProvided: Boolean,
    ex: Exception? = null,
): String {
    val username =
        when (user.authMethod) {
            SeerrAuthMethod.LOCAL,
            SeerrAuthMethod.JELLYFIN,
            -> user.username ?: stringResource(R.string.unknown)

            SeerrAuthMethod.API_KEY -> stringResource(R.string.api_key)
        }
    val method =
        when (user.authMethod) {
            SeerrAuthMethod.LOCAL -> R.string.seerr_local_user
            SeerrAuthMethod.JELLYFIN -> R.string.seerr_jellyfin_user
            SeerrAuthMethod.API_KEY -> 0
        }.let {
            if (it != 0) " (${stringResource(it)})" else ""
        }

    val body =
        """
        ${stringResource(R.string.url)}: ${server.url}
        ${stringResource(R.string.username)}: $username$method
        """.trimIndent() +
            if (pluginProvided) {
                "\n\n${stringResource(R.string.managed_by_server)}"
            } else {
                ""
            } +
            if (ex != null) {
                "\n\n${stringResource(R.string.error)}: ${ex.localizedMessage}"
            } else {
                ""
            }

    return remember { body }
}
