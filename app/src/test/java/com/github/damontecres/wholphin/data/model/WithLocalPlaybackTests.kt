package com.github.damontecres.wholphin.data.model

import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [withLocalPlayback], the pure helper that updates an item's user data to reflect a
 * just-finished local playback. The resume bar is driven by playedPercentage, which the server has
 * not computed yet for a freshly-played item, so it must be derived from position/runtime here.
 *
 * Scope: these are pure unit tests covering the decision logic only. The end-to-end wiring — the
 * lifecycle resume trigger that re-applies this on return, recording the outcome on playback/toggle,
 * and the card actually rendering the bar — is not covered here and can only be verified by an
 * instrumented/integration test (emulator + a fake data layer).
 */
class WithLocalPlaybackTests {
    /**
     * @param rt runtime ticks of the item, @param pos new playback position, @param played whether
     * playback finished, @param existing the item's prior playedPercentage, @param expected the
     * resulting playedPercentage (null = none/cleared).
     */
    private data class Case(
        val rt: Long?,
        val pos: Long,
        val played: Boolean,
        val existing: Double?,
        val expected: Double?,
    )

    @Test
    fun `playback updates position, played and a derived percentage`() {
        listOf(
            Case(rt = 10_000L, pos = 2_500L, played = false, existing = null, expected = 25.0),
            Case(rt = 10_000L, pos = 5_000L, played = false, existing = 10.0, expected = 50.0),
            Case(rt = 10_000L, pos = 0L, played = true, existing = 80.0, expected = null),
            Case(rt = null, pos = 1_000L, played = false, existing = 42.0, expected = 42.0),
            Case(rt = 10_000L, pos = 0L, played = false, existing = 42.0, expected = 42.0),
        ).forEach { c ->
            val userData =
                item(runTimeTicks = c.rt, playedPercentage = c.existing)
                    .withLocalPlayback(c.pos, c.played)
                    .data.userData!!

            assertEquals("case=$c played", c.played, userData.played)
            assertEquals("case=$c position", c.pos, userData.playbackPositionTicks)
            if (c.expected == null) {
                assertNull("case=$c percentage", userData.playedPercentage)
            } else {
                assertEquals("case=$c percentage", c.expected, userData.playedPercentage!!, EPS)
            }
        }
    }

    @Test
    fun `item without user data is returned unchanged`() {
        val original = item(runTimeTicks = 10_000L, withUserData = false)
        val result = original.withLocalPlayback(1_000L, false)

        assertNull(result.data.userData)
        assertSame(original, result)
    }

    @Test
    fun `unrelated user data fields are preserved`() {
        val result =
            item(runTimeTicks = 10_000L, playedPercentage = null, isFavorite = true, playCount = 3)
                .withLocalPlayback(2_500L, false)

        val userData = result.data.userData!!
        assertTrue(userData.isFavorite)
        assertEquals(3, userData.playCount)
    }

    private fun item(
        runTimeTicks: Long? = null,
        positionTicks: Long = 0L,
        played: Boolean = false,
        playedPercentage: Double? = null,
        isFavorite: Boolean = false,
        playCount: Int = 0,
        withUserData: Boolean = true,
    ): BaseItem {
        val id = UUID.randomUUID()
        val userData =
            if (withUserData) {
                UserItemDataDto(
                    playedPercentage = playedPercentage,
                    playbackPositionTicks = positionTicks,
                    playCount = playCount,
                    isFavorite = isFavorite,
                    played = played,
                    key = "key",
                    itemId = id,
                )
            } else {
                null
            }
        return BaseItem(
            BaseItemDto(
                id = id,
                type = BaseItemKind.MOVIE,
                runTimeTicks = runTimeTicks,
                userData = userData,
            ),
        )
    }

    companion object {
        private const val EPS = 0.0001
    }
}
