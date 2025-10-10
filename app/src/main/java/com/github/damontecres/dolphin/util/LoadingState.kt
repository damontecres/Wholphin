package com.github.damontecres.dolphin.util

/**
 * Generic state for loading something from the API
 */
sealed interface LoadingState {
    object Loading : LoadingState

    object Success : LoadingState

    data class Error(
        val message: String? = null,
        val exception: Throwable? = null,
    ) : LoadingState
}
