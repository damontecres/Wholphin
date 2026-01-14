package com.github.damontecres.wholphin.test

import android.app.Activity
import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
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
    
    // We need to capture the OnAudioFocusChangeListener passed to AudioFocusRequest
    private val focusRequestSlot = slot<AudioFocusRequest>()

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setup() {
        activity = mockk(relaxed = true)
        audioManager = mockk(relaxed = true)
        
        // Capture the AudioFocusRequest built by the specific line in VoiceInputManager
        every { audioManager.requestAudioFocus(capture(focusRequestSlot)) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        every { activity.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        every { activity.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockk(relaxed = true)

        speechRecognizer = mockk(relaxed = true)
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
        assertEquals(VoiceInputState.Listening, manager.state.value)
        
        // Verify requestAudioFocus was called and we captured the request
        assertTrue(focusRequestSlot.isCaptured)
        val request = focusRequestSlot.captured
        val listener = request.onAudioFocusChangeListener
        
        // Simulate Transient Loss (System Speech Recognizer starting up)
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
        
        val request = focusRequestSlot.captured
        val listener = request.onAudioFocusChangeListener
        
        // Simulate Permanent Loss (Another app playing music)
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        idleMainLooper()
        
        // Assert state is NOW Idle (Logic correctly stops)
        assertEquals(VoiceInputState.Idle, manager.state.value)
    }
}
