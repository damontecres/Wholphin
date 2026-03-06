package com.github.damontecres.wholphin.util

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions

/**
 * Generic state for loading something from the API
 *
 * @see DataLoadingState
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

    val completed: Boolean
        get() = this is Success || this is Error

    data class Pending(
        override val title: String,
    ) : HomeRowLoadingState

    data class Loading(
        override val title: String,
    ) : HomeRowLoadingState

    data class Success(
        override val title: String,
        val items: List<BaseItem?>,
        val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
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

/**
 * Generic state for loading something from the API
 *
 * @see LoadingState
 */
sealed interface DataLoadingState<out T> {
    data object Pending : DataLoadingState<Nothing>

    data object Loading : DataLoadingState<Nothing>

    data class Success<T>(
        val data: T,
    ) : DataLoadingState<T>

    data class Error(
        val message: String? = null,
        val exception: Throwable? = null,
    ) : DataLoadingState<Nothing> {
        constructor(exception: Throwable) : this(null, exception)

        val localizedMessage: String =
            listOfNotNull(message, exception?.localizedMessage).joinToString(" - ")
    }
}
