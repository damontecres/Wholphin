package com.github.damontecres.wholphin.util

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.ui.components.Genre

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

    data class Pending(
        override val title: String,
    ) : HomeRowLoadingState

    data class Loading(
        override val title: String,
    ) : HomeRowLoadingState

    data class Success(
        override val title: String,
        val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
        val data: HomeRow,
    ) : HomeRowLoadingState {
        constructor(
            title: String,
            items: List<BaseItem?>,
            viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
        ) : this(title, viewOptions, HomeRow.BaseItemHomeRow(items))
    }

    data class Error(
        override val title: String,
        val message: String? = null,
        val exception: Throwable? = null,
    ) : HomeRowLoadingState {
        val localizedMessage: String =
            listOfNotNull(message, exception?.localizedMessage).joinToString(" - ")
    }
}

sealed interface HomeRow {
    fun isEmpty(): Boolean

    fun isNotEmpty(): Boolean = !isEmpty()

    data class BaseItemHomeRow(
        val items: List<BaseItem?>,
    ) : HomeRow {
        override fun isEmpty(): Boolean = items.isEmpty()
    }

    data class GenreHowRow(
        val items: List<Genre>,
    ) : HomeRow {
        override fun isEmpty(): Boolean = items.isEmpty()
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
