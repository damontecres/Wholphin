package com.github.damontecres.wholphin.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.ByteBuffer

/**
 * The RPU rewrite itself is libdovi's and runs on a device; here it is a fake, and what is under
 * test is everything around it: which access units are touched at all, that the enhancement layer
 * goes and the rest stays contiguous, that a refused rewrite leaves the decoder's buffer exactly as
 * it was, and that the filter keeps to the [BitstreamFilter] contract on a direct buffer.
 */
class DolbyVisionProfile7FilterTest {
    @Test
    fun dropsTheEnhancementLayerAndRewritesTheRpu() {
        val filter = filterWith(FakeRewriter(profile = 7))
        val target = directBuffer(annexB(VPS, VCL, EL, EL, RPU))

        val size = filter.filter(target, 0, target.capacity())

        assertEquals(annexB(VPS, VCL, CONVERTED_RPU).toList(), target.bytes(0, size))
    }

    @Test
    fun convertsAnRpuWhichArrivedWithoutAnEnhancementLayer() {
        // A dual layer remux whose enhancement layer lives in Matroska block additions, which the
        // extractor discards: the RPU still needs its rewrite for the profile 8 decoder
        val filter = filterWith(FakeRewriter(profile = 7))
        val target = directBuffer(annexB(VCL, RPU))

        val size = filter.filter(target, 0, target.capacity())

        assertEquals(annexB(VCL, CONVERTED_RPU).toList(), target.bytes(0, size))
    }

    @Test
    fun worksFromAnOffsetAndLeavesTheBytesAroundTheAccessUnitAlone() {
        val filter = filterWith(FakeRewriter(profile = 7))
        val accessUnit = annexB(VCL, EL, RPU)
        val prefix = byteArrayOf(0x7A, 0x7B)
        val suffix = byteArrayOf(0x7C)
        val target = directBuffer(prefix + accessUnit + suffix)

        val size = filter.filter(target, prefix.size, accessUnit.size)

        assertEquals(annexB(VCL, CONVERTED_RPU).toList(), target.bytes(prefix.size, size))
        assertEquals(prefix.toList(), target.bytes(0, prefix.size))
        assertEquals(suffix.toList(), target.bytes(prefix.size + accessUnit.size, suffix.size))
    }

    @Test
    fun doesNotMoveThePositionOrLimitOfTheBuffer() {
        val filter = filterWith(FakeRewriter(profile = 7))
        val target = directBuffer(annexB(VCL, EL, RPU))
        target.position(3)
        target.limit(target.capacity() - 1)

        filter.filter(target, 0, target.capacity())

        assertEquals(3, target.position())
        assertEquals(target.capacity() - 1, target.limit())
    }

    @Test
    fun leavesAnAccessUnitWithoutAnRpuAlone() {
        val rewriter = FakeRewriter(profile = 7)
        val filter = filterWith(rewriter)
        val accessUnit = annexB(VPS, VCL, EL)
        val target = directBuffer(accessUnit)

        val size = filter.filter(target, 0, target.capacity())

        assertEquals(accessUnit.size, size)
        assertEquals(accessUnit.toList(), target.bytes(0, size))
        assertEquals(0, rewriter.conversions)
    }

    @Test
    fun leavesAnRpuOfAnotherProfileAlone() {
        val rewriter = FakeRewriter(profile = 8)
        val filter = filterWith(rewriter)
        val accessUnit = annexB(VCL, RPU)
        val target = directBuffer(accessUnit)

        val size = filter.filter(target, 0, target.capacity())

        assertEquals(accessUnit.size, size)
        assertEquals(accessUnit.toList(), target.bytes(0, size))
        assertEquals(0, rewriter.conversions)
    }

    @Test
    fun leavesAnAccessUnitAloneWhenTheRewriteThrows() {
        val filter = filterWith(FakeRewriter(profile = 7, throws = true))
        val accessUnit = annexB(VCL, EL, RPU)
        val target = directBuffer(accessUnit)

        val size = filter.filter(target, 0, target.capacity())

        assertEquals(accessUnit.size, size)
        assertEquals(accessUnit.toList(), target.bytes(0, size))
    }

    @Test
    fun leavesAnAccessUnitAloneWhenTheRewriteWouldNotFit() {
        // No enhancement layer to make room, and an RPU which comes out longer than it went in
        val filter = filterWith(FakeRewriter(profile = 7, converted = RPU + ByteArray(16) { 0x55 }))
        val accessUnit = annexB(VCL, RPU)
        val target = directBuffer(accessUnit)

        val size = filter.filter(target, 0, target.capacity())

        assertEquals(accessUnit.size, size)
        assertEquals(accessUnit.toList(), target.bytes(0, size))
    }

    @Test
    fun takesALongerRpuWhenTheEnhancementLayerMadeRoomForIt() {
        val longerRpu = RPU + byteArrayOf(0x55, 0x56)
        val filter = filterWith(FakeRewriter(profile = 7, converted = longerRpu))
        val target = directBuffer(annexB(VCL, EL, RPU))

        val size = filter.filter(target, 0, target.capacity())

        assertEquals(annexB(VCL, longerRpu).toList(), target.bytes(0, size))
    }

    @Test
    fun leavesAnAccessUnitAloneWhenTheRewriteReportsNonsense() {
        val filter = filterWith(FakeRewriter(profile = 7, reportedSize = -1))
        val accessUnit = annexB(VCL, EL, RPU)
        val target = directBuffer(accessUnit)

        val size = filter.filter(target, 0, target.capacity())

        assertEquals(accessUnit.size, size)
        assertEquals(accessUnit.toList(), target.bytes(0, size))
    }

    @Test
    fun leavesDataWhichIsNotAnnexBAlone() {
        val rewriter = FakeRewriter(profile = 7)
        val filter = filterWith(rewriter)
        val data = byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x00, 0x00, 0x01, 0x7C)
        val target = directBuffer(data)

        val size = filter.filter(target, 0, target.capacity())

        assertEquals(data.size, size)
        assertEquals(data.toList(), target.bytes(0, size))
        assertEquals(0, rewriter.conversions)
    }

    @Test
    fun walksThreeByteStartCodesToo() {
        val filter = filterWith(FakeRewriter(profile = 7))
        val accessUnit = SHORT_START_CODE + VCL + SHORT_START_CODE + EL + SHORT_START_CODE + RPU
        val target = directBuffer(accessUnit)

        val size = filter.filter(target, 0, target.capacity())

        assertEquals((SHORT_START_CODE + VCL + SHORT_START_CODE + CONVERTED_RPU).toList(), target.bytes(0, size))
    }

    @Test
    fun givesUpForGoodWhenTheNativeLibraryIsMissing() {
        var attempts = 0
        val filter =
            DolbyVisionProfile7Filter {
                attempts++
                throw UnsatisfiedLinkError("no libdovi_android for this ABI")
            }
        val accessUnit = annexB(VCL, EL, RPU)
        val target = directBuffer(accessUnit)

        val first = filter.filter(target, 0, target.capacity())
        val second = filter.filter(target, 0, target.capacity())

        assertEquals(accessUnit.size, first)
        assertEquals(accessUnit.size, second)
        assertEquals(accessUnit.toList(), target.bytes(0, accessUnit.size))
        assertEquals("the library should be tried once, not on every access unit", 1, attempts)
    }

    @Test
    fun handsTheRewriterTheAccessUnitWithoutTheEnhancementLayer() {
        val rewriter = FakeRewriter(profile = 7)
        val filter = filterWith(rewriter)
        val target = directBuffer(annexB(VPS, VCL, EL, RPU, EL))

        filter.filter(target, 0, target.capacity())

        assertEquals(annexB(VPS, VCL, RPU).toList(), rewriter.lastFrame)
    }

    /**
     * Stands in for libdovi. It finds the RPU NAL unit the way the native code does, by walking the
     * Annex B start codes, and swaps its payload for [converted], moving whatever follows.
     */
    private class FakeRewriter(
        private val profile: Int,
        private val converted: ByteArray = CONVERTED_RPU,
        private val throws: Boolean = false,
        private val reportedSize: Int? = null,
    ) : RpuRewriter {
        var conversions = 0
        var lastFrame: List<Byte> = emptyList()

        override fun profileOf(
            frame: ByteBuffer,
            size: Int,
        ): Int {
            assertFalse("libdovi reads the buffer's address, so the frame has to be direct", frame.hasArray())
            return profile
        }

        override fun convertToProfile8(
            frame: ByteBuffer,
            size: Int,
        ): Int {
            conversions++
            lastFrame = frame.bytes(0, size)
            if (throws) {
                throw IllegalArgumentException("Unable to parse Dolby Vision RPU")
            }
            val bytes = frame.bytes(0, size).toByteArray()
            val rpuStart = indexOfNal(bytes, RPU)
            val rpuEnd = nextStartCode(bytes, rpuStart + 1)
            val rewritten = bytes.copyOfRange(0, rpuStart) + converted + bytes.copyOfRange(rpuEnd, bytes.size)
            check(rewritten.size <= frame.capacity()) { "the fake outgrew the scratch buffer" }
            rewritten.forEachIndexed { index, byte -> frame.put(index, byte) }
            return reportedSize ?: rewritten.size
        }

        private fun indexOfNal(
            bytes: ByteArray,
            nal: ByteArray,
        ): Int {
            for (i in 0..bytes.size - nal.size) {
                if (bytes.copyOfRange(i, i + nal.size).contentEquals(nal)) {
                    return i
                }
            }
            throw AssertionError("RPU not found in the frame handed to the rewriter")
        }

        private fun nextStartCode(
            bytes: ByteArray,
            from: Int,
        ): Int {
            for (i in from..bytes.size - 3) {
                if (bytes[i] == 0.toByte() && bytes[i + 1] == 0.toByte() && bytes[i + 2] == 1.toByte()) {
                    return if (i > from && bytes[i - 1] == 0.toByte()) i - 1 else i
                }
            }
            return bytes.size
        }
    }

    companion object {
        private val START_CODE = byteArrayOf(0, 0, 0, 1)
        private val SHORT_START_CODE = byteArrayOf(0, 0, 1)

        /** NAL unit header for [type]: forbidden_zero_bit 0, nuh_layer_id 0, nuh_temporal_id_plus1 1. */
        private fun nal(
            type: Int,
            vararg payload: Byte,
        ): ByteArray = byteArrayOf((type shl 1).toByte(), 0x01) + payload

        private val VPS = nal(32, 0x0C, 0x01, 0x7F)
        private val VCL = nal(1, 0x11, 0x22, 0x33, 0x44)
        private val EL = nal(63, 0x0E, 0x0E, 0x0E, 0x0E, 0x0E, 0x0E)
        private val RPU = nal(62, 0x19, 0x07, 0x07, 0x07)
        private val CONVERTED_RPU = nal(62, 0x19, 0x08, 0x08)

        private fun annexB(vararg nals: ByteArray): ByteArray = nals.fold(byteArrayOf()) { acc, nal -> acc + START_CODE + nal }

        private fun filterWith(rewriter: RpuRewriter) = DolbyVisionProfile7Filter { rewriter }

        /** Like the decoder input buffer on a device: direct, with no backing array. */
        private fun directBuffer(data: ByteArray): ByteBuffer =
            ByteBuffer.allocateDirect(data.size).also { buffer ->
                data.forEachIndexed { index, byte -> buffer.put(index, byte) }
                assertFalse(buffer.hasArray())
            }

        private fun ByteBuffer.bytes(
            from: Int,
            count: Int,
        ): List<Byte> = (from until from + count).map { get(it) }
    }
}
