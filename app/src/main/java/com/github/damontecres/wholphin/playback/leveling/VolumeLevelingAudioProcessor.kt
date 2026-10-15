@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.github.damontecres.wholphin.playback.leveling

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import com.github.damontecres.wholphin.playback.leveling.core.LevelingMode
import com.github.damontecres.wholphin.playback.leveling.core.VolumeLeveler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/** UI writes mode/epoch only; the playback thread exclusively owns the DSP and buffers. */
class VolumeLevelingState(initialMode: LevelingMode = LevelingMode.OFF) {
    @Volatile
    var mode: LevelingMode = initialMode

    internal val mediaEpoch = AtomicLong()

    fun onMediaItemTransition() {
        mediaEpoch.incrementAndGet()
    }
}

/**
 * Removes the core's initial delay and drains its tail, retaining one output frame per input frame.
 * Scratch and direct output storage are allocated only on flush/format change. Large decoder buffers
 * are consumed in bounded chunks; Media3 calls queueInput again after consuming getOutput().
 */
class VolumeLevelingAudioProcessor(val state: VolumeLevelingState) : BaseAudioProcessor() {
    private var core: VolumeLeveler? = null
    private var format = AudioFormat.NOT_SET
    private var samples = FloatArray(0)
    private var processed = FloatArray(0)
    private var holdback = 0
    private var pendingFrames = 0
    private var epoch = 0L
    private var tailOutput: ByteBuffer = EMPTY_BUFFER
    private var endQueued = false

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (
            inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT ||
            inputAudioFormat.sampleRate !in 8000..384000 ||
            inputAudioFormat.channelCount !in 1..32
        ) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        // configure may precede draining the OLD format. Install the new DSP only at flush.
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: StreamMetadata) {
        if (inputAudioFormat == AudioFormat.NOT_SET) return
        val changed = format != inputAudioFormat || core == null
        if (changed) {
            core?.reset()
            format = inputAudioFormat
            core = VolumeLeveler(format.sampleRate, format.channelCount)
            val frames = maxOf(CHUNK_FRAMES, core!!.latencyFrames)
            samples = FloatArray(frames * format.channelCount)
            processed = FloatArray(samples.size)
            tailOutput = ByteBuffer.allocateDirect(core!!.latencyFrames * format.bytesPerFrame).order(ByteOrder.nativeOrder())
        } else {
            core!!.flush()
        }
        val newEpoch = state.mediaEpoch.get()
        if (epoch != newEpoch) core!!.reset()
        epoch = newEpoch
        holdback = core!!.latencyFrames
        pendingFrames = 0
        endQueued = false
        tailOutput.clear()
        tailOutput.flip()
        // Reserve enough for a transition's old tail followed by one chunk of the new item.
        replaceOutputBuffer((CHUNK_FRAMES + core!!.latencyFrames) * format.bytesPerFrame).flip()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining() || hasPendingOutput()) return
        val dsp = checkNotNull(core)
        require(inputBuffer.remaining() % format.bytesPerFrame == 0) { "Incomplete PCM frame" }
        val output = replaceOutputBuffer((CHUNK_FRAMES + dsp.latencyFrames) * format.bytesPerFrame)
        val newEpoch = state.mediaEpoch.get()
        if (epoch != newEpoch) {
            // A seamless transition need not cause a sink flush. Preserve the previous item's tail
            // before resetting its estimate, then hold back the new item's priming frames.
            drain(output)
            dsp.reset()
            holdback = dsp.latencyFrames
            pendingFrames = 0
            epoch = newEpoch
        }
        val frames = minOf(CHUNK_FRAMES, inputBuffer.remaining() / format.bytesPerFrame)
        val count = frames * format.channelCount
        var i = 0
        if (format.encoding == C.ENCODING_PCM_16BIT) {
            while (i < count) {
                samples[i++] = inputBuffer.short / 32768f
            }
        } else {
            while (i < count) {
                samples[i++] = inputBuffer.float
            }
        }
        dsp.mode = state.mode
        dsp.process(samples, 0, processed, 0, frames)
        val written = emit(output, frames)
        pendingFrames += frames - written
        output.flip()
    }

    private fun emit(output: ByteBuffer, frames: Int): Int {
        val skipped = minOf(holdback, frames)
        holdback -= skipped
        var i = skipped * format.channelCount
        val end = frames * format.channelCount
        if (format.encoding == C.ENCODING_PCM_16BIT) {
            while (i < end) {
                val value = (processed[i++].coerceIn(-1f, 1f) * 32768f).roundToInt()
                output.putShort(value.coerceIn(-32768, 32767).toShort())
            }
        } else {
            while (i < end) output.putFloat(processed[i++])
        }
        return frames - skipped
    }

    private fun drain(output: ByteBuffer) {
        if (pendingFrames == 0) return
        val dsp = checkNotNull(core)
        samples.fill(0f)
        // Keep the most recently processed mode through the old tail. The next queueInput reads
        // the latest UI mode; an EOS notification itself must not invent a new transition.
        dsp.process(samples, 0, processed, 0, dsp.latencyFrames)
        emit(output, dsp.latencyFrames)
        pendingFrames = 0
    }

    override fun onQueueEndOfStream() {
        if (core == null || endQueued) return
        endQueued = true
        // The pipeline may still hold getOutput()'s previous buffer when it signals EOS. A separate
        // preallocated tail buffer avoids overwriting that unconsumed audio.
        tailOutput.clear()
        drain(tailOutput)
        tailOutput.flip()
    }

    override fun getOutput(): ByteBuffer {
        val output = super.getOutput()
        return if (output.hasRemaining()) output else tailOutput
    }

    override fun isEnded(): Boolean = super.isEnded() && !tailOutput.hasRemaining()

    override fun onReset() {
        core?.reset()
        core = null
        format = AudioFormat.NOT_SET
        samples = FloatArray(0)
        processed = FloatArray(0)
        pendingFrames = 0
        holdback = 0
        tailOutput = EMPTY_BUFFER
    }

    private companion object {
        const val CHUNK_FRAMES = 1024
    }
}
