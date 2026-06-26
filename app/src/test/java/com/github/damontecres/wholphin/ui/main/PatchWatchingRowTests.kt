package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.services.PlaybackResult
import com.github.damontecres.wholphin.ui.util.EmptyStringProvider
import com.github.damontecres.wholphin.util.HomeRowLoadingState
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
 * Tests for [patchWatchingRow], which applies locally-known playback outcomes to a freshly fetched
 * "continue watching" row so it does not race the asynchronous server playback-stopped report.
 *
 * Scope: these are pure unit tests covering the decision logic only. The end-to-end wiring — the
 * flow collector that feeds outcomes in, recording the outcome on playback/toggle, and the card
 * actually rendering the result — is not covered here and can only be verified by an
 * instrumented/integration test (emulator + a fake data layer). The lookup is non-consuming, so the
 * same result may be applied repeatedly; that idempotence is what these cases exercise.
 */
class PatchWatchingRowTests {
    @Test
    fun `non-watching row is returned unchanged`() {
        val row = successRow(rowType = HomeRowConfig.RecentlyAdded(UUID.randomUUID()), items = listOf(item()))

        val result = patchWatchingRow(row) { error("lookup must not be called for a non-watching row") }

        assertSame(row, result)
    }

    @Test
    fun `item without a known result is kept as is`() {
        val original = item()
        val row = watchingRow(listOf(original))

        val result = patchWatchingRow(row) { null } as HomeRowLoadingState.Success

        assertEquals(1, result.items.size)
        assertSame(original, result.items[0])
    }

    @Test
    fun `null item is preserved`() {
        val row = watchingRow(listOf(null))

        val result = patchWatchingRow(row) { null } as HomeRowLoadingState.Success

        assertEquals(1, result.items.size)
        assertNull(result.items[0])
    }

    @Test
    fun `finished item is dropped from the row`() {
        val finished = item(runTimeTicks = 10_000L)
        val row = watchingRow(listOf(finished))
        val results = mapOf(finished.id to PlaybackResult(finished.id, 0L, played = true))

        val result = patchWatchingRow(row) { results[it] } as HomeRowLoadingState.Success

        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `mixed row drops finished, patches partial, keeps the rest in order`() {
        val partial = item(runTimeTicks = 10_000L)
        val finished = item(runTimeTicks = 10_000L)
        val untouched = item(runTimeTicks = 10_000L)
        val row = watchingRow(listOf(partial, finished, untouched))
        val results =
            mapOf(
                partial.id to PlaybackResult(partial.id, 5_000L, played = false),
                finished.id to PlaybackResult(finished.id, 0L, played = true),
            )

        val result = patchWatchingRow(row) { results[it] } as HomeRowLoadingState.Success

        assertEquals(2, result.items.size)
        assertEquals(partial.id, result.items[0]!!.id)
        assertEquals(
            50.0,
            result.items[0]!!
                .data.userData!!
                .playedPercentage!!,
            0.0001,
        )
        assertSame(untouched, result.items[1])
    }

    private fun watchingRow(items: List<BaseItem?>) = successRow(rowType = HomeRowConfig.ContinueWatching(), items = items)

    private fun successRow(
        rowType: HomeRowConfig?,
        items: List<BaseItem?>,
    ) = HomeRowLoadingState.Success(
        title = EmptyStringProvider,
        items = items,
        rowType = rowType,
    )

    private fun item(runTimeTicks: Long? = null): BaseItem {
        val id = UUID.randomUUID()
        return BaseItem(
            BaseItemDto(
                id = id,
                type = BaseItemKind.MOVIE,
                runTimeTicks = runTimeTicks,
                userData =
                    UserItemDataDto(
                        playedPercentage = null,
                        playbackPositionTicks = 0L,
                        playCount = 0,
                        isFavorite = false,
                        played = false,
                        key = "key",
                        itemId = id,
                    ),
            ),
        )
    }
}
