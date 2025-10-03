package com.github.damontecres.dolphin.util

sealed interface LoadingState {
    object Loading : LoadingState

    object Success : LoadingState

    data class Error(
        val message: String? = null,
        val exception: Throwable? = null,
    ) : LoadingState
}
