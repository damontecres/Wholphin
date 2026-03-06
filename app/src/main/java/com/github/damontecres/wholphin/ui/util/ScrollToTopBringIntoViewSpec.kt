package com.github.damontecres.wholphin.ui.util

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyColumn

/**
 * Overrides scrolling so that the item being scrolled to is at the top of the view offset by the provided pixels
 *
 * Note: the offset is necessary for anything that is focuseable, but has content before (eg a title) that needs to be displayed too
 *
 * Note: this applies to ALL scrollable composables within its scope, so a [LazyColumn] of [androidx.compose.foundation.lazy.LazyRow]s likely needs nested [LocalBringIntoViewSpec] overrides
 *
 * Example:
 * ```kotlin
 * val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
 * CompositionLocalProvider(LocalBringIntoViewSpec provides ScrollToTopBringIntoViewSpec(spaceAbovePx)) {
 *     LazyColumn{
 *         items(list){
 *             CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoViewSpec) {
 *                 // Content
 *             }
 *         }
 *     }
 * }
 * ```
 */
class ScrollToTopBringIntoViewSpec(
    val spaceAbovePx: Float = 100f,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
//        Timber.v(
//            "calculateScrollDistance: offset=%s, size=%s, containerSize=%s",
//            offset,
//            size,
//            containerSize,
//        )
        return offset - spaceAbovePx
    }
}
