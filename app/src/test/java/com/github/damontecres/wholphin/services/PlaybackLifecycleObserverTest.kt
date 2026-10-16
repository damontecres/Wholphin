package com.github.damontecres.wholphin.services

import androidx.lifecycle.LifecycleOwner
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class PlaybackLifecycleObserverTest {
    @Test
    fun `lifecycle ignores released player and uses current replacement for pause and resume`() {
        val factory = mockk<PlayerFactory>()
        val old = mockk<ExoPlayer>(relaxed = true)
        val replacement = mockk<ExoPlayer>(relaxed = true)
        val owner = mockk<LifecycleOwner>()
        every { old.isReleased } returns true
        every { replacement.isReleased } returns false
        every { replacement.isPlaying } returns true
        every { factory.currentPlayer } returns old
        val observer = PlaybackLifecycleObserver(mockk(relaxed = true), factory, mockk(relaxed = true))
        observer.onPause(owner)
        verify(exactly = 0) { old.isPlaying }
        verify(exactly = 0) { old.pause() }
        every { factory.currentPlayer } returns replacement
        observer.onPause(owner)
        observer.onResume(owner)
        verify { replacement.pause() }
        verify { replacement.play() }
        verify(exactly = 0) { old.play() }
        clearMocks(replacement, answers = false)
        every { factory.currentPlayer } returns old
        observer.onPause(owner)
        every { factory.currentPlayer } returns replacement
        observer.onResume(owner)
        verify(exactly = 0) { replacement.play() }
    }
}
