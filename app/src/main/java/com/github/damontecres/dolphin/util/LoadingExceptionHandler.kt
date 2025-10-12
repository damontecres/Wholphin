package com.github.damontecres.dolphin.util

import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import com.github.damontecres.dolphin.DolphinApplication
import com.github.damontecres.dolphin.ui.showToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

/**
 * A [CoroutineExceptionHandler] that is aware of a [LoadingState] and will set it to error if an exception occurs in the coroutine.
 */
class LoadingExceptionHandler(
    private val loadingState: MutableLiveData<LoadingState>,
    private val errorMessage: String?,
    private val autoToast: Boolean = false,
) : CoroutineExceptionHandler {
    override val key: CoroutineContext.Key<*>
        get() = CoroutineExceptionHandler

    override fun handleException(
        context: CoroutineContext,
        exception: Throwable,
    ) {
        if (exception is CancellationException) {
            // Don't log/toast cancellations
            return
        }
        Timber.e(exception, "Exception in coroutine")
        runBlocking {
            withContext(Dispatchers.Main) {
                loadingState.value =
                    LoadingState.Error(
                        message = errorMessage,
                        exception = exception,
                    )
            }
            if (autoToast) {
                showToast(
                    DolphinApplication.instance,
                    "Error: ${exception.message}",
                    Toast.LENGTH_LONG,
                )
            }
        }
    }
}
