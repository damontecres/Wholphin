package com.github.damontecres.dolphin.util

import android.widget.Toast
import com.github.damontecres.dolphin.DolphinApplication
import com.github.damontecres.dolphin.ui.showToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

/**
 * A general [CoroutineExceptionHandler] which can optionally show a [Toast] when an exception is thrown
 *
 * @param autoToast automatically show a toast with the exception's message
 */
class ExceptionHandler(
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

        if (autoToast) {
            runBlocking {
                showToast(
                    DolphinApplication.instance,
                    "Error: ${exception.message}",
                    Toast.LENGTH_LONG,
                )
            }
        }
    }
}
