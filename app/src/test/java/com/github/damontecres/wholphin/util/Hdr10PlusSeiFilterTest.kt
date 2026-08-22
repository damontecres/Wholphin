package com.github.damontecres.wholphin.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class Hdr10PlusSeiFilterTest {
    @Test
    fun masksSoleHdr10PlusMessage() {
        val accessUnit = annexB(seiNal(seiMessage(4, HDR10_PLUS_PAYLOAD)), vclNal())
        // Payload type byte follows the start code and the 2 byte NAL unit header
        val payloadTypeIndex = START_CODE.size + 2

        val (masked, result) = filter(accessUnit)

        assertEquals(1, masked)
        assertArrayEquals(maskTypeByte(accessUnit, payloadTypeIndex), result)
    }

    @Test
    fun masksWithThreeByteStartCodes() {
        val accessUnit =
            SHORT_START_CODE + seiNal(seiMessage(4, HDR10_PLUS_PAYLOAD)) + SHORT_START_CODE + vclNal()
        val payloadTypeIndex = SHORT_START_CODE.size + 2

        val (masked, result) = filter(accessUnit)

        assertEquals(1, masked)
        assertArrayEquals(maskTypeByte(accessUnit, payloadTypeIndex), result)
    }

    @Test
    fun masksOnlyHdr10PlusInMultiMessageNal() {
        val masteringDisplay = seiMessage(137, MASTERING_DISPLAY_PAYLOAD)
        val contentLightLevel = seiMessage(144, CONTENT_LIGHT_LEVEL_PAYLOAD)
        val accessUnit =
            annexB(
                seiNal(masteringDisplay, seiMessage(4, HDR10_PLUS_PAYLOAD), contentLightLevel),
                vclNal(),
            )
        val payloadTypeIndex = START_CODE.size + 2 + masteringDisplay.size

        val (masked, result) = filter(accessUnit)

        assertEquals(1, masked)
        assertArrayEquals(maskTypeByte(accessUnit, payloadTypeIndex), result)
    }

    @Test
    fun skipsEmulationPreventionBytesInPrecedingPayload() {
        // The escaped form of this payload is longer than its payload size, so naive byte
        // counting would misparse the HDR10+ message that follows it
        val zeroHeavyMessage = seiMessage(137, byteArrayOf(0, 0, 0, 0, 1))
        val accessUnit =
            annexB(seiNal(zeroHeavyMessage, seiMessage(4, HDR10_PLUS_PAYLOAD)), vclNal())
        val payloadTypeIndex = START_CODE.size + 2 + escape(zeroHeavyMessage).size

        val (masked, result) = filter(accessUnit)

        assertEquals(1, masked)
        assertArrayEquals(maskTypeByte(accessUnit, payloadTypeIndex), result)
    }

    @Test
    fun handlesMultiBytePayloadType() {
        val bigTypeMessage = seiMessage(300, byteArrayOf(0x42))
        val accessUnit =
            annexB(seiNal(bigTypeMessage, seiMessage(4, HDR10_PLUS_PAYLOAD)), vclNal())
        val payloadTypeIndex = START_CODE.size + 2 + bigTypeMessage.size

        val (masked, result) = filter(accessUnit)

        assertEquals(1, masked)
        assertArrayEquals(maskTypeByte(accessUnit, payloadTypeIndex), result)
    }

    @Test
    fun ignoresOtherItuTT35Messages() {
        val accessUnit = annexB(seiNal(seiMessage(4, CEA_708_PAYLOAD)), vclNal())

        val (masked, result) = filter(accessUnit)

        assertEquals(0, masked)
        assertArrayEquals(accessUnit, result)
    }

    @Test
    fun ignoresSeiNalUnitsAfterTheVclNalUnit() {
        val accessUnit = annexB(vclNal(), seiNal(seiMessage(4, HDR10_PLUS_PAYLOAD)))

        val (masked, result) = filter(accessUnit)

        assertEquals(0, masked)
        assertArrayEquals(accessUnit, result)
    }

    @Test
    fun masksAcrossMultipleNonVclNalUnits() {
        val accessUnit =
            annexB(
                audNal(),
                seiNal(seiMessage(137, MASTERING_DISPLAY_PAYLOAD)),
                seiNal(seiMessage(4, HDR10_PLUS_PAYLOAD)),
                vclNal(),
            )
        val payloadTypeIndex =
            START_CODE.size + audNal().size +
                START_CODE.size + seiNal(seiMessage(137, MASTERING_DISPLAY_PAYLOAD)).size +
                START_CODE.size + 2

        val (masked, result) = filter(accessUnit)

        assertEquals(1, masked)
        assertArrayEquals(maskTypeByte(accessUnit, payloadTypeIndex), result)
    }

    @Test
    fun leavesLengthPrefixedSamplesUntouched() {
        val nal = seiNal(seiMessage(4, HDR10_PLUS_PAYLOAD))
        val accessUnit =
            byteArrayOf(
                (nal.size ushr 24).toByte(),
                (nal.size ushr 16).toByte(),
                (nal.size ushr 8).toByte(),
                nal.size.toByte(),
            ) + nal

        val (masked, result) = filter(accessUnit)

        assertEquals(0, masked)
        assertArrayEquals(accessUnit, result)
    }

    @Test
    fun leavesTruncatedSeiNalUnitUntouched() {
        // Payload size claims more bytes than the NAL unit contains
        val brokenNal = byteArrayOf(0x4E, 0x01, 0x04, 0x30, 0xB5.toByte(), 0x00, 0x3C)
        val accessUnit = annexB(brokenNal, vclNal())

        val (masked, result) = filter(accessUnit)

        assertEquals(0, masked)
        assertArrayEquals(accessUnit, result)
    }

    @Test
    fun leavesShortItuTT35MessageUntouched() {
        val accessUnit = annexB(seiNal(seiMessage(4, byteArrayOf(0xB5.toByte(), 0x00, 0x3C))), vclNal())

        val (masked, result) = filter(accessUnit)

        assertEquals(0, masked)
        assertArrayEquals(accessUnit, result)
    }

    @Test
    fun respectsOffsetAndSize() {
        val accessUnit = annexB(seiNal(seiMessage(4, HDR10_PLUS_PAYLOAD)), vclNal())
        val prefix = byteArrayOf(0x55, 0x66)
        val data = prefix + accessUnit
        val payloadTypeIndex = prefix.size + START_CODE.size + 2

        val buffer = ByteBuffer.wrap(data.copyOf())
        val masked = Hdr10PlusSeiFilter.maskHdr10PlusSeiMessages(buffer, prefix.size, accessUnit.size)

        assertEquals(1, masked)
        assertArrayEquals(maskTypeByte(data, payloadTypeIndex), buffer.array())
    }

    @Test
    fun masksMultipleHdr10PlusMessages() {
        val firstMessage = seiMessage(4, HDR10_PLUS_PAYLOAD)
        val accessUnit =
            annexB(seiNal(firstMessage, seiMessage(4, HDR10_PLUS_PAYLOAD)), vclNal())
        val expected =
            maskTypeByte(
                maskTypeByte(accessUnit, START_CODE.size + 2),
                START_CODE.size + 2 + firstMessage.size,
            )

        val (masked, result) = filter(accessUnit)

        assertEquals(2, masked)
        assertArrayEquals(expected, result)
    }

    companion object {
        private val START_CODE = byteArrayOf(0, 0, 0, 1)
        private val SHORT_START_CODE = byteArrayOf(0, 0, 1)

        /** ITU-T T.35 header for SMPTE ST 2094-40 plus a few opaque payload bytes */
        private val HDR10_PLUS_PAYLOAD =
            byteArrayOf(0xB5.toByte(), 0x00, 0x3C, 0x00, 0x01, 0x04, 0x01, 0x12, 0x34)

        /** ITU-T T.35 with the ATSC provider code (0x0031), as used for CEA-708 captions */
        private val CEA_708_PAYLOAD =
            byteArrayOf(0xB5.toByte(), 0x00, 0x31, 0x47, 0x41, 0x39, 0x34, 0x03)

        private val MASTERING_DISPLAY_PAYLOAD = ByteArray(24) { (it + 1).toByte() }

        private val CONTENT_LIGHT_LEVEL_PAYLOAD = byteArrayOf(0x03, 0xE8.toByte(), 0x01, 0xF4.toByte())

        /** Encodes an SEI message with the ff-extended payload type and size coding of D.2.1 */
        private fun seiMessage(
            payloadType: Int,
            payload: ByteArray,
        ): ByteArray {
            val out = mutableListOf<Byte>()
            var type = payloadType
            while (type >= 255) {
                out.add(0xFF.toByte())
                type -= 255
            }
            out.add(type.toByte())
            var size = payload.size
            while (size >= 255) {
                out.add(0xFF.toByte())
                size -= 255
            }
            out.add(size.toByte())
            payload.forEach { out.add(it) }
            return out.toByteArray()
        }

        /** Inserts emulation prevention bytes as required by H.265 section 7.4.2 */
        private fun escape(rbsp: ByteArray): ByteArray {
            val out = mutableListOf<Byte>()
            var zeroRun = 0
            for (b in rbsp) {
                if (zeroRun >= 2 && (b.toInt() and 0xFF) <= 0x03) {
                    out.add(0x03)
                    zeroRun = 0
                }
                out.add(b)
                zeroRun = if (b == 0.toByte()) zeroRun + 1 else 0
            }
            return out.toByteArray()
        }

        /** Builds a prefix SEI NAL unit (type 39) containing the given messages */
        private fun seiNal(vararg messages: ByteArray): ByteArray {
            val rbsp = messages.fold(byteArrayOf()) { acc, message -> acc + message } + 0x80.toByte()
            return byteArrayOf(0x4E, 0x01) + escape(rbsp)
        }

        /** A minimal VCL NAL unit (type 1, TRAIL_R) */
        private fun vclNal(): ByteArray = byteArrayOf(0x02, 0x01, 0x11, 0x22, 0x33)

        /** A minimal access unit delimiter NAL unit (type 35) */
        private fun audNal(): ByteArray = byteArrayOf(0x46, 0x01, 0x50)

        private fun annexB(vararg nals: ByteArray): ByteArray = nals.fold(byteArrayOf()) { acc, nal -> acc + START_CODE + nal }

        private fun maskTypeByte(
            data: ByteArray,
            index: Int,
        ): ByteArray = data.copyOf().also { it[index] = 0xFE.toByte() }

        private fun filter(data: ByteArray): Pair<Int, ByteArray> {
            val buffer = ByteBuffer.wrap(data.copyOf())
            val masked = Hdr10PlusSeiFilter.maskHdr10PlusSeiMessages(buffer, 0, data.size)
            return masked to buffer.array()
        }
    }
}
