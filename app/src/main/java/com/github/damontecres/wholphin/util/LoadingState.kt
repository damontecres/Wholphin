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

sealed interface HomeRowLoadingState {
    val title: String

    data class Pending(
        override val title: String,
    ) : HomeRowLoadingState

    data class Loading(
        override val title: String,
    ) : HomeRowLoadingState

    data class Success(
        override val title: String,
        val items: List<BaseItem?>,
    ) : HomeRowLoadingState

    data class Error(
        override val title: String,
        val message: String? = null,
        val exception: Throwable? = null,
    ) : HomeRowLoadingState {
        val localizedMessage: String =
            listOfNotNull(message, exception?.localizedMessage).joinToString(" - ")
    }
}
