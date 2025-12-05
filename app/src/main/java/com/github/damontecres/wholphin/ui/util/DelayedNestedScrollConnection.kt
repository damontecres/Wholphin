package com.github.damontecres.wholphin.ui.util

import androidx.annotation.FloatRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

class DelayedNestedScrollConnection(
    @param:FloatRange(0.0, 1.0)
    private val xDelay: Float = 0f,
    @param:FloatRange(0.0, 1.0)
    private val yDelay: Float = .6f,
) : NestedScrollConnection {
    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset =
        if (source == NestedScrollSource.UserInput) {
            Offset(x = available.x * xDelay, y = available.y * yDelay)
        } else {
            Offset.Zero
        }
}

/**
 * Add a delay to the scroll speed via user interaction
 *
 * In some cases, slowing down scrolling makes it feel smoother and less jerky visually
 *
 * ```kotlin
 * val scrollState = rememberScrollState()
 * val scrollConnection = rememberDelayedNestedScroll(.5f, .5f)
 * Column(
 *   modifier =
 *       Modifier
 *           .nestedScroll(scrollConnection)
 *           .verticalScroll(scrollState)
 * ){ content() }
 * ```
 *
 * @param xDelay how much to delay left-right scrolling
 * @param yDelay how much to delay up-down scrolling
 *
 * @see rememberDelayedNestedScroll
 * @see androidx.compose.ui.input.nestedscroll.nestedScroll
 */
@Composable
fun rememberDelayedNestedScroll(
    @FloatRange(0.0, 1.0)
    xDelay: Float = 0f,
    @FloatRange(0.0, 1.0)
    yDelay: Float = .6f,
) = remember(xDelay, yDelay) { DelayedNestedScrollConnection(xDelay, yDelay) }
