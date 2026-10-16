package com.github.damontecres.wholphin.util

import androidx.media3.common.Player
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.operations.PlayStateApi
import org.jellyfin.sdk.model.api.PlayMethod
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class TrackActivityPlaybackListenerTest {
    @Test
    fun `release cancels pending start and ignores late callbacks from old player`() = runTest {
        WholphinDispatchers.configure(StandardTestDispatcher(testScheduler))
        try {
            val api = mockk<ApiClient>()
            val playState = mockk<PlayStateApi>(relaxed = true)
            every { api.playStateApi } returns playState
            val player = mockk<Player>(relaxed = true)
            every { player.currentPosition } returns 213_543L
            val finishStart = CompletableDeferred<Unit>()
            var startCancelled = false
            coEvery { playState.reportPlaybackStart(any()) } coAnswers {
                try { finishStart.await(); mockk(relaxed = true) }
                finally { startCancelled = !currentCoroutineContext().isActive }
            }
            val state = PlaybackItemState(UUID.randomUUID(), PlayMethod.DIRECT_PLAY, playSessionId = "old-session")
            val listener = TrackActivityPlaybackListener(api, player) { state }
            listener.onIsPlayingChanged(true)
            testScheduler.runCurrent()
            listener.release()
            testScheduler.runCurrent()
            assertTrue("released listener must cancel its in-flight start work", startCancelled)
            clearMocks(player, answers = false)
            listener.onIsPlayingChanged(true)
            listener.onPlaybackStateChanged(Player.STATE_ENDED)
            finishStart.complete(Unit)
            testScheduler.runCurrent()
            verify(exactly = 0) { player.currentPosition }
            verify(exactly = 0) { player.duration }
            coVerify(exactly = 1) { playState.reportPlaybackStopped(match { it.positionTicks == 2_135_430_000L && it.playSessionId == "old-session" }) }
            coVerify(exactly = 1) { playState.reportPlaybackStart(any()) }
            coVerify(exactly = 0) { playState.reportPlaybackProgress(any()) }
        } finally { WholphinDispatchers.reset() }
    }
}
