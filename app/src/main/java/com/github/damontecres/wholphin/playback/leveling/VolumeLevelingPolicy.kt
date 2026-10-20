@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.github.damontecres.wholphin.playback.leveling

import androidx.media3.common.C
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

/** Creation policy; an existing non-tunneled PCM route may be promoted without rebuilding. */
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
class PcmOnlyAudioSink(
    sink: AudioSink,
    pcmOnly: Boolean = true,
) : ForwardingAudioSink(sink) {
    var pcmOnly: Boolean = pcmOnly
        private set
    private var configuredPcm = false

    @Volatile private var activePcmTrack = false

    override fun setListener(listener: AudioSink.Listener) {
        super.setListener(
            object : AudioSink.Listener by listener {
                override fun onAudioTrackInitialized(config: AudioSink.AudioTrackConfig) {
                    activePcmTrack = !config.offload && !config.tunneling &&
                        (config.encoding == C.ENCODING_PCM_16BIT || config.encoding == C.ENCODING_PCM_FLOAT)
                    listener.onAudioTrackInitialized(config)
                }

                override fun onAudioTrackReleased(config: AudioSink.AudioTrackConfig) {
                    activePcmTrack = false
                    listener.onAudioTrackReleased(config)
                }
            },
        )
    }

    /** Playback thread only, serialized with configure/handleBuffer. Positive evidence of both
     * decoded input and a real non-offloaded, non-tunneled PCM AudioTrack is required. Unknown
     * and encoded routes keep the established rebuild. The protection lasts until release.
     */
    fun enableIfConfiguredPcm(): Boolean {
        if (!configuredPcm || !activePcmTrack) return false
        pcmOnly = true
        return true
    }

    override fun configure(audioSinkConfig: AudioSinkConfig) {
        configuredPcm = false
        activePcmTrack = false
        if (pcmOnly && audioSinkConfig.format.sampleMimeType != MimeTypes.AUDIO_RAW) {
            throw AudioSink.ConfigurationException("Volume leveling requires decoded PCM", audioSinkConfig.format)
        }
        super.configure(audioSinkConfig)
        configuredPcm = audioSinkConfig.format.sampleMimeType == MimeTypes.AUDIO_RAW
    }

    override fun reset() {
        configuredPcm = false
        activePcmTrack = false
        super.reset()
    }

    override fun supportsFormat(format: Format): Boolean =
        (!pcmOnly || format.sampleMimeType == MimeTypes.AUDIO_RAW) && super.supportsFormat(format)

    override fun getFormatSupport(format: Format): Int =
        if (!pcmOnly || format.sampleMimeType == MimeTypes.AUDIO_RAW) super.getFormatSupport(format) else AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport =
        if (pcmOnly) AudioOffloadSupport.DEFAULT_UNSUPPORTED else super.getFormatOffloadSupport(format)
}
