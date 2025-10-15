package com.github.damontecres.wholphin.util

/**
 * Generic state for loading something from the API
 */
sealed interface LoadingState {
    object Pending : LoadingState

    object Loading : LoadingState

    object Success : LoadingState

    data class Error(
        val message: String? = null,
        val exception: Throwable? = null,
    ) : LoadingState {
        constructor(exception: Throwable) : this(null, exception)
    }
}
