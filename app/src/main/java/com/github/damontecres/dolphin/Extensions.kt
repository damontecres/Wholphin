package com.github.damontecres.dolphin

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
fun CharSequence?.isNotNullOrBlank(): Boolean {
    contract {
        returns(true) implies (this@isNotNullOrBlank != null)
    }
    return !this.isNullOrBlank()
}

/**
 * Try to call [FocusRequester.requestFocus], but catch & log the exception if something is not configured properly
 */
fun FocusRequester.tryRequestFocus(): Boolean =
    try {
        requestFocus()
        true
    } catch (ex: IllegalStateException) {
        Timber.w(ex, "Failed to request focus")
        false
    }

/**
 * Used to apply modifiers conditionally.
 */
fun Modifier.ifElse(
    condition: () -> Boolean,
    ifTrueModifier: Modifier,
    ifFalseModifier: Modifier = Modifier,
): Modifier = then(if (condition()) ifTrueModifier else ifFalseModifier)

/**
 * Used to apply modifiers conditionally.
 */
fun Modifier.ifElse(
    condition: Boolean,
    ifTrueModifier: Modifier,
    ifFalseModifier: Modifier = Modifier,
): Modifier = ifElse({ condition }, ifTrueModifier, ifFalseModifier)

/**
 * Handles horizontal (Left & Right) D-Pad Keys and consumes the event(s) so that the focus doesn't
 * accidentally move to another element.
 * */
fun Modifier.handleDPadKeyEvents(
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onCenter: (() -> Unit)? = null,
    triggerOnAction: Int = KeyEvent.ACTION_UP,
) = onPreviewKeyEvent {
    fun onActionUp(block: () -> Unit) {
        if (it.nativeKeyEvent.action == triggerOnAction) block()
    }

    when (it.nativeKeyEvent.keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> {
            onLeft?.apply {
                onActionUp(::invoke)
                return@onPreviewKeyEvent true
            }
        }

        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> {
            onRight?.apply {
                onActionUp(::invoke)
                return@onPreviewKeyEvent true
            }
        }

        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            onCenter?.apply {
                onActionUp(::invoke)
                return@onPreviewKeyEvent true
            }
        }
    }

    false
}

/**
 * Run a [LaunchedEffect] exactly once even with multiple recompositions.
 *
 * If the composition is removed from the navigation back stack and "re-added", this will run again
 */
@Composable
fun OneTimeLaunchedEffect(runOnceBlock: suspend CoroutineScope.() -> Unit) {
    var hasRun by rememberSaveable { mutableStateOf(false) }
    if (!hasRun) {
        LaunchedEffect(Unit) {
            hasRun = true
            runOnceBlock.invoke(this)
        }
    }
}
