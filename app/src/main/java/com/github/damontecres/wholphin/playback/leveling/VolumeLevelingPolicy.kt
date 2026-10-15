@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.github.damontecres.wholphin.playback.leveling

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioSink.AudioSinkConfig
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import com.github.damontecres.wholphin.playback.leveling.core.LevelingMode
import com.github.damontecres.wholphin.preferences.EvenOutVolume
import com.github.damontecres.wholphin.preferences.PlayerBackend

fun EvenOutVolume.toLevelingMode(): LevelingMode =
    when (this) {
        EvenOutVolume.VOLUME_LIGHT -> LevelingMode.LIGHT
        EvenOutVolume.VOLUME_STRONG -> LevelingMode.STRONG
        else -> LevelingMode.OFF
    }

/** Captured at player creation: live changes never rebuild the sink or disturb playback. */
data class VolumeLevelingPolicy(
    val backend: PlayerBackend,
    val mode: LevelingMode,
) {
    val supported: Boolean = backend == PlayerBackend.EXO_PLAYER || backend == PlayerBackend.UNRECOGNIZED
    val pcmOnly: Boolean = supported && mode != LevelingMode.OFF

    fun disableOffload(existing: Boolean): Boolean = pcmOnly || existing
    fun tunneling(existing: Boolean?): Boolean? = if (pcmOnly) false else existing
}

/** Clear the PCM observation when a different track is configured, including PCM to passthrough. */
class VolumeLevelingAudioSink(sink: AudioSink, private val state: VolumeLevelingState) : ForwardingAudioSink(sink) {
    override fun configure(audioSinkConfig: AudioSinkConfig) {
        state.processingPcm = false
        state.pcmConfigured = audioSinkConfig.format.sampleMimeType == MimeTypes.AUDIO_RAW
        super.configure(audioSinkConfig)
    }
}

/** Media3 1.11 ignores Builder.setAudioCapabilities when Context is supplied. Guard the actual
 * sink support queries instead, including the direct/offload query used by MediaCodecAudioRenderer.
 */
class PcmOnlyAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    override fun configure(audioSinkConfig: AudioSinkConfig) {
        if (audioSinkConfig.format.sampleMimeType != MimeTypes.AUDIO_RAW) {
            throw AudioSink.ConfigurationException("Volume leveling requires decoded PCM", audioSinkConfig.format)
        }
        super.configure(audioSinkConfig)
    }

    override fun supportsFormat(format: Format): Boolean =
        format.sampleMimeType == MimeTypes.AUDIO_RAW && super.supportsFormat(format)

    override fun getFormatSupport(format: Format): Int =
        if (format.sampleMimeType == MimeTypes.AUDIO_RAW) super.getFormatSupport(format) else AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport = AudioOffloadSupport.DEFAULT_UNSUPPORTED
}
