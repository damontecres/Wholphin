@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.util

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.suyashbelekar.exoplayerhdrutils.libdovi.LibDovi
import timber.log.Timber
import java.nio.Buffer
import java.nio.ByteBuffer

/**
 * Rewrites the Dolby Vision RPU of one access unit, in place. The production implementation is
 * libdovi, as packaged by ExoplayerHdrUtils; tests substitute their own.
 *
 * Both methods take a direct buffer whose access unit starts at index 0 and is [size] bytes long.
 * That is the shape the native code reads: it takes the buffer's address and capacity, not its
 * position and limit.
 */
interface RpuRewriter {
    /**
     * The Dolby Vision profile of the RPU (NAL unit type 62) in the access unit, or 0 when there is
     * none it can parse.
     */
    fun profileOf(
        frame: ByteBuffer,
        size: Int,
    ): Int

    /**
     * Rewrites the profile 7 RPU in the access unit into its profile 8.1 form and returns the new
     * size of the access unit. The bytes after the RPU move to stay contiguous, so the buffer's
     * capacity must leave room past [size] for an RPU which comes out longer than it went in.
     */
    fun convertToProfile8(
        frame: ByteBuffer,
        size: Int,
    ): Int
}

/** [RpuRewriter] on libdovi. Constructing it loads the native library, which throws a [LinkageError] on an ABI it does not ship for. */
class LibDoviRpuRewriter : RpuRewriter {
    private val libDovi = LibDovi()

    override fun profileOf(
        frame: ByteBuffer,
        size: Int,
    ): Int = libDovi.getFrameInfo(frame, size)?.doviProfile ?: 0

    override fun convertToProfile8(
        frame: ByteBuffer,
        size: Int,
    ): Int = libDovi.processHevcFrame(frame, size, DOVI_TRANSFORM_CONVERT_TO_PROFILE_8, false)

    private companion object {
        /** `doviTransform` value of `LibDovi.processHevcFrame` for the profile 7 → 8.1 rewrite. */
        const val DOVI_TRANSFORM_CONVERT_TO_PROFILE_8 = 1
    }
}

/**
 * Converts Dolby Vision profile 7 access units to profile 8.1 on their way into the decoder.
 *
 * Profile 7 carries its RPU in NAL unit type 62 and its enhancement layer in type 63, after the
 * picture's own NAL units. The conversion drops every type 63 unit and has libdovi rewrite the RPU
 * into the profile 8.1 form, which is what a profile 8 decoder switches the display into Dolby
 * Vision for. The enhancement layer goes because profile 8.1 is single layer by definition: a
 * decoder handed a dual layer stream under that name keeps it out of the Dolby Vision pipeline and
 * shows the HDR10 base layer, which is the very symptom this filter exists to fix.
 *
 * Every access unit is judged on its own. One without an RPU, or whose RPU is not profile 7, is
 * left exactly as it came, and so is one libdovi refuses. The rewrite happens in a scratch buffer
 * and is copied back only once it has succeeded and fits, so a refused access unit reaches the
 * decoder untouched rather than half rewritten.
 *
 * The codec string of the track has to say profile 8 for the decoder to be chosen at all; that is
 * [DolbyVisionProfile7ExtractorsFactory]'s job. An instance belongs to one video renderer and is
 * called from its playback thread only.
 */
class DolbyVisionProfile7Filter(
    private val newRewriter: () -> RpuRewriter = { LibDoviRpuRewriter() },
) : BitstreamFilter {
    private var rewriter: RpuRewriter? = null
    private var rewriterUnavailable = false

    /** The access unit as it arrived, for scanning: a heap array is much faster to walk than absolute gets on a direct buffer. */
    private var staging = ByteArray(0)

    /** The access unit without its enhancement layer, where libdovi rewrites the RPU. Direct, because the native code needs its address. */
    private var scratch: ByteBuffer? = null

    private var convertedCount = 0
    private var passedThroughCount = 0
    private var refusedCount = 0

    override fun filter(
        data: ByteBuffer,
        offset: Int,
        size: Int,
    ): Int {
        if (!startsWithStartCode(data, offset, size)) {
            return size
        }
        val rewriter = rewriter() ?: return size

        val staging = staging(size)
        window(data, offset, size).get(staging, 0, size)
        if (!carriesRpu(staging, size)) {
            passedThroughCount++
            return size
        }

        val scratch = scratch(size + RPU_GROWTH_HEADROOM_BYTES)
        val kept = copyWithoutEnhancementLayer(staging, size, scratch)
        val converted =
            try {
                if (rewriter.profileOf(scratch, kept) != DOLBY_VISION_PROFILE_7) {
                    passedThroughCount++
                    return size
                }
                rewriter.convertToProfile8(scratch, kept)
            } catch (e: RuntimeException) {
                refuse("the RPU rewrite threw", e)
                return size
            }
        if (converted <= 0 || converted > size) {
            refuse("the RPU rewrite returned $converted bytes for a $size byte access unit", null)
            return size
        }

        window(data, offset, converted).put(window(scratch, 0, converted))
        convertedCount++
        return converted
    }

    private fun rewriter(): RpuRewriter? {
        if (rewriterUnavailable) {
            return null
        }
        return rewriter ?: try {
            newRewriter().also { rewriter = it }
        } catch (e: LinkageError) {
            // No libdovi for this ABI. Playback goes on exactly as it did without the filter.
            Timber.w(e, "libdovi is not available on this device, Dolby Vision profile 7 is left as it is")
            rewriterUnavailable = true
            null
        }
    }

    private fun refuse(
        reason: String,
        e: Throwable?,
    ) {
        if (refusedCount == 0) {
            Timber.w(e, "Leaving a Dolby Vision profile 7 access unit as it is: %s", reason)
        }
        refusedCount++
    }

    private fun staging(size: Int): ByteArray {
        if (staging.size < size) {
            staging = ByteArray(maxOf(size, INITIAL_CAPACITY_BYTES))
        }
        return staging
    }

    private fun scratch(capacity: Int): ByteBuffer {
        val current = scratch
        val buffer =
            if (current == null || current.capacity() < capacity) {
                ByteBuffer.allocateDirect(maxOf(capacity, INITIAL_CAPACITY_BYTES)).also { scratch = it }
            } else {
                current
            }
        (buffer as Buffer).clear()
        return buffer
    }

    override fun toString(): String =
        "Dolby Vision profile 7 → 8.1 ($convertedCount access units converted, " +
            "$passedThroughCount passed through, $refusedCount refused)"

    private companion object {
        const val DOLBY_VISION_PROFILE_7 = 7

        /** HEVC NAL unit type carrying the Dolby Vision RPU. */
        const val NAL_UNIT_TYPE_RPU = 62

        /** HEVC NAL unit type carrying the Dolby Vision enhancement layer picture. */
        const val NAL_UNIT_TYPE_ENHANCEMENT_LAYER = 63

        /** Big enough for a UHD access unit without regrowing on every early frame. */
        const val INITIAL_CAPACITY_BYTES = 512 * 1024

        /**
         * Room past the access unit for an RPU which comes out of the rewrite longer than it went
         * in. Dropping the enhancement layer usually frees far more, but a profile 7 stream whose
         * enhancement layer the extractor never delivered has none to free.
         */
        const val RPU_GROWTH_HEADROOM_BYTES = 4 * 1024

        private fun startsWithStartCode(
            data: ByteBuffer,
            offset: Int,
            size: Int,
        ): Boolean {
            if (size < 4) {
                return false
            }
            return data.get(offset) == 0.toByte() &&
                data.get(offset + 1) == 0.toByte() &&
                (
                    data.get(offset + 2) == 1.toByte() ||
                        (data.get(offset + 2) == 0.toByte() && data.get(offset + 3) == 1.toByte())
                )
        }

        /** Whether the Annex B access unit in `[0, length)` of [source] carries an RPU. */
        private fun carriesRpu(
            source: ByteArray,
            length: Int,
        ): Boolean {
            var unit = 0
            while (unit < length) {
                val payload = unit + startCodeLength(source, unit)
                if (payload >= length) {
                    return false
                }
                if (nalUnitType(source[payload]) == NAL_UNIT_TYPE_RPU) {
                    return true
                }
                unit = nextStartCode(source, payload, length)
            }
            return false
        }

        /**
         * Copies the Annex B access unit in `[0, length)` of [source] into [target] from index 0,
         * leaving out every enhancement layer NAL unit, and returns the number of bytes copied.
         */
        fun copyWithoutEnhancementLayer(
            source: ByteArray,
            length: Int,
            target: ByteBuffer,
        ): Int {
            var unit = 0
            var written = 0
            while (unit < length) {
                val payload = unit + startCodeLength(source, unit)
                if (payload >= length) {
                    // A start code with nothing behind it. Not something to interpret; kept as it came.
                    target.put(source, unit, length - unit)
                    written += length - unit
                    break
                }
                val next = nextStartCode(source, payload, length)
                if (nalUnitType(source[payload]) != NAL_UNIT_TYPE_ENHANCEMENT_LAYER) {
                    target.put(source, unit, next - unit)
                    written += next - unit
                }
                unit = next
            }
            return written
        }

        /**
         * A view of `[offset, offset + length)` of [data] with a position and limit of its own, so
         * that bulk copies leave the buffer's untouched. The position and limit calls go through
         * [Buffer]: the [ByteBuffer] overrides which return a ByteBuffer are API 34, and `minSdk` is 23.
         */
        private fun window(
            data: ByteBuffer,
            offset: Int,
            length: Int,
        ): ByteBuffer {
            val view = data.duplicate()
            val buffer: Buffer = view
            buffer.clear()
            buffer.position(offset)
            buffer.limit(offset + length)
            return view
        }

        /** nal_unit_type is the 6 bits after forbidden_zero_bit, H.265 section 7.3.1.2. */
        private fun nalUnitType(header: Byte): Int = (header.toInt() shr 1) and 0x3F

        /** Length of the start code at [at], which the caller knows is one: 3 or 4 bytes. */
        private fun startCodeLength(
            data: ByteArray,
            at: Int,
        ): Int = if (data[at + 2] == 1.toByte()) 3 else 4

        /**
         * Index of the next Annex B start code at or after [from], or [limit] if there is none. A
         * fourth leading zero byte counts as part of the start code, which is the form media3's
         * extractors emit; three byte codes are walked all the same.
         */
        private fun nextStartCode(
            data: ByteArray,
            from: Int,
            limit: Int,
        ): Int {
            var i = from
            while (i + 2 < limit) {
                if (data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 1.toByte()) {
                    return if (i > from && data[i - 1] == 0.toByte()) i - 1 else i
                }
                i++
            }
            return limit
        }
    }
}
