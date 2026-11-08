@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.github.damontecres.wholphin.playback.leveling

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import com.github.damontecres.wholphin.playback.leveling.core.LevelingMode
import com.github.damontecres.wholphin.playback.leveling.core.VolumeLeveler
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.random.Random

class VolumeLevelingAudioProcessorTest {
    private fun buffer(bytes: ByteArray): ByteBuffer =
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
            put(bytes)
            flip()
        }

    private fun configured(
        state: VolumeLevelingState = VolumeLevelingState(),
        rate: Int = 48000,
        channels: Int = 2,
        encoding: Int = C.ENCODING_PCM_16BIT,
    ): VolumeLevelingAudioProcessor =
        VolumeLevelingAudioProcessor(state).apply {
            assertEquals(AudioFormat(rate, channels, encoding), configure(AudioFormat(rate, channels, encoding)))
            assertTrue(isActive)
            flush()
            output // discard empty preallocated output
        }

    private fun copyOutput(
        processor: VolumeLevelingAudioProcessor,
        target: ByteArrayOutputStream,
    ) {
        val output = processor.output
        val bytes = ByteArray(output.remaining())
        output.get(bytes)
        target.write(bytes)
    }

    private fun feed(
        processor: VolumeLevelingAudioProcessor,
        bytes: ByteArray,
        target: ByteArrayOutputStream,
    ) {
        val input = buffer(bytes)
        while (input.hasRemaining()) {
            val before = input.position()
            processor.queueInput(input)
            copyOutput(processor, target)
            assertTrue("must consume input or expose output", input.position() > before)
        }
    }

    private fun finish(
        processor: VolumeLevelingAudioProcessor,
        target: ByteArrayOutputStream,
    ) {
        processor.queueEndOfStream()
        copyOutput(processor, target)
        assertTrue(processor.isEnded)
    }

    private fun run(
        bytes: ByteArray,
        rate: Int,
        channels: Int,
        encoding: Int,
        mode: LevelingMode,
        seed: Int,
    ): ByteArray {
        val processor = configured(VolumeLevelingState(mode), rate, channels, encoding)
        val result = ByteArrayOutputStream()
        val random = Random(seed)
        val frameBytes = channels * if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        var position = 0
        while (position < bytes.size) {
            val length = minOf(random.nextInt(1, 6000) * frameBytes, bytes.size - position)
            feed(processor, bytes.copyOfRange(position, position + length), result)
            position += length
        }
        finish(processor, result)
        return result.toByteArray()
    }

    @Test
    fun `Off preserves every PCM16 value including EOS across random chunks and channel counts`() {
        val values = ByteBuffer.allocate(65536 * 2).order(ByteOrder.nativeOrder())
        for (i in Short.MIN_VALUE..Short.MAX_VALUE) values.putShort(i.toShort())
        for ((rate, channels) in listOf(8000 to 1, 44100 to 2, 48000 to 6, 96000 to 8, 384000 to 1)) {
            val bytes = values.array().copyOf(values.capacity() / (2 * channels) * (2 * channels))
            repeat(
                3,
            ) { seed ->
                assertArrayEquals(
                    "$rate/$channels seed=$seed",
                    bytes,
                    run(bytes, rate, channels, C.ENCODING_PCM_16BIT, LevelingMode.OFF, seed),
                )
            }
        }
    }

    @Test
    fun `empty and sub-latency streams have exact length and no leading or trailing padding`() {
        for (frames in listOf(0, 1, 31, 191, 192, 193, 1024, 10000)) {
            val bytes = Random(frames).nextBytes(frames * 4)
            assertArrayEquals("frames=$frames", bytes, run(bytes, 48000, 2, C.ENCODING_PCM_16BIT, LevelingMode.OFF, frames))
        }
    }

    @Test
    fun `float Off preserves finite sample bits including signed zero`() {
        val input = ByteBuffer.allocate(2048 * 4).order(ByteOrder.nativeOrder())
        repeat(2048) { i ->
            input.putFloat(
                when (i % 4) {
                    0 -> -0f
                    1 -> 1f
                    2 -> -1f
                    else -> .12345678f
                },
            )
        }
        assertArrayEquals(input.array(), run(input.array(), 44100, 2, C.ENCODING_PCM_FLOAT, LevelingMode.OFF, 12))
    }

    @Test
    fun `enabled output equals the reference core with its delay removed for all channel layouts`() {
        for (channels in listOf(1, 2, 6, 8)) {
            for (mode in listOf(LevelingMode.LIGHT, LevelingMode.STRONG)) {
                val frames = 9001
                val samples = FloatArray(frames * channels) { i -> (sin(i * .041) * .7).toFloat() }
                val bytes =
                    ByteBuffer
                        .allocate(
                            samples.size * 4,
                        ).order(ByteOrder.nativeOrder())
                        .apply { samples.forEach { putFloat(it) } }
                        .array()
                val reference = VolumeLeveler(48000, channels).apply { this.mode = mode }
                val delayed = FloatArray(samples.size)
                reference.process(samples, 0, delayed, 0, frames)
                val tail = FloatArray(reference.latencyFrames * channels)
                reference.process(FloatArray(tail.size), 0, tail, 0, reference.latencyFrames)
                val expected = ByteBuffer.allocate(bytes.size).order(ByteOrder.nativeOrder())
                delayed.drop(reference.latencyFrames * channels).forEach { expected.putFloat(it) }
                tail.forEach { expected.putFloat(it) }
                assertArrayEquals("$channels/$mode", expected.array(), run(bytes, 48000, channels, C.ENCODING_PCM_FLOAT, mode, 31))
            }
        }
    }

    @Test
    fun `flush discards old tail and re-primes holdback without leaking samples`() {
        val processor = configured(VolumeLevelingState(LevelingMode.STRONG), channels = 1, encoding = C.ENCODING_PCM_FLOAT)
        val old = floats(1000) { .6 * sin(it * .05) }
        feed(processor, old, ByteArrayOutputStream())
        processor.flush()
        val result = ByteArrayOutputStream()
        val fresh = floats(300) { .3 * sin(it * .09) }
        feed(processor, fresh, result)
        finish(processor, result)
        val reference = VolumeLeveler(48000, 1).apply { mode = LevelingMode.STRONG }
        samples(old).let { reference.process(it, 0, FloatArray(it.size), 0, it.size) }
        reference.flush()
        assertArrayEquals(levelled(reference, fresh), result.toByteArray())
    }

    @Test
    fun `format change waits for flush and resets channel count and delay`() {
        val processor = configured(VolumeLevelingState(LevelingMode.STRONG))
        val old = Random(9).nextBytes(1200)
        val oldResult = ByteArrayOutputStream()
        feed(processor, old, oldResult)
        processor.configure(AudioFormat(96000, 6, C.ENCODING_PCM_FLOAT))
        finish(processor, oldResult)
        assertArrayEquals(run(old, 48000, 2, C.ENCODING_PCM_16BIT, LevelingMode.STRONG, 91), oldResult.toByteArray())
        processor.flush()
        val fresh =
            ByteBuffer
                .allocate(24)
                .order(ByteOrder.nativeOrder())
                .apply { repeat(6) { putFloat(it / 10f) } }
                .array()
        val result = ByteArrayOutputStream()
        feed(processor, fresh, result)
        finish(processor, result)
        assertArrayEquals(run(fresh, 96000, 6, C.ENCODING_PCM_FLOAT, LevelingMode.STRONG, 92), result.toByteArray())
    }

    @Test
    fun `a trained processor changing format matches a fresh core at the new rate and channel layout`() {
        val processor = configured(VolumeLevelingState(LevelingMode.STRONG), channels = 1, encoding = C.ENCODING_PCM_FLOAT)
        val training =
            ByteBuffer
                .allocate(48000 * 10 * 4)
                .order(ByteOrder.nativeOrder())
                .apply {
                    repeat(48000 * 10) { putFloat((.02 * sin(it * .06)).toFloat()) }
                }.array()
        feed(processor, training, ByteArrayOutputStream())
        processor.configure(AudioFormat(96000, 6, C.ENCODING_PCM_FLOAT))
        processor.flush()
        val fresh =
            ByteBuffer
                .allocate(48001 * 6 * 4)
                .order(ByteOrder.nativeOrder())
                .apply {
                    repeat(48001 * 6) { putFloat((.7 * sin(it * .07)).toFloat()) }
                }.array()
        val result = ByteArrayOutputStream()
        feed(processor, fresh, result)
        finish(processor, result)
        assertArrayEquals(run(fresh, 96000, 6, C.ENCODING_PCM_FLOAT, LevelingMode.STRONG, 73), result.toByteArray())
    }

    @Test
    fun `rejects unsupported encodings and invalid DSP formats`() {
        for (encoding in listOf(C.ENCODING_PCM_8BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_AC3)) {
            assertThrows(UnhandledAudioFormatException::class.java) { configured(encoding = encoding) }
        }
        assertThrows(UnhandledAudioFormatException::class.java) { configured(rate = 0) }
        assertThrows(UnhandledAudioFormatException::class.java) { configured(channels = 33) }
    }

    @Test
    fun `a seamless media transition preserves the old tail and frame alignment`() {
        val state = VolumeLevelingState(LevelingMode.STRONG)
        val processor = configured(state)
        val first = Random(7).nextBytes(4000)
        val second = Random(8).nextBytes(8000)
        val result = ByteArrayOutputStream()
        feed(processor, first, result)
        state.onMediaItemTransition()
        feed(processor, second, result)
        finish(processor, result)
        val expected =
            run(first, 48000, 2, C.ENCODING_PCM_16BIT, LevelingMode.STRONG, 71) +
                run(second, 48000, 2, C.ENCODING_PCM_16BIT, LevelingMode.STRONG, 72)
        assertArrayEquals(expected, result.toByteArray())
    }

    @Test
    fun `media transition resets a trained estimate to the new titles warmup`() {
        val state = VolumeLevelingState(LevelingMode.STRONG)
        val processor = configured(state, channels = 1, encoding = C.ENCODING_PCM_FLOAT)
        val quiet =
            ByteBuffer
                .allocate(48000 * 20 * 4)
                .order(ByteOrder.nativeOrder())
                .apply {
                    repeat(48000 * 20) { putFloat((.02 * sin(it * .06)).toFloat()) }
                }.array()
        feed(processor, quiet, ByteArrayOutputStream())
        state.onMediaItemTransition()
        val fresh = quiet.copyOf(4800 * 4)
        val result = ByteArrayOutputStream()
        feed(processor, fresh, result)
        finish(processor, result)
        val freshReference = run(fresh, 48000, 1, C.ENCODING_PCM_FLOAT, LevelingMode.STRONG, 82)
        val oldTail = ByteBuffer.wrap(result.toByteArray(), 0, 192 * 4).order(ByteOrder.nativeOrder())
        val dryTail = ByteBuffer.wrap(quiet, quiet.size - 192 * 4, 192 * 4).order(ByteOrder.nativeOrder())
        var oldEnergy = 0.0
        var dryEnergy = 0.0
        repeat(192) {
            val wet = oldTail.float.toDouble()
            val dry = dryTail.float.toDouble()
            oldEnergy += wet * wet
            dryEnergy += dry * dry
        }
        assertTrue("fixture must train gain before testing reset", oldEnergy > dryEnergy * 3)
        // The old tail is deliberately returned first; the NEW title starts at its own frame zero.
        assertArrayEquals(freshReference, result.toByteArray().takeLast(fresh.size).toByteArray())
    }

    @Test
    fun `live holder switch changes audio and returns to exact Off after crossfade`() {
        val state = VolumeLevelingState()
        val processor = configured(state, channels = 1)
        val tone =
            ByteBuffer
                .allocate(48000 * 2)
                .order(ByteOrder.nativeOrder())
                .apply {
                    repeat(48000) { putShort((sin(it * .07) * 29000).toInt().toShort()) }
                }.array()
        feed(processor, tone, ByteArrayOutputStream())
        state.mode = LevelingMode.STRONG
        val wet = ByteArrayOutputStream()
        repeat(4) { feed(processor, tone, wet) }
        assertFalse(tone.contentEquals(wet.toByteArray().takeLast(tone.size).toByteArray()))
        state.mode = LevelingMode.OFF
        val dry = ByteArrayOutputStream()
        feed(processor, tone, dry)
        feed(processor, tone, dry)
        assertFalse("Off must stop running the leveler once its fade ends", processor.levelerRunning)
        finish(processor, dry)
        assertArrayEquals(tone, dry.toByteArray().takeLast(tone.size).toByteArray())
        assertEquals(tone.size * 7L, wet.size() + dry.size() + tone.size.toLong())
        assertTrue(processor.isActive)
    }

    private fun floats(
        frames: Int,
        sample: (Int) -> Double,
    ): ByteArray =
        ByteBuffer
            .allocate(frames * 4)
            .order(ByteOrder.nativeOrder())
            .apply { repeat(frames) { putFloat(sample(it).toFloat()) } }
            .array()

    private fun samples(bytes: ByteArray): FloatArray {
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        return FloatArray(bytes.size / 4) { input.float }
    }

    // Mono float frames through a core in its current state, with its delay removed and its tail drained.
    private fun levelled(
        core: VolumeLeveler,
        bytes: ByteArray,
    ): ByteArray {
        val input = samples(bytes).copyOf(bytes.size / 4 + core.latencyFrames)
        val processed = FloatArray(input.size)
        core.process(input, 0, processed, 0, input.size)
        val output = ByteBuffer.allocate(bytes.size).order(ByteOrder.nativeOrder())
        processed.drop(core.latencyFrames).forEach { output.putFloat(it) }
        return output.array()
    }

    @Test
    fun `Off copies audio without running the leveler`() {
        for (encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) {
            val processor = configured(encoding = encoding)
            val bytes = Random(5).nextBytes(48000 * 8)
            val result = ByteArrayOutputStream()
            feed(processor, bytes, result)
            assertFalse(processor.levelerRunning)
            finish(processor, result)
            assertArrayEquals(bytes, result.toByteArray())
        }
    }

    @Test
    fun `turning on mid-stream starts a fresh leveler and fades it in without moving frames`() {
        val state = VolumeLevelingState(LevelingMode.STRONG)
        val processor = configured(state, channels = 1, encoding = C.ENCODING_PCM_FLOAT)
        val result = ByteArrayOutputStream()
        // Train an estimate that a fresh core would not have, then turn off until the core is idle.
        val training = floats(48000 * 20) { .02 * sin(it * .06) }
        feed(processor, training, result)
        state.mode = LevelingMode.OFF
        val off = floats(48000) { .5 * sin(it * .05) }
        feed(processor, off, result)
        assertFalse(processor.levelerRunning)
        state.mode = LevelingMode.STRONG
        val on = floats(48000 * 3) { (if (it < 72000) .05 else .8) * sin(it * .07) }
        val before = result.size()
        feed(processor, on, result)
        assertTrue(processor.levelerRunning)
        finish(processor, result)
        assertEquals(training.size + off.size + on.size, result.size())

        // The first chunk after switching on runs at Off, so the core starts there and fades in.
        val input = ByteBuffer.wrap(on).order(ByteOrder.nativeOrder())
        val samples = FloatArray(on.size / 4) { input.float }
        val reference = VolumeLeveler(48000, 1)
        val delayed = FloatArray(samples.size)
        reference.process(samples, 0, delayed, 0, 1024)
        reference.mode = LevelingMode.STRONG
        reference.process(samples, 1024, delayed, 1024, samples.size - 1024)
        val tail = FloatArray(reference.latencyFrames)
        reference.process(FloatArray(tail.size), 0, tail, 0, tail.size)
        val expected = ByteBuffer.allocate(on.size).order(ByteOrder.nativeOrder())
        delayed.drop(reference.latencyFrames).forEach { expected.putFloat(it) }
        tail.forEach { expected.putFloat(it) }
        val actual = result.toByteArray().copyOfRange(before, result.size())
        assertFalse("the leveler must change the loud part", on.contentEquals(actual))
        assertArrayEquals(expected.array(), actual)
    }

    @Test
    fun `seeking after switching on while idle starts a fresh leveler`() {
        val state = VolumeLevelingState(LevelingMode.STRONG)
        val processor = configured(state, channels = 1, encoding = C.ENCODING_PCM_FLOAT)
        val quiet = floats(48000 * 20) { .02 * sin(it * .06) }
        feed(processor, quiet, ByteArrayOutputStream())
        state.mode = LevelingMode.OFF
        feed(processor, quiet.copyOf(48000 * 4), ByteArrayOutputStream())
        assertFalse(processor.levelerRunning)
        state.mode = LevelingMode.STRONG
        processor.flush()
        val fresh = quiet.copyOf(4800 * 4)
        val result = ByteArrayOutputStream()
        feed(processor, fresh, result)
        finish(processor, result)
        assertArrayEquals(run(fresh, 48000, 1, C.ENCODING_PCM_FLOAT, LevelingMode.STRONG, 47), result.toByteArray())
    }

    @Test
    fun `input larger than fixed storage is consumed with bounded backpressure`() {
        val processor = configured()
        val bytes = Random(45).nextBytes(100000 * 4)
        val input = buffer(bytes)
        processor.queueInput(input)
        val firstPosition = input.position()
        assertTrue(firstPosition in 4..4096)
        processor.queueInput(input)
        assertEquals("must not overwrite unconsumed output", firstPosition, input.position())
        val result = ByteArrayOutputStream()
        copyOutput(processor, result)
        feed(processor, bytes.copyOfRange(input.position(), bytes.size), result)
        finish(processor, result)
        assertArrayEquals(bytes, result.toByteArray())
    }

    @Test
    fun `EOS preserves output still held by Media3 before exposing the delayed tail`() {
        for (retrieveFirst in listOf(false, true)) {
            val processor = configured(VolumeLevelingState(LevelingMode.STRONG))
            val bytes = Random(84).nextBytes(4000)
            processor.queueInput(buffer(bytes))
            val held = if (retrieveFirst) processor.output else null
            processor.queueEndOfStream()
            val result = ByteArrayOutputStream()
            if (held != null) {
                val first = ByteArray(held.remaining())
                held.get(first)
                result.write(first)
            }
            while (!processor.isEnded) copyOutput(processor, result)
            val expected = run(bytes, 48000, 2, C.ENCODING_PCM_16BIT, LevelingMode.STRONG, 84)
            assertArrayEquals("retrieveFirst=$retrieveFirst", expected, result.toByteArray())
        }
    }

    @Test
    fun `repeated EOS from an upstream processor cannot erase a partially consumed tail`() {
        val processor = configured(VolumeLevelingState(LevelingMode.STRONG))
        val bytes = Random(62).nextBytes(4000)
        val result = ByteArrayOutputStream()
        feed(processor, bytes, result)
        processor.queueEndOfStream()
        val tail = processor.output
        val first = tail.get()
        result.write(first.toInt())
        processor.queueEndOfStream()
        val rest = ByteArray(tail.remaining())
        tail.get(rest)
        result.write(rest)
        assertArrayEquals(run(bytes, 48000, 2, C.ENCODING_PCM_16BIT, LevelingMode.STRONG, 62), result.toByteArray())
    }

    @Test
    fun `Media3 pipeline preserves frame origin and tail with an active upstream processor`() {
        val processor = VolumeLevelingAudioProcessor(VolumeLevelingState(LevelingMode.STRONG))
        val mapping =
            androidx.media3.exoplayer.audio
                .ChannelMappingAudioProcessor()
                .apply { setChannelMap(intArrayOf(1, 0)) }
        val pipeline =
            androidx.media3.common.audio.AudioProcessingPipeline(
                com.google.common.collect.ImmutableList
                    .of(
                        mapping,
                        processor,
                        androidx.media3.common.audio
                            .SonicAudioProcessor(),
                    ),
            )
        pipeline.configure(AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
        pipeline.flush()
        val bytes = Random(451).nextBytes(32000)
        val input = buffer(bytes)
        val result = ByteArrayOutputStream()
        while (input.hasRemaining()) {
            pipeline.queueInput(input)
            val output = pipeline.output
            val block = ByteArray(output.remaining())
            output.get(block)
            result.write(block)
        }
        pipeline.queueEndOfStream()
        var iterations = 0
        while (!pipeline.isEnded) {
            assertTrue("pipeline must finish draining", iterations++ < 1000)
            val output = pipeline.output
            val block = ByteArray(output.remaining())
            output.get(block)
            result.write(block)
        }
        val swapped = ByteArray(bytes.size)
        for (frame in bytes.indices step 4) {
            swapped[frame] = bytes[frame + 2]
            swapped[frame + 1] = bytes[frame + 3]
            swapped[frame + 2] = bytes[frame]
            swapped[frame + 3] = bytes[frame + 1]
        }
        assertArrayEquals(run(swapped, 48000, 2, C.ENCODING_PCM_16BIT, LevelingMode.STRONG, 451), result.toByteArray())
    }

    @Test
    fun `flushing a seek preserves the trained level estimate`() {
        val processor = configured(VolumeLevelingState(LevelingMode.STRONG), channels = 1, encoding = C.ENCODING_PCM_FLOAT)
        val quiet =
            ByteBuffer
                .allocate(48000 * 20 * 4)
                .order(ByteOrder.nativeOrder())
                .apply {
                    repeat(48000 * 20) { putFloat((.02 * sin(it * .06)).toFloat()) }
                }.array()
        feed(processor, quiet, ByteArrayOutputStream())
        processor.flush()
        val fresh = quiet.copyOf(4800 * 4)
        val result = ByteArrayOutputStream()
        feed(processor, fresh, result)
        finish(processor, result)
        val settled = ByteBuffer.wrap(result.toByteArray()).order(ByteOrder.nativeOrder())
        val reset = ByteBuffer.wrap(run(fresh, 48000, 1, C.ENCODING_PCM_FLOAT, LevelingMode.STRONG, 47)).order(ByteOrder.nativeOrder())
        var settledEnergy = 0.0
        var resetEnergy = 0.0
        repeat(4800) {
            settledEnergy += settled.float.toDouble().let { it * it }
            resetEnergy += reset.float.toDouble().let { it * it }
        }
        assertTrue("seek must retain slow gain rather than restart warmup", settledEnergy > resetEnergy * 3)
    }

    @Test
    fun `steady callbacks including live switches use preallocated scratch and output storage`() {
        val bean =
            java.lang.management.ManagementFactory
                .getThreadMXBean() as com.sun.management.ThreadMXBean
        assertTrue(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        val state = VolumeLevelingState()
        val processor = configured(state)
        val input = buffer(Random(15).nextBytes(1024 * 4))
        repeat(1000) {
            input.rewind()
            processor.queueInput(input)
            val output = processor.output
            output.position(output.limit())
        }
        val thread = Thread.currentThread().id
        val counterStart = bean.getThreadAllocatedBytes(thread)
        val counterOverhead = bean.getThreadAllocatedBytes(thread) - counterStart
        val before = bean.getThreadAllocatedBytes(thread)
        repeat(1000) { index ->
            if (index == 100) state.mode = LevelingMode.LIGHT
            if (index == 400) state.mode = LevelingMode.STRONG
            if (index == 700) state.mode = LevelingMode.OFF
            input.rewind()
            processor.queueInput(input)
            val output = processor.output
            output.position(output.limit())
        }
        val allocated = bean.getThreadAllocatedBytes(thread) - before
        println("Wrapper allocated bytes across 1000 callbacks: $allocated; empty counter control: $counterOverhead")
        assertTrue("1000 audio callbacks allocated $allocated bytes", allocated < 1024)
    }

    @Test
    fun `reset permits a new configuration with no previous sample state`() {
        val processor = configured()
        feed(processor, Random(1).nextBytes(5000), ByteArrayOutputStream())
        processor.reset()
        assertFalse(processor.isActive)
        processor.configure(AudioFormat(8000, 1, C.ENCODING_PCM_16BIT))
        processor.flush()
        val fresh = byteArrayOf(0, -128, -1, 127)
        val result = ByteArrayOutputStream()
        feed(processor, fresh, result)
        finish(processor, result)
        assertArrayEquals(fresh, result.toByteArray())
    }
}
