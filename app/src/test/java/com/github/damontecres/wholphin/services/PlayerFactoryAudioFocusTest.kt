package com.github.damontecres.wholphin.services

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAudioManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerFactoryAudioFocusTest {
    private lateinit var context: Context
    private lateinit var player: ExoPlayer

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        player =
            PlayerFactory(context, OkHttpClient()).createAudioPlayer(disableAudioOffload = true)
    }

    @After
    fun tearDown() {
        shadowOf(Looper.getMainLooper()).idle()
        player.release()
    }

    @Test
    fun audioPlayerUsesMusicContentType() {
        assertEquals(C.AUDIO_CONTENT_TYPE_MUSIC, player.audioAttributes.contentType)
    }

    @Test
    fun audioPlayerPausesWhenAnotherAppTakesAudioFocus() {
        player.setMediaSource(SilenceMediaSource(SILENCE_DURATION_US))
        player.prepare()
        player.play()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focusRequest = waitForAudioFocusRequest(audioManager)
        assertEquals(AudioManager.AUDIOFOCUS_GAIN, focusRequest.durationHint)
        assertTrue(player.playWhenReady)

        focusRequest.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        waitUntil("Player should pause after audio focus loss") { !player.playWhenReady }
        assertFalse(player.playWhenReady)
    }

    private fun waitForAudioFocusRequest(audioManager: AudioManager): ShadowAudioManager.AudioFocusRequest {
        waitUntil("Audio player should request audio focus when playback starts") {
            shadowOf(audioManager).lastAudioFocusRequest != null
        }
        return shadowOf(audioManager).lastAudioFocusRequest
    }

    private fun waitUntil(
        message: String,
        attempts: Int = 100,
        condition: () -> Boolean,
    ) {
        val mainLooper = shadowOf(Looper.getMainLooper())
        repeat(attempts) {
            if (condition()) return
            mainLooper.idle()
            Thread.sleep(20)
        }
        assertTrue(message, condition())
    }

    companion object {
        private const val SILENCE_DURATION_US = 30_000_000L
    }
}
