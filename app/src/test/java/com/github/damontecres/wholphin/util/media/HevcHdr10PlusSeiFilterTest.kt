package com.github.damontecres.wholphin.util.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class HevcHdr10PlusSeiFilterTest {
    @Test
    fun strip_exactHdr10PlusMessage_removesSeiNalAndPreservesAdjacentRpu() {
        val vps = nal(32, byteArrayOf(0x01, 0x02))
        val hdr10Plus = seiNal(39, listOf(message(4, hdr10PlusPayload())))
        val rpu = nal(62, byteArrayOf(0x19, 0x08, 0x10))
        val sample = concat(vps, hdr10Plus, rpu)

        val filtered = filter(sample)

        assertEquals(HevcHdr10PlusSeiFilter.Result.FILTERED, filtered.result)
        assertArrayEquals(concat(vps, rpu), filtered.bytes)
    }

    @Test
    fun strip_versionOne_removesHdr10PlusMessage() {
        val target = seiNal(39, listOf(message(4, hdr10PlusPayload(version = 1))))

        val filtered = filter(target)

        assertEquals(HevcHdr10PlusSeiFilter.Result.FILTERED, filtered.result)
        assertArrayEquals(byteArrayOf(), filtered.bytes)
    }

    @Test
    fun strip_mismatchedSignatureAndVersionTwo_leavesSampleUnchanged() {
        val wrongProvider = hdr10PlusPayload().also { it[2] = 0x3D }
        val sample =
            seiNal(
                39,
                listOf(
                    message(4, wrongProvider),
                    message(4, hdr10PlusPayload(version = 2)),
                ),
            )

        val filtered = filter(sample)

        assertEquals(HevcHdr10PlusSeiFilter.Result.UNCHANGED, filtered.result)
        assertArrayEquals(sample, filtered.bytes)
    }

    @Test
    fun strip_mixedMessages_removesOnlyAllHdr10PlusMessages() {
        val unregistered = message(5, byteArrayOf(0x11, 0x22, 0x33))
        val otherT35 = message(4, byteArrayOf(0xB5.toByte(), 0x00, 0x31, 0x00, 0x01, 0x04, 0x00))
        val hdr10Plus0 = message(4, hdr10PlusPayload(version = 0, tail = byteArrayOf(0x40)))
        val hdr10Plus1 = message(4, hdr10PlusPayload(version = 1, tail = byteArrayOf(0x41)))
        val staticMasteringDisplay = message(137, byteArrayOf(0x01, 0x02, 0x03, 0x04))
        val sample = seiNal(39, listOf(unregistered, hdr10Plus0, otherT35, hdr10Plus1, staticMasteringDisplay))
        val expected = seiNal(39, listOf(unregistered, otherT35, staticMasteringDisplay))

        val filtered = filter(sample)

        assertEquals(HevcHdr10PlusSeiFilter.Result.FILTERED, filtered.result)
        assertArrayEquals(expected, filtered.bytes)
    }

    @Test
    fun strip_suffixSei_removesHdr10PlusAndPreservesVcl() {
        val vcl = nal(1, byteArrayOf(0x55, 0x66, 0x77))
        val suffix =
            seiNal(
                40,
                listOf(
                    message(144, byteArrayOf(0x00, 0x01)),
                    message(4, hdr10PlusPayload()),
                ),
            )
        val expectedSuffix = seiNal(40, listOf(message(144, byteArrayOf(0x00, 0x01))))

        val filtered = filter(concat(vcl, suffix))

        assertEquals(HevcHdr10PlusSeiFilter.Result.FILTERED, filtered.result)
        assertArrayEquals(concat(vcl, expectedSuffix), filtered.bytes)
    }

    @Test
    fun strip_rebuildsEmulationPreventionBytes() {
        val unrelatedPayload = byteArrayOf(0x10, 0x00, 0x00, 0x01, 0x00, 0x00, 0x02, 0x20)
        val unrelated = message(5, unrelatedPayload)
        val target = message(4, hdr10PlusPayload(tail = byteArrayOf(0x00, 0x00, 0x01)))
        val sample = seiNal(39, listOf(unrelated, target))
        val expected = seiNal(39, listOf(unrelated))

        val filtered = filter(sample)

        assertEquals(HevcHdr10PlusSeiFilter.Result.FILTERED, filtered.result)
        assertArrayEquals(expected, filtered.bytes)
    }

    @Test
    fun strip_extendedPayloadTypeAndSizes_preservesUnrelatedMessage() {
        val largeUnrelatedPayload = ByteArray(258) { (it and 0x7F).toByte() }
        largeUnrelatedPayload[20] = 0
        largeUnrelatedPayload[21] = 0
        largeUnrelatedPayload[22] = 1
        val unrelated = message(259, largeUnrelatedPayload)
        val largeTargetPayload = hdr10PlusPayload(tail = ByteArray(260) { 0x5A })
        val target = message(4, largeTargetPayload)
        val sample = seiNal(39, listOf(unrelated, target), startCodeSize = 3)
        val expected = seiNal(39, listOf(unrelated), startCodeSize = 3)

        val filtered = filter(sample)

        assertEquals(HevcHdr10PlusSeiFilter.Result.FILTERED, filtered.result)
        assertArrayEquals(expected, filtered.bytes)
    }

    @Test
    fun strip_threeAndFourByteStartCodes_filtersBoth() {
        val prefix =
            seiNal(
                39,
                listOf(message(5, byteArrayOf(0x01)), message(4, hdr10PlusPayload())),
                startCodeSize = 3,
            )
        val suffix = seiNal(40, listOf(message(4, hdr10PlusPayload())), startCodeSize = 4)
        val expectedPrefix = seiNal(39, listOf(message(5, byteArrayOf(0x01))), startCodeSize = 3)

        val filtered = filter(concat(prefix, suffix))

        assertEquals(HevcHdr10PlusSeiFilter.Result.FILTERED, filtered.result)
        assertArrayEquals(expectedPrefix, filtered.bytes)
    }

    @Test
    fun strip_preservesVpsSpsPpsVclStaticHdrAndRpu() {
        val vps = nal(32, byteArrayOf(0x01))
        val sps = nal(33, byteArrayOf(0x02))
        val pps = nal(34, byteArrayOf(0x03))
        val staticHdr = message(137, byteArrayOf(0x00, 0x01, 0x02, 0x03))
        val sei = seiNal(39, listOf(staticHdr, message(4, hdr10PlusPayload())))
        val expectedSei = seiNal(39, listOf(staticHdr))
        val vcl = nal(19, byteArrayOf(0x04, 0x05, 0x06))
        val rpu = nal(62, byteArrayOf(0x07, 0x08, 0x09))

        val filtered = filter(concat(vps, sps, pps, sei, vcl, rpu))

        assertEquals(HevcHdr10PlusSeiFilter.Result.FILTERED, filtered.result)
        assertArrayEquals(concat(vps, sps, pps, expectedSei, vcl, rpu), filtered.bytes)
    }

    @Test
    fun strip_malformedAfterValidMatch_failsOpenWithWholeSampleUnchanged() {
        val validTarget = seiNal(39, listOf(message(4, hdr10PlusPayload())))
        val truncatedRbsp = byteArrayOf(0x04, 0x14, 0xB5.toByte(), 0x00, 0x3C)
        val malformed = nalFromEbsp(39, truncatedRbsp)
        val sample = concat(validTarget, malformed)

        val filtered = filter(sample)

        assertEquals(HevcHdr10PlusSeiFilter.Result.MALFORMED, filtered.result)
        assertArrayEquals(sample, filtered.bytes)
    }

    @Test
    fun strip_malformedEmulationPrevention_failsOpen() {
        val malformedEbsp = byteArrayOf(0x05, 0x04, 0x00, 0x00, 0x03, 0x04, 0x80.toByte())
        val sample = nalFromEbsp(39, malformedEbsp)

        val filtered = filter(sample)

        assertEquals(HevcHdr10PlusSeiFilter.Result.MALFORMED, filtered.result)
        assertArrayEquals(sample, filtered.bytes)
    }

    @Test
    fun strip_noMatchDirectBuffer_preservesContentPositionAndLimit() {
        val sample =
            concat(
                nal(32, byteArrayOf(0x01)),
                seiNal(39, listOf(message(4, byteArrayOf(0xB5.toByte(), 0, 0x3C, 0, 2, 4, 0)))),
            )
        val padding = 5
        val buffer = ByteBuffer.allocateDirect(sample.size + padding + 3)
        var index = 0
        while (index < sample.size) {
            buffer.put(padding + index, sample[index])
            index++
        }
        buffer.position(padding)
        buffer.limit(padding + sample.size)

        val result = HevcHdr10PlusSeiFilter.strip(buffer)

        assertEquals(HevcHdr10PlusSeiFilter.Result.UNCHANGED, result)
        assertEquals(padding, buffer.position())
        assertEquals(padding + sample.size, buffer.limit())
        assertArrayEquals(sample, bytesBetweenPositionAndLimit(buffer))
    }

    @Test
    fun strip_matchingDirectBuffer_compactsAtPositionAndUpdatesLimit() {
        val kept = nal(62, byteArrayOf(0x01, 0x02, 0x03))
        val sample = concat(seiNal(39, listOf(message(4, hdr10PlusPayload()))), kept)
        val padding = 7
        val buffer = ByteBuffer.allocateDirect(sample.size + padding + 4)
        var index = 0
        while (index < sample.size) {
            buffer.put(padding + index, sample[index])
            index++
        }
        buffer.position(padding)
        buffer.limit(padding + sample.size)

        val result = HevcHdr10PlusSeiFilter.strip(buffer)

        assertEquals(HevcHdr10PlusSeiFilter.Result.FILTERED, result)
        assertEquals(padding, buffer.position())
        assertEquals(padding + kept.size, buffer.limit())
        assertArrayEquals(kept, bytesBetweenPositionAndLimit(buffer))
    }

    private fun filter(sample: ByteArray): FilteredSample {
        val buffer = ByteBuffer.wrap(sample.copyOf())
        val result = HevcHdr10PlusSeiFilter.strip(buffer)
        return FilteredSample(result, bytesBetweenPositionAndLimit(buffer))
    }

    private fun bytesBetweenPositionAndLimit(buffer: ByteBuffer): ByteArray {
        val bytes = ByteArray(buffer.remaining())
        var index = 0
        while (index < bytes.size) {
            bytes[index] = buffer.get(buffer.position() + index)
            index++
        }
        return bytes
    }

    private fun seiNal(
        type: Int,
        messages: List<ByteArray>,
        startCodeSize: Int = 4,
    ): ByteArray = nal(type, concat(*(messages + byteArrayOf(0x80.toByte())).toTypedArray()), startCodeSize)

    private fun nal(
        type: Int,
        rbsp: ByteArray,
        startCodeSize: Int = 4,
    ): ByteArray = nalFromEbsp(type, escapeRbsp(rbsp), startCodeSize)

    private fun nalFromEbsp(
        type: Int,
        ebsp: ByteArray,
        startCodeSize: Int = 4,
    ): ByteArray {
        val startCode = if (startCodeSize == 3) byteArrayOf(0, 0, 1) else byteArrayOf(0, 0, 0, 1)
        return concat(startCode, byteArrayOf((type shl 1).toByte(), 0x01), ebsp)
    }

    private fun message(
        type: Int,
        payload: ByteArray,
    ): ByteArray = concat(extendedValue(type), extendedValue(payload.size), payload)

    private fun extendedValue(value: Int): ByteArray {
        var remaining = value
        val bytes = ArrayList<Byte>()
        while (remaining >= 0xFF) {
            bytes.add(0xFF.toByte())
            remaining -= 0xFF
        }
        bytes.add(remaining.toByte())
        return bytes.toByteArray()
    }

    private fun hdr10PlusPayload(
        version: Int = 0,
        tail: ByteArray = byteArrayOf(0x10, 0x20),
    ): ByteArray =
        concat(
            byteArrayOf(
                0xB5.toByte(),
                0x00,
                0x3C,
                0x00,
                0x01,
                0x04,
                version.toByte(),
            ),
            tail,
        )

    private fun escapeRbsp(rbsp: ByteArray): ByteArray {
        val output = ArrayList<Byte>(rbsp.size)
        var consecutiveZeros = 0
        for (byte in rbsp) {
            val value = byte.toInt() and 0xFF
            if (consecutiveZeros >= 2 && value <= 3) {
                output.add(3)
                consecutiveZeros = 0
            }
            output.add(byte)
            consecutiveZeros = if (value == 0) consecutiveZeros + 1 else 0
        }
        return output.toByteArray()
    }

    private fun concat(vararg arrays: ByteArray): ByteArray {
        val output = ByteArray(arrays.sumOf { it.size })
        var offset = 0
        for (array in arrays) {
            array.copyInto(output, offset)
            offset += array.size
        }
        return output
    }

    private data class FilteredSample(
        val result: HevcHdr10PlusSeiFilter.Result,
        val bytes: ByteArray,
    )
}
