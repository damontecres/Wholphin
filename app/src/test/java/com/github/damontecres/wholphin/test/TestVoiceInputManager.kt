package com.github.damontecres.wholphin.test

import android.app.Activity
import android.content.Intent
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
import org.robolectric.annotation.Config

/**
 * Unit tests for [VoiceInputManager] state machine logic.
 *
 * Uses Robolectric to provide Android framework classes (Intent, Bundle)
 * and Mockk to mock [SpeechRecognizer] and simulate recognition callbacks
 * without requiring a real microphone or emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TestVoiceInputManager {
    private lateinit var activity: Activity
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var listenerSlot: CapturingSlot<RecognitionListener>
    private lateinit var manager: VoiceInputManager

    private val capturedListener: RecognitionListener
        get() = listenerSlot.captured

    @Before
    fun setup() {
        // Mock Activity
        activity = mockk(relaxed = true)

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
        assertEquals(VoiceInputState.Idle, manager.state)
    }

    @Test
    fun `initial soundLevel is zero`() {
        assertEquals(0f, manager.soundLevel)
    }

    @Test
    fun `initial partialResult is empty`() {
        assertEquals("", manager.partialResult)
    }

    // ========== Test Case 2: Start Listening ==========

    @Test
    fun `startListening transitions state to Listening`() {
        manager.startListening()

        assertEquals(VoiceInputState.Listening, manager.state)
    }

    @Test
    fun `startListening creates SpeechRecognizer`() {
        manager.startListening()

        verify { SpeechRecognizer.createSpeechRecognizer(activity) }
    }

    @Test
    fun `startListening sets recognition listener`() {
        manager.startListening()

        verify { speechRecognizer.setRecognitionListener(any()) }
        assertTrue(listenerSlot.isCaptured)
    }

    @Test
    fun `startListening calls recognizer startListening`() {
        manager.startListening()

        verify { speechRecognizer.startListening(any<Intent>()) }
    }

    @Test
    fun `startListening resets partialResult`() {
        manager.startListening()
        capturedListener.onPartialResults(createResultsBundle("partial"))
        manager.stopListening()

        manager.startListening()

        assertEquals("", manager.partialResult)
    }

    @Test
    fun `startListening is ignored when already listening`() {
        manager.startListening()
        manager.startListening() // Should be ignored

        // Only one recognizer should be created
        verify(exactly = 1) { SpeechRecognizer.createSpeechRecognizer(activity) }
    }

    // ========== Test Case 3: Permission Denied ==========

    @Test
    fun `onPermissionDenied transitions to Error state`() {
        manager.onPermissionDenied()

        assertTrue(manager.state is VoiceInputState.Error)
    }

    @Test
    fun `onPermissionDenied sets correct error resource`() {
        manager.onPermissionDenied()

        val errorState = manager.state as VoiceInputState.Error
        assertEquals(R.string.voice_error_permissions, errorState.messageResId)
    }

    // ========== Test Case 4: Result Success ==========

    @Test
    fun `onResults transitions to Result state with correct text`() {
        manager.startListening()

        capturedListener.onResults(createResultsBundle("hello world"))

        assertTrue(manager.state is VoiceInputState.Result)
        assertEquals("hello world", (manager.state as VoiceInputState.Result).text)
    }

    @Test
    fun `onResults resets soundLevel to zero`() {
        manager.startListening()
        capturedListener.onRmsChanged(5f)

        capturedListener.onResults(createResultsBundle("test"))

        assertEquals(0f, manager.soundLevel)
    }

    @Test
    fun `onResults with empty text transitions to Error state`() {
        manager.startListening()

        capturedListener.onResults(createResultsBundle(""))

        assertTrue(manager.state is VoiceInputState.Error)
        assertEquals(R.string.voice_error_no_match, (manager.state as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onResults with null bundle transitions to Error state`() {
        manager.startListening()

        capturedListener.onResults(null)

        assertTrue(manager.state is VoiceInputState.Error)
        assertEquals(R.string.voice_error_no_match, (manager.state as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onResults with blank text transitions to Error state`() {
        manager.startListening()

        capturedListener.onResults(createResultsBundle("   "))

        assertTrue(manager.state is VoiceInputState.Error)
    }

    // ========== Test Case 5: Error Handling ==========

    @Test
    fun `onError transitions to Error state`() {
        manager.startListening()

        capturedListener.onError(SpeechRecognizer.ERROR_NETWORK)

        assertTrue(manager.state is VoiceInputState.Error)
    }

    @Test
    fun `onError maps ERROR_NETWORK to correct resource`() {
        manager.startListening()

        capturedListener.onError(SpeechRecognizer.ERROR_NETWORK)

        assertEquals(R.string.voice_error_network, (manager.state as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError maps ERROR_NO_MATCH to correct resource`() {
        manager.startListening()

        capturedListener.onError(SpeechRecognizer.ERROR_NO_MATCH)

        assertEquals(R.string.voice_error_no_match, (manager.state as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError maps ERROR_AUDIO to correct resource`() {
        manager.startListening()

        capturedListener.onError(SpeechRecognizer.ERROR_AUDIO)

        assertEquals(R.string.voice_error_audio, (manager.state as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError maps ERROR_SERVER to correct resource`() {
        manager.startListening()

        capturedListener.onError(SpeechRecognizer.ERROR_SERVER)

        assertEquals(R.string.voice_error_server, (manager.state as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError maps ERROR_CLIENT to correct resource`() {
        manager.startListening()

        capturedListener.onError(SpeechRecognizer.ERROR_CLIENT)

        assertEquals(R.string.voice_error_client, (manager.state as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError maps ERROR_SPEECH_TIMEOUT to correct resource`() {
        manager.startListening()

        capturedListener.onError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)

        assertEquals(R.string.voice_error_speech_timeout, (manager.state as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError maps ERROR_NETWORK_TIMEOUT to correct resource`() {
        manager.startListening()

        capturedListener.onError(SpeechRecognizer.ERROR_NETWORK_TIMEOUT)

        assertEquals(R.string.voice_error_network_timeout, (manager.state as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError maps ERROR_RECOGNIZER_BUSY to correct resource`() {
        manager.startListening()

        capturedListener.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)

        assertEquals(R.string.voice_error_busy, (manager.state as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError maps ERROR_INSUFFICIENT_PERMISSIONS to correct resource`() {
        manager.startListening()

        capturedListener.onError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)

        assertEquals(R.string.voice_error_permissions, (manager.state as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError maps unknown error to unknown resource`() {
        manager.startListening()

        capturedListener.onError(999) // Unknown error code

        assertEquals(R.string.voice_error_unknown, (manager.state as VoiceInputState.Error).messageResId)
    }

    @Test
    fun `onError resets soundLevel to zero`() {
        manager.startListening()
        capturedListener.onRmsChanged(5f)

        capturedListener.onError(SpeechRecognizer.ERROR_NETWORK)

        assertEquals(0f, manager.soundLevel)
    }

    // ========== Test Case 6: Cleanup/Lifecycle ==========

    @Test
    fun `cleanup resets state to Idle`() {
        manager.startListening()

        manager.cleanup()

        assertEquals(VoiceInputState.Idle, manager.state)
    }

    @Test
    fun `cleanup calls cancel on recognizer`() {
        manager.startListening()

        manager.cleanup()

        verify { speechRecognizer.cancel() }
    }

    @Test
    fun `cleanup calls destroy on recognizer`() {
        manager.startListening()

        manager.cleanup()

        verify { speechRecognizer.destroy() }
    }

    @Test
    fun `cleanup resets soundLevel to zero`() {
        manager.startListening()
        capturedListener.onRmsChanged(5f)

        manager.cleanup()

        assertEquals(0f, manager.soundLevel)
    }

    @Test
    fun `cleanup resets partialResult to empty`() {
        manager.startListening()
        capturedListener.onPartialResults(createResultsBundle("partial"))

        manager.cleanup()

        assertEquals("", manager.partialResult)
    }

    @Test
    fun `stopListening triggers cleanup`() {
        manager.startListening()

        manager.stopListening()

        assertEquals(VoiceInputState.Idle, manager.state)
        verify { speechRecognizer.cancel() }
        verify { speechRecognizer.destroy() }
    }

    // ========== Additional Coverage ==========

    @Test
    fun `onEndOfSpeech transitions to Processing state`() {
        manager.startListening()

        capturedListener.onEndOfSpeech()

        assertEquals(VoiceInputState.Processing, manager.state)
    }

    @Test
    fun `onRmsChanged updates soundLevel with normalized value`() {
        manager.startListening()

        // RMS normalization: (rmsdB - (-2)) / (10 - (-2)) clamped to [0, 1]
        // For rmsdB = 4: (4 - (-2)) / 12 = 6/12 = 0.5
        capturedListener.onRmsChanged(4f)

        assertEquals(0.5f, manager.soundLevel, 0.01f)
    }

    @Test
    fun `onRmsChanged clamps high values to 1`() {
        manager.startListening()

        capturedListener.onRmsChanged(20f) // Above max

        assertEquals(1f, manager.soundLevel)
    }

    @Test
    fun `onRmsChanged clamps low values to 0`() {
        manager.startListening()

        capturedListener.onRmsChanged(-10f) // Below min

        assertEquals(0f, manager.soundLevel)
    }

    @Test
    fun `onPartialResults updates partialResult`() {
        manager.startListening()

        capturedListener.onPartialResults(createResultsBundle("hello"))

        assertEquals("hello", manager.partialResult)
    }

    @Test
    fun `onPartialResults ignores blank text`() {
        manager.startListening()
        capturedListener.onPartialResults(createResultsBundle("first"))

        capturedListener.onPartialResults(createResultsBundle("  "))

        assertEquals("first", manager.partialResult)
    }

    @Test
    fun `acknowledge resets state to Idle`() {
        manager.startListening()
        capturedListener.onResults(createResultsBundle("test"))

        manager.acknowledge()

        assertEquals(VoiceInputState.Idle, manager.state)
    }

    @Test
    fun `acknowledge works from Error state`() {
        manager.onPermissionDenied()

        manager.acknowledge()

        assertEquals(VoiceInputState.Idle, manager.state)
    }

    @Test
    fun `onPermissionGranted calls startListening`() {
        manager.onPermissionGranted()

        assertEquals(VoiceInputState.Listening, manager.state)
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
        val firstListener = firstListenerSlot.captured

        manager.cleanup()
        manager.startListening()

        // Simulate callback from the old (zombie) recognizer
        firstListener.onResults(createResultsBundle("zombie result"))

        // State should remain Listening (from the second startListening), not Result
        assertEquals(VoiceInputState.Listening, manager.state)
    }

    @Test
    fun `isAvailable returns mocked value`() {
        assertTrue(manager.isAvailable)
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
