package com.github.damontecres.wholphin.ui.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.res.stringResource
import kotlinx.serialization.Serializable

/**
 * Flexible way to provide a string for the UI
 */
@Serializable
sealed interface StringProvider {
    @Composable
    @NonRestartableComposable
    fun getString(): String
}

/**
 * Provides a string literal
 */
@Serializable
data class StringStringProvider(
    val str: String,
) : StringProvider {
    @Composable
    @NonRestartableComposable
    override fun getString(): String = str
}

/**
 * Provide an empty string literal
 */
@Serializable
data object EmptyStringProvider : StringProvider {
    @Composable
    @NonRestartableComposable
    override fun getString(): String = ""
}

/**
 * Provides a string resource using [stringResource]
 */
@Serializable
data class ResStringProvider(
    @param:StringRes val stringResId: Int,
) : StringProvider {
    @Composable
    @NonRestartableComposable
    override fun getString() = stringResource(stringResId)
}

/**
 * Provides a string resource with format arguments using [stringResource]
 */
@Serializable
data class ResArgStringProvider(
    @param:StringRes val stringResId: Int,
    val args: Array<String>,
) : StringProvider {
    constructor(stringResId: Int, arg: String) : this(stringResId, arrayOf(arg))

    @Composable
    @NonRestartableComposable
    override fun getString() = stringResource(stringResId, *args)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResArgStringProvider) return false

        if (stringResId != other.stringResId) return false
        if (!args.contentEquals(other.args)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = stringResId
        result = 31 * result + args.contentHashCode()
        return result
    }
}

/**
 * Provides a string resource with another [StringProvider] format argument using [stringResource]
 */
@Serializable
data class ResProviderStringProvider(
    @param:StringRes val stringResId: Int,
    val argProvider: StringProvider,
) : StringProvider {
    @Composable
    @NonRestartableComposable
    override fun getString(): String = stringResource(stringResId, argProvider.getString())
}
