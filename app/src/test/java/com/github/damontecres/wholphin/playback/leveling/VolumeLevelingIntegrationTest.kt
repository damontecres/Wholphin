@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.github.damontecres.wholphin.playback.leveling

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.test.core.app.ApplicationProvider
import com.github.damontecres.wholphin.playback.leveling.core.LevelingMode
import com.github.damontecres.wholphin.preferences.*
import com.github.damontecres.wholphin.services.PlayerFactory
import com.github.damontecres.wholphin.services.WholphinRenderersFactory
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.*
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class VolumeLevelingIntegrationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val raw = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setSampleRate(48000).setChannelCount(2).setPcmEncoding(C.ENCODING_PCM_16BIT).build()

    @Test
    fun `default and legacy protobuf preferences keep leveling Off and ExoPlayer selected`() = runTest {
        val serializer = AppPreferencesSerializer()
        for (prefs in listOf(serializer.defaultValue, serializer.readFrom(ByteArrayInputStream(byteArrayOf())), AppPreferences.getDefaultInstance())) {
            assertEquals(false, prefs.playbackPreferences.reduceLoudSounds)
            assertEquals(PlayerBackend.EXO_PLAYER, prefs.playbackPreferences.playerBackend)
        }
        assertFalse(AppPreference.ReduceLoudSounds.defaultValue)
    }

    @Test
    fun `old enum field values are ignored without crashing or enabling the new boolean`() = runTest {
        for (oldValue in listOf(0, 1, 2, 99)) {
            // Old playback field 27, wire type varint. The new boolean uses field 28.
            val playback = PlaybackPreferences.parseFrom(byteArrayOf(0xd8.toByte(), 0x01, oldValue.toByte()))
            val old = AppPreferences.newBuilder().setPlaybackPreferences(playback).build()
            val restored = AppPreferencesSerializer().readFrom(ByteArrayInputStream(old.toByteArray()))
            assertFalse(restored.playbackPreferences.reduceLoudSounds)
        }
    }

    @Test
    fun `boolean On uses the single named Strong profile and Off bypasses`() {
        assertEquals(LevelingMode.STRONG, REDUCE_LOUD_SOUNDS_PROFILE)
        assertEquals(REDUCE_LOUD_SOUNDS_PROFILE, true.toLevelingMode())
        assertEquals(LevelingMode.OFF, false.toLevelingMode())
    }

    @Test
    fun `choice persists through serializer without changing other playback preferences`() = runTest {
        val original = AppPreferencesSerializer().defaultValue.updatePlaybackPreferences { maxBitrate = 987654; skipBackMs = 7000 }
        for (mode in listOf(false, true)) {
            val updated = AppPreference.ReduceLoudSounds.setter(original, mode)
            val bytes = ByteArrayOutputStream()
            AppPreferencesSerializer().writeTo(updated, bytes)
            val restored = AppPreferencesSerializer().readFrom(ByteArrayInputStream(bytes.toByteArray()))
            assertEquals(mode, AppPreference.ReduceLoudSounds.getter(restored))
            assertEquals(original, restored.updatePlaybackPreferences { reduceLoudSounds = false })
        }
    }

    @Test
    fun `PCM-only sink refuses every encoded receiver format including offload`() {
        val receiver = mockk<AudioSink>()
        every { receiver.supportsFormat(any()) } returns true
        every { receiver.getFormatSupport(any()) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        val offload = androidx.media3.exoplayer.audio.AudioOffloadSupport.Builder().setIsFormatSupported(true).build()
        every { receiver.getFormatOffloadSupport(any()) } returns offload
        val originalBehavior = receiver
        val guarded = PcmOnlyAudioSink(receiver)
        for (mime in listOf(MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_TRUEHD, MimeTypes.AUDIO_AAC)) {
            val encoded = raw.buildUpon().setSampleMimeType(mime).build()
            assertTrue(originalBehavior.supportsFormat(encoded))
            assertEquals(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY, originalBehavior.getFormatSupport(encoded))
            assertSame(offload, originalBehavior.getFormatOffloadSupport(encoded))
            assertFalse(guarded.supportsFormat(encoded))
            assertEquals(AudioSink.SINK_FORMAT_UNSUPPORTED, guarded.getFormatSupport(encoded))
            assertFalse(guarded.getFormatOffloadSupport(encoded).isFormatSupported)
        }
        assertTrue(guarded.supportsFormat(raw))
        assertEquals(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY, guarded.getFormatSupport(raw))
        assertFalse(guarded.getFormatOffloadSupport(raw).isFormatSupported)
    }

    @Test
    fun `renderers factory installs custom processing for PCM even when mode starts Off`() {
        for (mode in listOf(false, true)) {
            val processor = spyk(VolumeLevelingAudioProcessor(VolumeLevelingState(mode.toLevelingMode())))
            val factory = WholphinRenderersFactory(context, false, volumeProcessor = processor, pcmOnly = mode)
            val sink = checkNotNull(factory.buildAudioSink(context, false, false))
            try {
                sink.configure(raw, 0, null)
                verify { processor.configure(AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)) }
                assertTrue(processor.isActive)
                assertEquals(mode, (sink as PcmOnlyAudioSink).pcmOnly)
            } finally {
                sink.reset()
                sink.release()
            }
        }
    }

    @Test
    fun `PCM-only sink rejects encoded configure`() {
        val encoded = raw.buildUpon().setSampleMimeType(MimeTypes.AUDIO_AC3).build()
        val guarded = PcmOnlyAudioSink(mockk(relaxed = true))
        assertThrows(AudioSink.ConfigurationException::class.java) {
            guarded.configure(androidx.media3.exoplayer.audio.AudioSink.AudioSinkConfig.Builder(encoded).build())
        }
    }

    @Test
    fun `non-video factory retains the unmodified default sink path`() {
        val factory = WholphinRenderersFactory(context, false)
        val sink = checkNotNull(factory.buildAudioSink(context, false, false))
        assertFalse(sink is PcmOnlyAudioSink)
        sink.release()
    }

    @Test
    fun `video factory disables tunneling and offload when leveling starts on`() = runTest {
        WholphinDispatchers.configure(StandardTestDispatcher(testScheduler))
        try {
            val factory = PlayerFactory(context, OkHttpClient())
            val prefs = factoryPreferences(true)
            val player = factory.createVideoPlayer(PlayerBackend.EXO_PLAYER, prefs).player as ExoPlayer
            try {
                val parameters = (player.trackSelector as DefaultTrackSelector).parameters
                assertFalse(parameters.tunnelingEnabled)
                assertEquals(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED, parameters.audioOffloadPreferences.audioOffloadMode)
                val installed = installedProcessor(player)
                assertTrue(installed.first is PcmOnlyAudioSink)
                val processor = checkNotNull(installed.second)
                assertEquals(REDUCE_LOUD_SOUNDS_PROFILE, processor.state.mode)
                val epoch = processor.state.mediaEpoch.get()
                player.setMediaItem(androidx.media3.common.MediaItem.fromUri("https://example.invalid/video.mp4"))
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertTrue("real player transition must reset the installed processor", processor.state.mediaEpoch.get() > epoch)
                factory.setReduceLoudSounds(false)
                assertEquals(LevelingMode.OFF, processor.state.mode)
            } finally { player.release() }
        } finally { WholphinDispatchers.reset() }
    }

    @Test
    fun `Off at video creation preserves existing tunneling and offload preferences`() = runTest {
        WholphinDispatchers.configure(StandardTestDispatcher(testScheduler))
        try {
            for (disabled in listOf(false, true)) for (tunneled in listOf(false, true)) {
                val factory = PlayerFactory(context, OkHttpClient())
                val prefs = factoryPreferences(false).updateExperimentalPreferences {
                    disableAudioOffload = disabled
                    videoTunnelingEnabled = tunneled
                }
                val player = factory.createVideoPlayer(PlayerBackend.EXO_PLAYER, prefs).player as ExoPlayer
                try {
                    val parameters = (player.trackSelector as DefaultTrackSelector).parameters
                    assertEquals(tunneled, parameters.tunnelingEnabled)
                    val installed = installedProcessor(player)
                    assertFalse((installed.first as? PcmOnlyAudioSink)?.pcmOnly == true)
                    if (tunneled) {
                        assertNull("Off-created tunneling must keep the original non-buffering sink", installed.second)
                    } else {
                        assertEquals(LevelingMode.OFF, checkNotNull(installed.second).state.mode)
                    }
                    assertEquals(
                        if (disabled) AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED else AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED,
                        parameters.audioOffloadPreferences.audioOffloadMode,
                    )
                    factory.setReduceLoudSounds(true)
                    assertEquals(true, factory.reduceLoudSounds.value)
                    if (!tunneled) assertEquals(LevelingMode.STRONG, checkNotNull(installed.second).state.mode)
                    assertEquals(tunneled, (player.trackSelector as DefaultTrackSelector).parameters.tunnelingEnabled)
                } finally { player.release() }
            }
        } finally { WholphinDispatchers.reset() }
    }

    @Test
    fun `MPV prefer-MPV and external backends never activate the ExoPlayer leveling policy`() {
        for (backend in listOf(PlayerBackend.MPV, PlayerBackend.PREFER_MPV, PlayerBackend.EXTERNAL_PLAYER)) {
            for (mode in listOf(false, true)) {
                val policy = VolumeLevelingPolicy(backend, mode)
                assertFalse(policy.supported)
                assertFalse(policy.pcmOnly)
                assertFalse(policy.disableOffload(false))
                assertTrue(policy.tunneling(true) == true)
            }
        }
    }

    @Test
    fun `mode holder shares live writes with an already constructed processor`() {
        val holder = VolumeLevelingState()
        val processor = VolumeLevelingAudioProcessor(holder)
        val writer = Thread { holder.mode = LevelingMode.STRONG; holder.onMediaItemTransition() }
        writer.start()
        writer.join()
        assertSame(holder, processor.state)
        assertEquals(LevelingMode.STRONG, processor.state.mode)
        assertEquals(1L, processor.state.mediaEpoch.get())
    }

    @Test
    fun `Off sink delegates receiver capabilities until PCM is positively configured`() {
        val delegate = mockk<AudioSink>(relaxed = true)
        val encoded = raw.buildUpon().setSampleMimeType(MimeTypes.AUDIO_E_AC3).build()
        val offload = androidx.media3.exoplayer.audio.AudioOffloadSupport.Builder().setIsFormatSupported(true).build()
        every { delegate.supportsFormat(any()) } returns true
        every { delegate.getFormatSupport(any()) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        every { delegate.getFormatOffloadSupport(any()) } returns offload
        val sink = PcmOnlyAudioSink(delegate, pcmOnly = false)
        assertFalse(sink.enableIfConfiguredPcm())
        assertTrue(sink.supportsFormat(encoded))
        assertEquals(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY, sink.getFormatSupport(encoded))
        assertSame(offload, sink.getFormatOffloadSupport(encoded))
        sink.configure(AudioSink.AudioSinkConfig.Builder(encoded).build())
        assertFalse("encoded/offloaded/passthrough output must rebuild", sink.enableIfConfiguredPcm())
        assertFalse(sink.pcmOnly)
        verify(exactly = 0) { delegate.flush() }
    }

    @Test
    fun `PCM latch keeps the existing sink alive and prevents every later encoded route`() {
        for (encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) {
            val delegate = mockk<AudioSink>(relaxed = true)
            every { delegate.supportsFormat(any()) } returns true
            every { delegate.getFormatSupport(any()) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
            val sink = PcmOnlyAudioSink(delegate, pcmOnly = false)
            val pcm = raw.buildUpon().setPcmEncoding(encoding).build()
            val listener = slot<AudioSink.Listener>()
            every { delegate.setListener(capture(listener)) } just Runs
            sink.setListener(mockk(relaxed = true))
            sink.configure(AudioSink.AudioSinkConfig.Builder(pcm).build())
            assertFalse("configuration alone does not prove the active AudioTrack route", sink.enableIfConfiguredPcm())
            listener.captured.onAudioTrackInitialized(AudioSink.AudioTrackConfig(encoding, 48000, 12, false, true, 4096))
            assertFalse("offload must fall back even if a device claims PCM", sink.enableIfConfiguredPcm())
            listener.captured.onAudioTrackInitialized(AudioSink.AudioTrackConfig(encoding, 48000, 12, true, false, 4096))
            assertFalse("tunneling must fall back", sink.enableIfConfiguredPcm())
            listener.captured.onAudioTrackInitialized(AudioSink.AudioTrackConfig(encoding, 48000, 12, false, false, 4096))
            assertTrue(sink.enableIfConfiguredPcm())
            assertTrue(sink.pcmOnly)
            for (mime in listOf(MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_TRUEHD, MimeTypes.AUDIO_AAC)) {
                val encoded = pcm.buildUpon().setSampleMimeType(mime).build()
                assertFalse(sink.supportsFormat(encoded))
                assertEquals(AudioSink.SINK_FORMAT_UNSUPPORTED, sink.getFormatSupport(encoded))
                assertFalse(sink.getFormatOffloadSupport(encoded).isFormatSupported)
                assertThrows(AudioSink.ConfigurationException::class.java) {
                    sink.configure(AudioSink.AudioSinkConfig.Builder(encoded).build())
                }
            }
            verify(exactly = 0) { delegate.flush() }
            verify(exactly = 0) { delegate.reset() }
            verify(exactly = 0) { delegate.release() }
            sink.reset()
            assertTrue("PCM-only protection lasts for the player's lifetime", sink.pcmOnly)
            assertFalse("reset invalidates positive format knowledge", sink.enableIfConfiguredPcm())
        }
    }

    @Test
    fun `failed PCM configuration never makes the fast path eligible`() {
        val delegate = mockk<AudioSink>(relaxed = true)
        every { delegate.configure(any<AudioSink.AudioSinkConfig>()) } throws AudioSink.ConfigurationException("test", raw)
        val sink = PcmOnlyAudioSink(delegate, pcmOnly = false)
        assertThrows(AudioSink.ConfigurationException::class.java) {
            sink.configure(AudioSink.AudioSinkConfig.Builder(raw).build())
        }
        assertFalse(sink.enableIfConfiguredPcm())
        assertFalse(sink.pcmOnly)
    }

    @Test
    fun `real player PCM promotion preserves queued messages timeline position tracks and playback intent`() = runTest {
        WholphinDispatchers.configure(StandardTestDispatcher(testScheduler))
        try {
            val factory = PlayerFactory(context, OkHttpClient())
            val prefs = factoryPreferences(false).updateExperimentalPreferences { videoTunnelingEnabled = false }
            val player = factory.createVideoPlayer(PlayerBackend.EXO_PLAYER, prefs).player as ExoPlayer
            try {
                player.setMediaItem(androidx.media3.common.MediaItem.fromUri("https://example.invalid/video.mp4"), 1234L)
                player.playWhenReady = false
                player.playbackParameters = androidx.media3.common.PlaybackParameters(1.25f)
                player.volume = .7f
                val audioGroup = androidx.media3.common.TrackGroup("selected-audio", raw.buildUpon().setSampleMimeType(MimeTypes.AUDIO_AAC).build())
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setOverrideForType(androidx.media3.common.TrackSelectionOverride(audioGroup, 0))
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                var delivered = false
                val scheduled = player.createMessage { _, _ -> delivered = true }.setPosition(10000).send()
                player.createMessage { _, _ -> }.send().blockUntilDelivered(2000)
                val timeline = player.currentTimeline
                val parameters = player.trackSelectionParameters
                val installed = installedProcessor(player)
                val sink = installed.first as PcmOnlyAudioSink
                assertFalse(sink.pcmOnly)
                // An idle player's renderer hasn't installed its listener yet (onEnabled does that).
                // Supply that listener, then require a real AudioTrack initialization and PCM write.
                sink.setListener(mockk(relaxed = true))
                sink.configure(AudioSink.AudioSinkConfig.Builder(raw).build())
                val pcm = java.nio.ByteBuffer.allocateDirect(19200).order(java.nio.ByteOrder.nativeOrder())
                val deadline = System.nanoTime() + 2_000_000_000L
                while (pcm.hasRemaining() && System.nanoTime() < deadline) {
                    sink.handleBuffer(pcm, 0L, 1)
                    if (pcm.hasRemaining()) Thread.sleep(10)
                }
                assertFalse("the real sink must have initialized and accepted PCM", pcm.hasRemaining())
                factory.setReduceLoudSounds(true)
                val promotion = async { factory.tryEnableReduceLoudSoundsOnPcm() }
                testScheduler.runCurrent()
                player.createMessage { _, _ -> }.send().blockUntilDelivered(2000)
                testScheduler.runCurrent()
                assertTrue(promotion.await())
                assertSame(player, factory.currentPlayer)
                assertSame(timeline, player.currentTimeline)
                assertEquals(1234L, player.currentPosition)
                assertFalse(player.playWhenReady)
                assertEquals(1.25f, player.playbackParameters.speed)
                assertEquals(.7f, player.volume)
                assertTrue(player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
                assertEquals(parameters.overrides, player.trackSelectionParameters.overrides)
                assertTrue(sink.pcmOnly)
                assertEquals(REDUCE_LOUD_SOUNDS_PROFILE, checkNotNull(installed.second).state.mode)
                assertEquals(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED, player.trackSelectionParameters.audioOffloadPreferences.audioOffloadMode)
                assertFalse(scheduled.isCanceled)
                val processed = scheduled.javaClass.getDeclaredField("isProcessed").apply { isAccessible = true }
                assertFalse(processed.getBoolean(scheduled))
                assertFalse(delivered)
                factory.setReduceLoudSounds(false)
                assertTrue(sink.pcmOnly)
                assertEquals(LevelingMode.OFF, installed.second!!.state.mode)
                scheduled.cancel()
            } finally { player.release() }
        } finally { WholphinDispatchers.reset() }
    }

    @Test
    fun `copied core matches original recorded hashes after undoing the package rename`() {
        val root = File("src/main/java/com/github/damontecres/wholphin/playback/leveling/core")
        val recorded = File(root, "README.md").readText()
        assertTrue(recorded.contains("`fcdc3a8562a00b33739c4bafc3c86f5218c22edd`"))
        val expectedHashes = mapOf(
            "Api.kt" to "f556a8b2bc61282f641b3512b1770abca0515fdc2b633d53537ff033e3656f2f",
            "DspMath.kt" to "efe218bcede73e2fb906924f148c7b41dfeeffc16ed92bc56bd2f10f5c1d63d4",
            "LoudnessMeter.kt" to "bc1d948f25e5eb7b5c1148cd5f204c2b02f8b43a32dee5fdf87c841fbc847bed",
        )
        val files = root.listFiles()!!.filter { it.extension == "kt" }
        assertEquals(expectedHashes.keys, files.map { it.name }.toSet())
        files.forEach { file ->
            val original = file.readText().replace("package com.github.damontecres.wholphin.playback.leveling.core", "package volumeleveler")
            val hash = MessageDigest.getInstance("SHA-256").digest(original.toByteArray()).joinToString("") { "%02x".format(it) }
            assertEquals("${file.name}: copied core differs from source commit", expectedHashes.getValue(file.name), hash)
            assertTrue("${file.name}: $hash absent from provenance", recorded.contains("`${file.name}`: `$hash`"))
        }
    }

    /** Inspect the real player's audio renderer, then assert on the installed DSP's live behavior.
     * Reflection is confined to Media3's private sink/chain fields; no production test hooks.
     */
    private fun installedProcessor(player: ExoPlayer): Pair<AudioSink, VolumeLevelingAudioProcessor?> {
        val renderer = (0 until player.rendererCount).map { player.getRenderer(it) }
            .filterIsInstance<androidx.media3.exoplayer.audio.MediaCodecAudioRenderer>().single()
        val sinkField = androidx.media3.exoplayer.audio.MediaCodecAudioRenderer::class.java.getDeclaredField("audioSink").apply { isAccessible = true }
        val installedSink = sinkField.get(renderer) as AudioSink
        var sink = installedSink
        val delegateField = androidx.media3.exoplayer.audio.ForwardingAudioSink::class.java.getDeclaredField("sink").apply { isAccessible = true }
        while (sink is androidx.media3.exoplayer.audio.ForwardingAudioSink) sink = delegateField.get(sink) as AudioSink
        val chainField = androidx.media3.exoplayer.audio.DefaultAudioSink::class.java.getDeclaredField("audioProcessorChain").apply { isAccessible = true }
        val chain = chainField.get(sink) as androidx.media3.common.audio.AudioProcessorChain
        val processor = chain.audioProcessors.filterIsInstance<VolumeLevelingAudioProcessor>().singleOrNull()
        return installedSink to processor
    }

    private fun factoryPreferences(mode: Boolean): AppPreferences =
        AppPreferencesSerializer().defaultValue
            .updatePlaybackPreferences {
                reduceLoudSounds = mode
                overrides = overrides.toBuilder().setAssPlaybackMode(AssPlaybackMode.ASS_EXO_PLAYER).setMediaExtensionsEnabled(MediaExtensionStatus.MES_DISABLED).build()
            }.updateExperimentalPreferences {
                enabled = true
                videoTunnelingEnabled = true
                disableAudioOffload = false
            }
}
