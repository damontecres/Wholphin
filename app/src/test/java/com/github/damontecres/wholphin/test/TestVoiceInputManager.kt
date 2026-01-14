package com.github.damontecres.wholphin.test

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.VoiceInputManager
import com.github.damontecres.wholphin.ui.components.VoiceInputState
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit tests for [VoiceInputManager] state machine logic.
 *
 * Uses Robolectric to provide Android framework classes (Intent, Bundle)
 * and Mockk to mock [SpeechRecognizer] and simulate recognition callbacks
 * without requiring a real microphone or emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class TestVoiceInputManager {
    private lateinit var activity: Activity
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var listenerSlot: CapturingSlot<RecognitionListener>
    private lateinit var manager: VoiceInputManager
    private lateinit var audioManager: AudioManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var network: Network
    private lateinit var networkCapabilities: NetworkCapabilities

    private val capturedListener: RecognitionListener
        get() = listenerSlot.captured

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setup() {
        // Mock Activity
        activity = mockk(relaxed = true)

        // Mock AudioManager
        audioManager = mockk(relaxed = true)
        every { audioManager.requestAudioFocus(any<AudioFocusRequest>()) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        every { activity.getSystemService(Context.AUDIO_SERVICE) } returns audioManager

        // Mock ConnectivityManager with network available by default
        connectivityManager = mockk(relaxed = true)
        network = mockk()
        networkCapabilities = mockk()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { activity.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager

        // Mock SpeechRecognizer instance
        speechRecognizer = mockk(relaxed = true)
        listenerSlot = slot()

        // Capture the RecognitionListener when setRecognitionListener is called
        every { speechRecognizer.setRecognitionListener(capture(listenerSlot)) } just Runs

        // Mock static factory method
        mockkStatic(SpeechRecognizer::class)
        every { SpeechRecognizer.createSpeechRecognizer(activity) } returns speechRecognizer
        every { SpeechRecognizer.isRecognitionAvailable(activity) } returns true

        // Create the manager under test
        manager = VoiceInputManager(activity)
    }

    @After
    fun teardown() {
        unmockkStatic(SpeechRecognizer::class)
    }

    // ========== Test Case 1: Initial State ==========

    @Test
    fun `initial state is Idle`() {
        assertEquals(VoiceInputState.Idle, manager.state.value)
    }

    @Test
    fun `initial soundLevel is zero`() {
        assertEquals(0f, manager.soundLevel.value)
    }

    @Test
    fun `initial partialResult is empty`() {
        assertEquals("", manager.partialResult.value)
    }

    // ========== Test Case 2: Start Listening ==========

    @Test
    fun `startListening transitions state to Listening`() {
        manager.startListening()
        idleMainLooper()

        assertEquals(VoiceInputState.Listening, manager.state.value)
    }

    @Test
    fun `startListening creates SpeechRecognizer`() {
        manager.startListening()
        idleMainLooper()

        verify { SpeechRecognizer.createSpeechRecognizer(activity) }
    }

    @Test
    fun `startListening sets recognition listener`() {
        manager.startListening()
        idleMainLooper()

        verify { speechRecognizer.setRecognitionListener(any()) }
        assertTrue(listenerSlot.isCaptured)
    }

    @Test
    fun `startListening calls recognizer startListening`() {
        manager.startListening()
        idleMainLooper()

        verify { speechRecognizer.startListening(any<Intent>()) }
    }

    @Test
    fun `startListening resets partialResult`() {
        manager.startListening()
        idleMainLooper()
        capturedListener.onPartialResults(createResultsBundle("partial"))
        idleMainLooper()
        manager.stopListening()
        idleMainLooper()

        manager.startListening()
        idleMainLooper()

        assertEquals("", manager.partialResult.value)
    }

    @Test
    fun `startListening is ignored when already listening`() {
        manager.startListening()
        idleMainLooper()
        manager.startListening() // Should be ignored
        idleMainLooper()

        // Only one recognizer should be created
        verify(exactly = 1) { SpeechRecognizer.createSpeechRecognizer(activity) }
    }

    // ========== Test Case 3: Permission Denied ==========

    @Test
    fun `onPermissionDenied transitions to Error state`() {
        manager.onPermissionDenied()
        idleMainLooper()

        assertTrue(manager.state.value is VoiceInputState.Error)
    }

    @Test
    fun `onPermissionDenied sets correct error resource`() {
        manager.onPermissionDenied()
        idleMainLooper()

        val errorState = manager.state.value as VoiceInputState.Error
        assertEquals(R.string.voice_error_permissions, errorState.messageResId)
    }

    // ========== Test Case 4: Result Success ==========

    @Test
    fun `onResults transitions to Result state with correct text`() {
        manager.startListening()
        idleMainLooper()

        capturedListener.onResults(createResultsBundle("hello world"))
        idleMainLooper()

        assertTrue(manager.state.value is VoiceInputState.Result)
        assertEquals("hello world", (manager.state.value as VoiceInputState.Result).text)
    }

    @Test
    fun `onResults resets soundLevel to zero`() {
        manager.startListening()
        idleMainLooper()
        capturedListener.onRmsChanged(5f)
        idleMainLooper()

        capturedListener.onResults(createResultsBundle("test"))
        idleMainLooper()

        assertEquals(0f, manager.soundLevel.value)
    }

    @Test
    fun `onResults with empty text transitions to Error state`() {
        manager.startListening()
        idleMainLooper()

        capturedListener.onResults(createResultsBundle(""))
        idleMainLooper()

        assertTrue(manager.state.value is VoiceInputState.Error)
        assertEquals(R.string.voice_error_no_match, (manager.state.value as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onResults with null bundle transitions to Error state`() {
        manager.startListening()
        idleMainLooper()

        capturedListener.onResults(null)
        idleMainLooper()

        assertTrue(manager.state.value is VoiceInputState.Error)
        assertEquals(R.string.voice_error_no_match, (manager.state.value as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onResults with blank text transitions to Error state`() {
        manager.startListening()
        idleMainLooper()

        capturedListener.onResults(createResultsBundle("   "))
        idleMainLooper()

        assertTrue(manager.state.value is VoiceInputState.Error)
    }

    // ========== Test Case 5: Error Handling ==========

    @Test
    fun `onError transitions to Error state`() {
        manager.startListening()
        idleMainLooper()

        capturedListener.onError(SpeechRecognizer.ERROR_NETWORK)
        idleMainLooper()

        assertTrue(manager.state.value is VoiceInputState.Error)
    }

    @Test
    fun `onError maps ERROR_NETWORK to correct resource`() {
        manager.startListening()
        idleMainLooper()

        capturedListener.onError(SpeechRecognizer.ERROR_NETWORK)
        idleMainLooper()

        assertEquals(R.string.voice_error_network, (manager.state.value as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError maps ERROR_NO_MATCH to correct resource`() {
        manager.startListening()
        idleMainLooper()

        capturedListener.onError(SpeechRecognizer.ERROR_NO_MATCH)
        idleMainLooper()

        assertEquals(R.string.voice_error_no_match, (manager.state.value as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError maps ERROR_AUDIO to correct resource`() {
        manager.startListening()
        idleMainLooper()

        capturedListener.onError(SpeechRecognizer.ERROR_AUDIO)
        idleMainLooper()

        assertEquals(R.string.voice_error_audio, (manager.state.value as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError maps ERROR_SERVER to correct resource`() {
        manager.startListening()
        idleMainLooper()

        capturedListener.onError(SpeechRecognizer.ERROR_SERVER)
        idleMainLooper()

        assertEquals(R.string.voice_error_server, (manager.state.value as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError maps ERROR_CLIENT to correct resource`() {
        manager.startListening()
        idleMainLooper()

        capturedListener.onError(SpeechRecognizer.ERROR_CLIENT)
        idleMainLooper()

        assertEquals(R.string.voice_error_client, (manager.state.value as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError maps ERROR_SPEECH_TIMEOUT to correct resource`() {
        manager.startListening()
        idleMainLooper()

        capturedListener.onError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        idleMainLooper()

        assertEquals(R.string.voice_error_speech_timeout, (manager.state.value as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError maps ERROR_NETWORK_TIMEOUT to correct resource`() {
        manager.startListening()
        idleMainLooper()

        capturedListener.onError(SpeechRecognizer.ERROR_NETWORK_TIMEOUT)
        idleMainLooper()

        assertEquals(R.string.voice_error_network_timeout, (manager.state.value as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError maps ERROR_RECOGNIZER_BUSY to correct resource after retry exhausted`() {
        manager.startListening()
        idleMainLooper()

        // First BUSY triggers auto-retry
        capturedListener.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(350))

        // Second BUSY exhausts retry count and shows error
        capturedListener.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        idleMainLooper()

        assertEquals(R.string.voice_error_busy, (manager.state.value as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError maps ERROR_INSUFFICIENT_PERMISSIONS to correct resource`() {
        manager.startListening()
        idleMainLooper()

        capturedListener.onError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        idleMainLooper()

        assertEquals(R.string.voice_error_permissions, (manager.state.value as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError maps unknown error to unknown resource`() {
        manager.startListening()
        idleMainLooper()

        capturedListener.onError(999) // Unknown error code
        idleMainLooper()

        assertEquals(R.string.voice_error_unknown, (manager.state.value as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError resets soundLevel to zero`() {
        manager.startListening()
        idleMainLooper()
        capturedListener.onRmsChanged(5f)
        idleMainLooper()

        capturedListener.onError(SpeechRecognizer.ERROR_NETWORK)
        idleMainLooper()

        assertEquals(0f, manager.soundLevel.value)
    }

    // ========== Test Case 6: Cleanup/Lifecycle ==========

    @Test
    fun `cleanup resets state to Idle`() {
        manager.startListening()
        idleMainLooper()

        manager.close()
        idleMainLooper()

        assertEquals(VoiceInputState.Idle, manager.state.value)
    }

    @Test
    fun `cleanup calls cancel on recognizer`() {
        manager.startListening()
        idleMainLooper()

        manager.close()

        verify { speechRecognizer.cancel() }
    }

    @Test
    fun `cleanup calls destroy on recognizer`() {
        manager.startListening()
        idleMainLooper()

        manager.close()

        verify { speechRecognizer.destroy() }
    }

    @Test
    fun `cleanup resets soundLevel to zero`() {
        manager.startListening()
        idleMainLooper()
        capturedListener.onRmsChanged(5f)
        idleMainLooper()

        manager.close()
        idleMainLooper()

        assertEquals(0f, manager.soundLevel.value)
    }

    @Test
    fun `cleanup resets partialResult to empty`() {
        manager.startListening()
        idleMainLooper()
        capturedListener.onPartialResults(createResultsBundle("partial"))
        idleMainLooper()

        manager.close()
        idleMainLooper()

        assertEquals("", manager.partialResult.value)
    }

    @Test
    fun `stopListening triggers cleanup`() {
        manager.startListening()
        idleMainLooper()

        manager.stopListening()
        idleMainLooper()

        assertEquals(VoiceInputState.Idle, manager.state.value)
        verify { speechRecognizer.cancel() }
        verify { speechRecognizer.destroy() }
    }

    // ========== Additional Coverage ==========

    @Test
    fun `onEndOfSpeech transitions to Processing state`() {
        manager.startListening()
        idleMainLooper()

        capturedListener.onEndOfSpeech()
        idleMainLooper()

        assertEquals(VoiceInputState.Processing, manager.state.value)
    }

    @Test
    fun `onRmsChanged updates soundLevel with normalized value`() {
        manager.startListening()
        idleMainLooper()

        // RMS normalization: (rmsdB - (-2)) / (10 - (-2)) clamped to [0, 1]
        // For rmsdB = 4: (4 - (-2)) / 12 = 6/12 = 0.5
        capturedListener.onRmsChanged(4f)
        idleMainLooper()

        assertEquals(0.5f, manager.soundLevel.value, 0.01f)
    }

    @Test
    fun `onRmsChanged clamps high values to 1`() {
        manager.startListening()
        idleMainLooper()

        capturedListener.onRmsChanged(20f) // Above max
        idleMainLooper()

        assertEquals(1f, manager.soundLevel.value)
    }

    @Test
    fun `onRmsChanged clamps low values to 0`() {
        manager.startListening()
        idleMainLooper()

        capturedListener.onRmsChanged(-10f) // Below min
        idleMainLooper()

        assertEquals(0f, manager.soundLevel.value)
    }

    @Test
    fun `onPartialResults updates partialResult`() {
        manager.startListening()
        idleMainLooper()

        capturedListener.onPartialResults(createResultsBundle("hello"))
        idleMainLooper()

        assertEquals("hello", manager.partialResult.value)
    }

    @Test
    fun `onPartialResults ignores blank text`() {
        manager.startListening()
        idleMainLooper()
        capturedListener.onPartialResults(createResultsBundle("first"))
        idleMainLooper()

        capturedListener.onPartialResults(createResultsBundle("  "))
        idleMainLooper()

        assertEquals("first", manager.partialResult.value)
    }

    @Test
    fun `acknowledge resets state to Idle`() {
        manager.startListening()
        idleMainLooper()
        capturedListener.onResults(createResultsBundle("test"))
        idleMainLooper()

        manager.acknowledge()
        idleMainLooper()

        assertEquals(VoiceInputState.Idle, manager.state.value)
    }

    @Test
    fun `acknowledge works from Error state`() {
        manager.onPermissionDenied()
        idleMainLooper()

        manager.acknowledge()
        idleMainLooper()

        assertEquals(VoiceInputState.Idle, manager.state.value)
    }

    @Test
    fun `onPermissionGranted calls startListening`() {
        manager.onPermissionGranted()
        idleMainLooper()

        assertEquals(VoiceInputState.Listening, manager.state.value)
        verify { SpeechRecognizer.createSpeechRecognizer(activity) }
    }

    @Test
    fun `callbacks from previous recognizer are ignored after cleanup`() {
        // Create two different mock recognizers to simulate real behavior
        val firstRecognizer = mockk<SpeechRecognizer>(relaxed = true)
        val secondRecognizer = mockk<SpeechRecognizer>(relaxed = true)
        val firstListenerSlot = slot<RecognitionListener>()
        val secondListenerSlot = slot<RecognitionListener>()

        every { firstRecognizer.setRecognitionListener(capture(firstListenerSlot)) } just Runs
        every { secondRecognizer.setRecognitionListener(capture(secondListenerSlot)) } just Runs

        // Return different recognizers for each call
        every { SpeechRecognizer.createSpeechRecognizer(activity) } returnsMany listOf(firstRecognizer, secondRecognizer)

        manager.startListening()
        idleMainLooper()
        val firstListener = firstListenerSlot.captured

        manager.close()
        idleMainLooper()
        manager.startListening()
        idleMainLooper()

        // Simulate callback from the old (zombie) recognizer
        firstListener.onResults(createResultsBundle("zombie result"))
        idleMainLooper()

        // State should remain Listening (from the second startListening), not Result
        assertEquals(VoiceInputState.Listening, manager.state.value)
    }

    @Test
    fun `isAvailable returns mocked value`() {
        assertTrue(manager.isAvailable)
    }

    // ========== Test Case: Network Fast-Fail ==========

    @Test
    fun `startListening fails immediately when no network`() {
        every { connectivityManager.activeNetwork } returns null

        manager.startListening()
        idleMainLooper()

        assertTrue(manager.state.value is VoiceInputState.Error)
        assertEquals(R.string.voice_error_network, (manager.state.value as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `startListening fails immediately when no network capabilities`() {
        every { connectivityManager.getNetworkCapabilities(network) } returns null

        manager.startListening()
        idleMainLooper()

        assertTrue(manager.state.value is VoiceInputState.Error)
        assertEquals(R.string.voice_error_network, (manager.state.value as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `startListening fails immediately when no internet capability`() {
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false

        manager.startListening()
        idleMainLooper()

        assertTrue(manager.state.value is VoiceInputState.Error)
        assertEquals(R.string.voice_error_network, (manager.state.value as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `network error from fast-fail is retryable`() {
        every { connectivityManager.activeNetwork } returns null

        manager.startListening()
        idleMainLooper()

        assertTrue((manager.state.value as VoiceInputState.Error).isRetryable)
    }

    @Test
    fun `startListening does not create recognizer when no network`() {
        every { connectivityManager.activeNetwork } returns null

        manager.startListening()
        idleMainLooper()

        verify(exactly = 0) { SpeechRecognizer.createSpeechRecognizer(any()) }
    }

    // ========== Test Case: Audio Focus ==========

    @Test
    fun `startListening fails when audio focus not granted`() {
        every { audioManager.requestAudioFocus(any<AudioFocusRequest>()) } returns AudioManager.AUDIOFOCUS_REQUEST_FAILED

        manager.startListening()
        idleMainLooper()

        assertTrue(manager.state.value is VoiceInputState.Error)
        assertEquals(R.string.voice_error_audio, (manager.state.value as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `audio focus error is retryable`() {
        every { audioManager.requestAudioFocus(any<AudioFocusRequest>()) } returns AudioManager.AUDIOFOCUS_REQUEST_FAILED

        manager.startListening()
        idleMainLooper()

        assertTrue((manager.state.value as VoiceInputState.Error).isRetryable)
    }

    @Test
    fun `startListening does not create recognizer when audio focus denied`() {
        every { audioManager.requestAudioFocus(any<AudioFocusRequest>()) } returns AudioManager.AUDIOFOCUS_REQUEST_FAILED

        manager.startListening()
        idleMainLooper()

        verify(exactly = 0) { SpeechRecognizer.createSpeechRecognizer(any()) }
    }

    @Test
    fun `audio focus is abandoned when recognizer is destroyed`() {
        manager.startListening()
        idleMainLooper()

        manager.close()

        verify { audioManager.abandonAudioFocusRequest(any()) }
    }

    @Test
    fun `startListening requests audio focus`() {
        manager.startListening()
        idleMainLooper()

        verify { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
    }

    // ========== Helper Functions ==========

    private fun createResultsBundle(text: String): Bundle =
        Bundle().apply {
            putStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf(text),
            )
        }
}

private typealias CapturingSlot<T> = io.mockk.CapturingSlot<T>
