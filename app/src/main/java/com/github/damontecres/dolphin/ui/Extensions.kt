package com.github.damontecres.dolphin.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.request.ErrorResult
import com.github.damontecres.dolphin.ui.data.RowColumn
import com.github.damontecres.dolphin.ui.data.RowColumnSaver
import kotlinx.coroutines.CoroutineScope
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// This file is a dumping ground mostly for extensions

@OptIn(ExperimentalContracts::class)
fun CharSequence?.isNotNullOrBlank(): Boolean {
    contract {
        returns(true) implies (this@isNotNullOrBlank != null)
    }
    return !this.isNullOrBlank()
}

inline fun <T> List<T>.indexOfFirstOrNull(predicate: (T) -> Boolean): Int? {
    val index = this.indexOfFirst(predicate)
    return if (index >= 0) index else null
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

fun Modifier.ifElse(
    condition: Boolean,
    ifTrueModifier: () -> Modifier,
    ifFalseModifier: () -> Modifier = { Modifier },
): Modifier = then(if (condition) ifTrueModifier.invoke() else ifFalseModifier.invoke())

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

fun Modifier.enableMarquee(focused: Boolean) =
    if (focused) {
        basicMarquee(
            initialDelayMillis = 250,
            animationMode = MarqueeAnimationMode.Immediately,
            velocity = 40.dp,
        )
    } else {
        basicMarquee(animationMode = MarqueeAnimationMode.WhileFocused)
    }

@Composable
fun Modifier.playSoundOnFocus(enabled: Boolean): Modifier {
    if (!enabled) {
        return this
    }
    val context = LocalContext.current
    val audioManager =
        remember {
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }
    return onFocusChanged {
        if (it.isFocused) {
            audioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP)
        }
    }
}

fun playOnClickSound(
    context: Context,
    effectType: Int = AudioManager.FX_KEY_CLICK,
) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioManager.playSoundEffect(effectType)
}

/**
 * Rounds a [Duration] to nearest whole minute
 */
val Duration.roundMinutes: Duration
    get() = (this + 30.seconds).inWholeMinutes.minutes

/**
 * Rounds a [Duration] to nearest whole second
 */
val Duration.roundSeconds: Duration
    get() = (this + 30.milliseconds).inWholeSeconds.seconds

/**
 * Gets the user's playback position as a [Duration]
 */
val BaseItemDto.timeRemaining: Duration?
    get() =
        userData?.playbackPositionTicks?.let {
            if (it > 0) {
                runTimeTicks?.minus(it)?.ticks
            } else {
                null
            }
        }

/**
 * Seek back the current media item by a [Duration]
 */
fun Player.seekBack(amount: Duration) = seekTo((currentPosition - amount.inWholeMilliseconds).coerceAtLeast(0L))

/**
 * Seek forward the current media item by a [Duration]
 */
fun Player.seekForward(amount: Duration) = seekTo((currentPosition + amount.inWholeMilliseconds).coerceAtMost(duration))

/**
 * Like [let] but only if the collection is not empty, otherwise returns null
 */
@OptIn(ExperimentalContracts::class)
inline fun <T : Collection<*>, R> T.letNotEmpty(block: (T) -> R): R? {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }
    return if (this.isNotEmpty()) block(this) else null
}

// Adapted from https://stackoverflow.com/a/69196765
fun Arrangement.spacedByWithFooter(space: Dp) =
    object : Arrangement.Vertical {
        override val spacing = space

        override fun Density.arrange(
            totalSize: Int,
            sizes: IntArray,
            outPositions: IntArray,
        ) {
            if (sizes.isEmpty()) return
            val spacePx = space.roundToPx()

            var occupied = 0
            sizes.forEachIndexed { index, size ->
                if (index == sizes.lastIndex) {
                    outPositions[index] = totalSize - size
                } else {
                    outPositions[index] = min(occupied, totalSize - size)
                }
                val lastSpace = min(spacePx, totalSize - outPositions[index] - size)
                occupied = outPositions[index] + size + lastSpace
            }
        }
    }

/**
 * Tries to find the [Activity] for the given [Context]. Often used for [keepScreenOn].
 */
fun Context.findActivity(): Activity? {
    if (this is Activity) {
        return this
    }
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * Keep the screen on for an [Activity]. Often used with [findActivity].
 */
fun Activity.keepScreenOn(keep: Boolean) {
    Timber.v("Keep screen on: $keep")
    if (keep) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

/**
 * Selectively log errors from Coil image loading.
 *
 * If an HTTP error occurs, the entire stacktrace is not logged.
 */
fun logCoilError(
    url: String?,
    errorResult: ErrorResult,
) {
    if (errorResult.throwable is coil3.network.HttpException) {
        Timber.w("Error loading image: %s for %s", errorResult.throwable.localizedMessage, url)
    } else {
        Timber.e(errorResult.throwable, "Error loading image: %s", url)
    }
}

/**
 * Convenient way to [rememberSaveable] a [RowColumn]
 */
@Composable
fun rememberPosition(initialPosition: RowColumn = RowColumn(-1, -1)) =
    rememberSaveable(stateSaver = RowColumnSaver) {
        mutableStateOf(
            initialPosition,
        )
    }
