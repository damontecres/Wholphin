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

    val durationMinutes = if (durationMs > 0L) durationMs / 60_000L else 0L
    return when {
        durationMinutes < 30 -> {
            when {
                repeatCount < 30 -> 1
                repeatCount < 60 -> 2
                else -> 2
            }
        }

        durationMinutes < 90 -> {
            when {
                repeatCount < 25 -> 1
                repeatCount < 50 -> 2
                repeatCount < 75 -> 3
                else -> 4
            }
        }

        durationMinutes < 150 -> {
            when {
                repeatCount < 20 -> 1
                repeatCount < 40 -> 2
                repeatCount < 60 -> 4
                else -> 6
            }
        }

        else -> {
            when {
                repeatCount < 20 -> 1
                repeatCount < 40 -> 3
                repeatCount < 60 -> 6
                else -> 10
            }
        }
    }
}
