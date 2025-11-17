package com.github.damontecres.wholphin.ui

import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.transition.CrossfadeTransition
import coil3.transition.Transition
import coil3.transition.TransitionTarget
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * Similar to [coil3.transition.CrossfadeTransition.Factory] but always cross fades even if loading from memory
 */
class CrossFadeFactory(
    val duration: Duration,
) : Transition.Factory {
    override fun create(
        target: TransitionTarget,
        result: ImageResult,
    ): Transition =
        if (result is SuccessResult) {
            CrossfadeTransition(target, result, duration.toInt(DurationUnit.MILLISECONDS), false)
        } else {
            Transition.Factory.NONE.create(target, result)
        }
}
