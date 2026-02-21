package com.github.damontecres.wholphin.ui.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekAccelerationTest {
    @Test
    fun returnsOneWhenNotRepeating() {
        assertEquals(1, calculateSeekAccelerationMultiplier(repeatCount = 0, durationMs = 30_000L))
    }

    @Test
    fun unknownDurationUsesShortestProfile() {
        assertEquals(1, calculateSeekAccelerationMultiplier(repeatCount = 89, durationMs = 0L))
        assertEquals(2, calculateSeekAccelerationMultiplier(repeatCount = 300, durationMs = -1L))
    }

    @Test
    fun shortContentHasTwoTiers() {
        val shortDurationMs = 20L * 60_000L

        assertEquals(
            1,
            calculateSeekAccelerationMultiplier(repeatCount = 89, durationMs = shortDurationMs),
        )
        assertEquals(
            2,
            calculateSeekAccelerationMultiplier(repeatCount = 90, durationMs = shortDurationMs),
        )
    }

    @Test
    fun mediumContentEscalatesAcrossAllTiers() {
        val mediumDurationMs = 60L * 60_000L

        assertEquals(
            1,
            calculateSeekAccelerationMultiplier(repeatCount = 74, durationMs = mediumDurationMs),
        )
        assertEquals(
            2,
            calculateSeekAccelerationMultiplier(repeatCount = 75, durationMs = mediumDurationMs),
        )
        assertEquals(
            3,
            calculateSeekAccelerationMultiplier(repeatCount = 150, durationMs = mediumDurationMs),
        )
        assertEquals(
            4,
            calculateSeekAccelerationMultiplier(repeatCount = 225, durationMs = mediumDurationMs),
        )
    }
}
