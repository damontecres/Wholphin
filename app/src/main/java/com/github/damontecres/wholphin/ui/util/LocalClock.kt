package com.github.damontecres.wholphin.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.github.damontecres.wholphin.ui.TimeFormatter
import kotlinx.coroutines.Dispatchers
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
     * The current time formatted as a string with [TimeFormatter]
     */
    val timeString: MutableState<String> = mutableStateOf(TimeFormatter.format(now.value)),
)

@Composable
fun ProvideLocalClock(content: @Composable () -> Unit) {
    val clock = remember { Clock() }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            while (isActive) {
                val now = LocalDateTime.now()
                val time = TimeFormatter.format(now)
                clock.now.value = now
                clock.timeString.value = time
                delay(2_000)
            }
        }
    }
    CompositionLocalProvider(LocalClock provides clock, content)
}
