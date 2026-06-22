package com.github.damontecres.wholphin.services

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import java.util.UUID

/**
 * Tests for [PlaybackResultService], the flow that publishes locally-known playback outcomes for
 * ViewModels to collect.
 *
 * Scope: the flow contract only (a recorded result reaches collectors; replay = 1 reaches a late
 * subscriber). How ViewModels apply a collected result to their state is covered by their own tests
 * (e.g. PatchWatchingRowTests) and by instrumented testing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackResultServiceTest {
    private val service = PlaybackResultService()

    @Test
    fun `recorded result reaches a collector`() =
        runTest {
            val itemId = UUID.randomUUID()
            val received = mutableListOf<PlaybackResult>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                service.playbackResultFlow.toList(received)
            }

            service.record(itemId, positionTicks = 1_234L, played = false)

            Assert.assertEquals(1, received.size)
            Assert.assertEquals(PlaybackResult(itemId, 1_234L, played = false), received[0])
        }

    @Test
    fun `late subscriber receives the most recent result`() =
        runTest {
            val older = UUID.randomUUID()
            val newer = UUID.randomUUID()
            service.record(older, positionTicks = 10L, played = false)
            service.record(newer, positionTicks = 0L, played = true)

            val received = mutableListOf<PlaybackResult>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                service.playbackResultFlow.toList(received)
            }

            Assert.assertEquals(1, received.size)
            Assert.assertEquals(PlaybackResult(newer, 0L, played = true), received[0])
        }
}
