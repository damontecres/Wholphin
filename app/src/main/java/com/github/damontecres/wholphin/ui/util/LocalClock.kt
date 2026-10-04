package com.github.damontecres.wholphin.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.github.damontecres.wholphin.ui.formatTime
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

val LocalClock = compositionLocalOf<Clock> { Clock() }

/**
 * Represents the current time
 */
data class Clock(
    /**
     * The current [LocalDateTime]
     */
    val now: MutableState<LocalDateTime> = mutableStateOf(LocalDateTime.now()),
    /**
     * The current time formatted as a string; populated by [ProvideLocalClock] via [formatTime].
     */
    val timeString: MutableState<String> = mutableStateOf(""),
)

@Composable
fun ProvideLocalClock(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val clock = remember { Clock() }
    LaunchedEffect(context) {
        withContext(WholphinDispatchers.Default) {
            while (isActive) {
                val now = LocalDateTime.now()
                val time = formatTime(context, now)
                clock.now.value = now
                clock.timeString.value = time
                delay(2_000)
            }
        }
    }
    CompositionLocalProvider(LocalClock provides clock, content)
}
