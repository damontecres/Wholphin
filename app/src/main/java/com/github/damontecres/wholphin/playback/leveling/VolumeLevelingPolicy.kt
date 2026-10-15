@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.github.damontecres.wholphin.playback.leveling

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioSink.AudioSinkConfig
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import com.github.damontecres.wholphin.playback.leveling.core.LevelingMode
import com.github.damontecres.wholphin.preferences.PlayerBackend

/** The single enabled profile, kept outside the unchanged DSP so it can be retuned here. */
val REDUCE_LOUD_SOUNDS_PROFILE = LevelingMode.STRONG

fun Boolean.toLevelingMode(): LevelingMode = if (this) REDUCE_LOUD_SOUNDS_PROFILE else LevelingMode.OFF

/** Captured at player creation: enabling on an Off-created player requires a rebuild. */
data class VolumeLevelingPolicy(
    val backend: PlayerBackend,
    val enabled: Boolean,
) {
    val supported: Boolean = backend == PlayerBackend.EXO_PLAYER || backend == PlayerBackend.UNRECOGNIZED
    val pcmOnly: Boolean = supported && enabled

    fun disableOffload(existing: Boolean): Boolean = pcmOnly || existing
    fun tunneling(existing: Boolean?): Boolean? = if (pcmOnly) false else existing
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
