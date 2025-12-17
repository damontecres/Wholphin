package com.github.damontecres.wholphin.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.damontecres.wholphin.ui.TimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDateTime

val LocalClock = compositionLocalOf<Clock> { throw IllegalStateException() }

/**
 * Represents the current time
 */
data class Clock(
    /**
     * The current [LocalDateTime]
     */
    val now: LocalDateTime,
    /**
     * The current time formatted as a string with [TimeFormatter]
     */
    val timeString: String,
)

@Composable
fun ProvideLocalClock(content: @Composable () -> Unit) {
    var clock by remember { mutableStateOf(LocalDateTime.now().let { Clock(it, TimeFormatter.format(it)) }) }
    LaunchedEffect(Unit) {
        while (isActive) {
            clock = LocalDateTime.now().let { Clock(it, TimeFormatter.format(it)) }
            delay(1_000)
        }
    }
    CompositionLocalProvider(LocalClock provides clock, content)
}
