package com.github.damontecres.wholphin.test

import android.app.Activity
import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import com.github.damontecres.wholphin.ui.components.VoiceInputManager
import com.github.damontecres.wholphin.ui.components.VoiceInputState
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class TestVoiceInputManagerAutoFocus {
    private lateinit var activity: Activity
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var manager: VoiceInputManager
    private lateinit var audioManager: AudioManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var listenerSlot: CapturingSlot<RecognitionListener>

    // We need to capture the OnAudioFocusChangeListener passed to AudioFocusRequest
    private val focusRequestSlot = slot<AudioFocusRequest>()

    private val capturedListener: RecognitionListener
        get() = listenerSlot.captured

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setup() {
        activity = mockk(relaxed = true)
        audioManager = mockk(relaxed = true)
        connectivityManager = mockk(relaxed = true)

        // Mock network availability
        val mockNetwork = mockk<Network>()
        val mockCapabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns mockNetwork
        every { connectivityManager.getNetworkCapabilities(mockNetwork) } returns mockCapabilities
        every { mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true

        // Capture the AudioFocusRequest built by the specific line in VoiceInputManager
        every { audioManager.requestAudioFocus(capture(focusRequestSlot)) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        every { activity.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        every { activity.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager

        speechRecognizer = mockk(relaxed = true)
        listenerSlot = slot()
        every { speechRecognizer.setRecognitionListener(capture(listenerSlot)) } just Runs

        mockkStatic(SpeechRecognizer::class)
        every { SpeechRecognizer.createSpeechRecognizer(activity) } returns speechRecognizer
        every { SpeechRecognizer.isRecognitionAvailable(activity) } returns true

        manager = VoiceInputManager(activity)
    }

    @After
    fun teardown() {
        unmockkStatic(SpeechRecognizer::class)
    }

    @Test
    fun `transient audio focus loss is ignored`() {
        // Start listening
        manager.startListening()
        idleMainLooper()
        capturedListener.onReadyForSpeech(null)
        idleMainLooper()
        assertEquals(VoiceInputState.Listening, manager.state.value)

        // Verify requestAudioFocus was called
        assertTrue(focusRequestSlot.isCaptured)

        // Get listener via reflection since AudioFocusRequest doesn't expose it
        val field = VoiceInputManager::class.java.getDeclaredField("audioFocusListener")
        field.isAccessible = true
        val listener = field.get(manager) as AudioManager.OnAudioFocusChangeListener

        // Invoke onAudioFocusChange directly on the listener
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)

        idleMainLooper()

        // Assert state is STILL Listening (Logic correctly ignores it)
        assertEquals(VoiceInputState.Listening, manager.state.value)
    }

    @Test
    fun `permanent audio focus loss stops listening`() {
        // Start listening
        manager.startListening()
        idleMainLooper()
        capturedListener.onReadyForSpeech(null)
        idleMainLooper()

        // Get listener via reflection since AudioFocusRequest doesn't expose it
        val field = VoiceInputManager::class.java.getDeclaredField("audioFocusListener")
        field.isAccessible = true
        val listener = field.get(manager) as AudioManager.OnAudioFocusChangeListener

        // Invoke onAudioFocusChange directly on the listener
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)

        idleMainLooper()

        // Assert state is NOW Idle (Logic correctly stops)
        assertEquals(VoiceInputState.Idle, manager.state.value)
    }
}
