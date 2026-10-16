@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.github.damontecres.wholphin.ui.playback

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.media3.common.Player
import com.github.damontecres.wholphin.preferences.AppPreferencesSerializer
import com.github.damontecres.wholphin.preferences.DpadSeekMode
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.preferences.updatePlaybackPreferences
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PlaybackReplacementTest {
    @get:Rule val compose = createComposeRule()
    private fun player(position: Long) = mockk<Player>(relaxed = true).also {
        every { it.playbackState } returns Player.STATE_READY
        every { it.currentPosition } returns position
        every { it.duration } returns 300_000L
        every { it.availableCommands } returns Player.Commands.Builder().addAll(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM).build()
    }
    @Test
    fun `hidden controls seek and stop use replacement player with unchanged preferences`() {
        val old = player(100_000L)
        val replacement = player(213_543L)
        val current = mutableStateOf(PlayerInstance(old, PlayerBackend.EXO_PLAYER, null))
        val viewModel = mockk<PlaybackViewModel>(relaxed = true)
        every { viewModel.state } returns MutableStateFlow(PlaybackState())
        every { viewModel.subtitleSearchState } returns MutableStateFlow(SubtitleSearchState())
        every { viewModel.currentUserDto } returns MutableStateFlow(null)
        every { viewModel.reduceLoudSounds } returns MutableStateFlow(false)
        every { viewModel.controllerViewState } returns ControllerViewState(5000, true)
        val preferences = UserPreferences(AppPreferencesSerializer().defaultValue.updatePlaybackPreferences {
            skipForwardMs = 10_000L
            skipBackMs = 5_000L
            dpadSeekMode = DpadSeekMode.SEEKBAR_MINIMAL
        }, null)
        val destination = Destination.Playback(UUID.randomUUID(), 0L)
        val images = mockk<ImageUrlService>(relaxed = true)
        compose.setContent {
            CompositionLocalProvider(LocalImageUrlService provides images) {
                WholphinTheme { PlaybackPageContent(current.value, preferences, destination, viewModel = viewModel) }
            }
        }
        compose.onRoot().performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        compose.runOnIdle {
            verify { old.seekTo(110_000L) }
            current.value = PlayerInstance(replacement, PlayerBackend.EXO_PLAYER, null)
            clearMocks(old, replacement, answers = false)
        }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        compose.onRoot().performKeyInput { keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft) }
        compose.onRoot().performKeyInput { keyDown(Key.MediaStop); keyUp(Key.MediaStop) }
        compose.runOnIdle {
            verify { replacement.seekTo(223_543L) }
            verify { replacement.setPlaybackSpeed(1f) }
            verify { replacement.seekTo(208_543L) }
            verify { replacement.stop() }
            verify(exactly = 0) { old.seekTo(any<Long>()) }
            verify(exactly = 0) { old.stop() }
        }
    }
}
