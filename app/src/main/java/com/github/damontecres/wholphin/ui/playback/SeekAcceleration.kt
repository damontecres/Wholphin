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

    // Repeat cadence varies by device. Scaling down by 3 keeps ramp-up closer to multi-second holds.
    val scaledRepeatCount = repeatCount / 3
    if (scaledRepeatCount <= 0) return 1

    // Unknown/unset durations fall back to the shortest-content profile.
    val durationMinutes = if (durationMs > 0L) durationMs / 60_000L else 0L
    return when {
        durationMinutes < 30 -> {
            if (scaledRepeatCount < 30) 1 else 2
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
