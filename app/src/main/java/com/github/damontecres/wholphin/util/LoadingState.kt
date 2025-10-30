package com.github.damontecres.wholphin.util

import com.github.damontecres.wholphin.data.model.BaseItem

/**
 * Generic state for loading something from the API
 */
sealed interface LoadingState {
    data object Pending : LoadingState

    data object Loading : LoadingState

    data object Success : LoadingState

    data class Error(
        val message: String? = null,
        val exception: Throwable? = null,
    ) : LoadingState {
        constructor(exception: Throwable) : this(null, exception)

        val localizedMessage: String =
            listOfNotNull(message, exception?.localizedMessage).joinToString(" - ")
    }
}

sealed interface RowLoadingState {
    data object Pending : RowLoadingState

    data object Loading : RowLoadingState

    data class Success(
        val items: List<BaseItem?>,
    ) : RowLoadingState

    data class Error(
        val message: String? = null,
        val exception: Throwable? = null,
    ) : RowLoadingState {
        constructor(exception: Throwable) : this(null, exception)

        val localizedMessage: String =
            listOfNotNull(message, exception?.localizedMessage).joinToString(" - ")
    }
}
