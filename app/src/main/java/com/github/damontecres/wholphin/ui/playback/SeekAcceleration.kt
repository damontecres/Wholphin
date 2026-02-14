package com.github.damontecres.wholphin.ui.playback

/**
 * Shared seek acceleration profile for hold-to-seek behavior.
 * Keep this in sync anywhere directional key repeat seeking is handled.
 */
fun calculateSeekAccelerationMultiplier(
    repeatCount: Int,
    durationMs: Long,
): Int {
    if (repeatCount <= 0) return 1

    // Slow acceleration to roughly one-third the previous ramp rate.
    val scaledRepeatCount = repeatCount / 3
    if (scaledRepeatCount <= 0) return 1

    val durationMinutes = if (durationMs > 0L) durationMs / 60_000L else 0L
    return when {
        durationMinutes < 30 -> {
            when {
                scaledRepeatCount < 30 -> 1
                scaledRepeatCount < 60 -> 2
                else -> 2
            }
        }

        durationMinutes < 90 -> {
            when {
                scaledRepeatCount < 25 -> 1
                scaledRepeatCount < 50 -> 2
                scaledRepeatCount < 75 -> 3
                else -> 4
            }
        }

        durationMinutes < 150 -> {
            when {
                scaledRepeatCount < 20 -> 1
                scaledRepeatCount < 40 -> 2
                scaledRepeatCount < 60 -> 4
                else -> 6
            }
        }

        else -> {
            when {
                scaledRepeatCount < 20 -> 1
                scaledRepeatCount < 40 -> 3
                scaledRepeatCount < 60 -> 6
                else -> 10
            }
        }
    }
}
