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
            assertEquals(EvenOutVolume.VOLUME_OFF, prefs.playbackPreferences.evenOutVolume)
            assertEquals(PlayerBackend.EXO_PLAYER, prefs.playbackPreferences.playerBackend)
        }
        assertEquals(EvenOutVolume.VOLUME_OFF, AppPreference.EvenOutVolumePref.defaultValue)
    }

    @Test
    fun `choice persists through serializer without changing other playback preferences`() = runTest {
        val original = AppPreferencesSerializer().defaultValue.updatePlaybackPreferences { maxBitrate = 987654; skipBackMs = 7000 }
        for (mode in listOf(EvenOutVolume.VOLUME_OFF, EvenOutVolume.VOLUME_LIGHT, EvenOutVolume.VOLUME_STRONG)) {
            val updated = AppPreference.EvenOutVolumePref.setter(original, mode)
            val bytes = ByteArrayOutputStream()
            AppPreferencesSerializer().writeTo(updated, bytes)
            val restored = AppPreferencesSerializer().readFrom(ByteArrayInputStream(bytes.toByteArray()))
            assertEquals(mode, AppPreference.EvenOutVolumePref.getter(restored))
            assertEquals(original, restored.updatePlaybackPreferences { evenOutVolume = EvenOutVolume.VOLUME_OFF })
        }
    }

    @Test
    fun `PCM-only sink refuses every encoded receiver format including offload`() {
        val receiver = mockk<AudioSink>()
        every { receiver.supportsFormat(any()) } returns true
        every { receiver.getFormatSupport(any()) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        val offload = androidx.media3.exoplayer.audio.AudioOffloadSupport.Builder().setIsFormatSupported(true).build()
        every { receiver.getFormatOffloadSupport(any()) } returns offload
        val originalBehavior = VolumeLevelingAudioSink(receiver, VolumeLevelingState())
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
        for (mode in LevelingMode.entries) {
            val processor = spyk(VolumeLevelingAudioProcessor(VolumeLevelingState(mode)))
            val factory = WholphinRenderersFactory(context, false, volumeProcessor = processor, pcmOnly = mode != LevelingMode.OFF)
            val sink = checkNotNull(factory.buildAudioSink(context, false, false))
            try {
                sink.configure(raw, 0, null)
                verify { processor.configure(AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)) }
                assertTrue(processor.isActive)
                assertEquals(mode != LevelingMode.OFF, sink is PcmOnlyAudioSink)
            } finally {
                sink.reset()
                sink.release()
            }
        }
    }

    @Test
    fun `switching tracks clears the previous PCM observation and PCM-only rejects encoded configure`() {
        val state = VolumeLevelingState().apply { processingPcm = true }
        val sink = VolumeLevelingAudioSink(mockk(relaxed = true), state)
        val encoded = raw.buildUpon().setSampleMimeType(MimeTypes.AUDIO_AC3).build()
        sink.configure(androidx.media3.exoplayer.audio.AudioSink.AudioSinkConfig.Builder(encoded).build())
        assertFalse(state.processingPcm)
        val oldPcm = VolumeLevelingAudioProcessor(state)
        oldPcm.configure(AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
        oldPcm.flush()
        oldPcm.queueInput(java.nio.ByteBuffer.allocateDirect(4000).order(java.nio.ByteOrder.nativeOrder()))
        assertFalse("old PCM still draining cannot hide the new passthrough route", state.processingPcm)
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
            for (mode in listOf(EvenOutVolume.VOLUME_LIGHT, EvenOutVolume.VOLUME_STRONG)) {
                val factory = PlayerFactory(context, OkHttpClient())
                val prefs = factoryPreferences(mode)
                val player = factory.createVideoPlayer(PlayerBackend.EXO_PLAYER, prefs).player as ExoPlayer
                try {
                    val parameters = (player.trackSelector as DefaultTrackSelector).parameters
                    assertFalse(parameters.tunnelingEnabled)
                    assertEquals(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED, parameters.audioOffloadPreferences.audioOffloadMode)
                    assertEquals(mode, factory.evenOutVolume.value)
                    assertFalse(factory.levelingNeedsNextVideo)
                    val installed = installedProcessor(player)
                    assertTrue(installed.first is PcmOnlyAudioSink)
                    val processor = checkNotNull(installed.second)
                    assertEquals(mode.toLevelingMode(), processor.state.mode)
                    val epoch = processor.state.mediaEpoch.get()
                    player.setMediaItem(androidx.media3.common.MediaItem.fromUri("https://example.invalid/video.mp4"))
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    assertTrue("real player transition must reset the installed processor", processor.state.mediaEpoch.get() > epoch)
                    factory.setEvenOutVolume(EvenOutVolume.VOLUME_OFF)
                    assertEquals(LevelingMode.OFF, processor.state.mode)
                    assertFalse("live Off cannot rebuild or unguard the on-created sink", factory.levelingNeedsNextVideo)
                } finally { player.release() }
            }
        } finally { WholphinDispatchers.reset() }
    }

    @Test
    fun `Off at video creation preserves existing tunneling and offload preferences`() = runTest {
        WholphinDispatchers.configure(StandardTestDispatcher(testScheduler))
        try {
            for (disabled in listOf(false, true)) for (tunneled in listOf(false, true)) {
                val factory = PlayerFactory(context, OkHttpClient())
                val prefs = factoryPreferences(EvenOutVolume.VOLUME_OFF).updateExperimentalPreferences {
                    disableAudioOffload = disabled
                    videoTunnelingEnabled = tunneled
                }
                val player = factory.createVideoPlayer(PlayerBackend.EXO_PLAYER, prefs).player as ExoPlayer
                try {
                    val parameters = (player.trackSelector as DefaultTrackSelector).parameters
                    assertEquals(tunneled, parameters.tunnelingEnabled)
                    val installed = installedProcessor(player)
                    assertFalse(installed.first is PcmOnlyAudioSink)
                    if (tunneled) {
                        assertNull("Off-created tunneling must keep the original non-buffering sink", installed.second)
                    } else {
                        assertEquals(LevelingMode.OFF, checkNotNull(installed.second).state.mode)
                    }
                    assertEquals(
                        if (disabled) AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED else AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED,
                        parameters.audioOffloadPreferences.audioOffloadMode,
                    )
                    assertTrue("no PCM has reached the processor; toggle must be marked deferred", factory.levelingNeedsNextVideo)
                    factory.setEvenOutVolume(EvenOutVolume.VOLUME_STRONG)
                    assertEquals(EvenOutVolume.VOLUME_STRONG, factory.evenOutVolume.value)
                    if (!tunneled) assertEquals(LevelingMode.STRONG, checkNotNull(installed.second).state.mode)
                    assertTrue(factory.levelingNeedsNextVideo)
                    assertEquals(tunneled, (player.trackSelector as DefaultTrackSelector).parameters.tunnelingEnabled)
                } finally { player.release() }
            }
        } finally { WholphinDispatchers.reset() }
    }

    @Test
    fun `MPV prefer-MPV and external backends never activate the ExoPlayer leveling policy`() {
        for (backend in listOf(PlayerBackend.MPV, PlayerBackend.PREFER_MPV, PlayerBackend.EXTERNAL_PLAYER)) {
            for (mode in LevelingMode.entries) {
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
    fun `copied core matches original recorded hashes after undoing the package rename`() {
        val root = File("src/main/java/com/github/damontecres/wholphin/playback/leveling/core")
        val recorded = File(root, "README.md").readText()
        val files = root.listFiles()!!.filter { it.extension == "kt" }
        assertEquals(3, files.size)
        files.forEach { file ->
            val original = file.readText().replace("package com.github.damontecres.wholphin.playback.leveling.core", "package volumeleveler")
            val hash = MessageDigest.getInstance("SHA-256").digest(original.toByteArray()).joinToString("") { "%02x".format(it) }
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

    private fun factoryPreferences(mode: EvenOutVolume): AppPreferences =
        AppPreferencesSerializer().defaultValue
            .updatePlaybackPreferences {
                evenOutVolume = mode
                overrides = overrides.toBuilder().setAssPlaybackMode(AssPlaybackMode.ASS_EXO_PLAYER).setMediaExtensionsEnabled(MediaExtensionStatus.MES_DISABLED).build()
            }.updateExperimentalPreferences {
                enabled = true
                videoTunnelingEnabled = true
                disableAudioOffload = false
            }
}
