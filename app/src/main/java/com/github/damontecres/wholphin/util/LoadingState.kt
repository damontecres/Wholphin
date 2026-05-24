package com.github.damontecres.wholphin.util

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.ui.util.StringProvider

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
    val title: StringProvider

    val completed: Boolean
        get() = this is Success || this is Error

    data class Pending(
        override val title: StringProvider,
    ) : HomeRowLoadingState

    data class Loading(
        override val title: StringProvider,
    ) : HomeRowLoadingState

    data class Success(
        override val title: StringProvider,
        val items: List<BaseItem?>,
        val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
        val rowType: HomeRowConfig? = null,
    ) : HomeRowLoadingState

    data class Error(
        override val title: StringProvider,
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

val <T> DataLoadingState<T>.successValue: T? get() = (this as? DataLoadingState.Success<T>)?.data
