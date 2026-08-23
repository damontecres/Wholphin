package com.github.damontecres.wholphin.util.media

import java.nio.ByteBuffer

/**
 * Neutralizes HDR10+ metadata in HEVC Annex-B samples while leaving all other NAL units and SEI
 * messages unchanged.
 *
 * Matching registered ITU-T T.35 messages are changed from payload type 4 to the reserved payload
 * type 254. Decoders consequently ignore the message, while the access unit keeps exactly the same
 * size and no sample data needs to be copied. The input is fully validated before it is modified,
 * making malformed input fail open. Neither the matching nor no-match path allocates sample data.
 */
internal object HevcHdr10PlusSeiFilter {
    enum class Result {
        FILTERED,
        UNCHANGED,
        MALFORMED,
        READ_ONLY,
    }

    private enum class SeiNalAnalysis {
        UNCHANGED,
        HDR10_PLUS,
        MALFORMED,
    }

    private const val NAL_PREFIX_SEI = 39
    private const val NAL_SUFFIX_SEI = 40
    private const val SEI_USER_DATA_REGISTERED_ITU_T_T35 = 4

    private const val RBSP_EOF = -1L
    private const val RBSP_MALFORMED = -2L

    /**
     * Filters the bytes between [ByteBuffer.position] and [ByteBuffer.limit].
     *
     * The position and limit are preserved for every result. On success, only the payload type byte
     * of each validated HDR10+ SEI message is changed in place.
     */
    fun strip(buffer: ByteBuffer): Result {
        val originalPosition = buffer.position()
        val originalLimit = buffer.limit()
        var startCode = findStartCode(buffer, originalPosition, originalLimit)
        if (startCode < 0) return Result.UNCHANGED

        var containsHdr10Plus = false
        while (startCode >= 0) {
            val startCodeOneOffset = unpackStartCodeOneOffset(startCode)
            val nalHeaderOffset = startCodeOneOffset + 1
            if (nalHeaderOffset + 2 > originalLimit) return Result.MALFORMED

            val nextStartCode = findStartCode(buffer, nalHeaderOffset + 2, originalLimit)
            val nalEnd =
                if (nextStartCode >= 0) {
                    unpackStartCodeOffset(nextStartCode)
                } else {
                    originalLimit
                }
            val nalType = (buffer.get(nalHeaderOffset).toInt() and 0x7E) ushr 1
            if (nalType == NAL_PREFIX_SEI || nalType == NAL_SUFFIX_SEI) {
                when (scanSeiNal(buffer, nalHeaderOffset + 2, nalEnd, neutralize = false)) {
                    SeiNalAnalysis.MALFORMED -> return Result.MALFORMED
                    SeiNalAnalysis.HDR10_PLUS -> containsHdr10Plus = true

                    SeiNalAnalysis.UNCHANGED -> Unit
                }
            }
            startCode = nextStartCode
        }

        if (!containsHdr10Plus) return Result.UNCHANGED
        if (buffer.isReadOnly) return Result.READ_ONLY

        // A second pass keeps the validation phase transactional without retaining per-message
        // offsets. Decoder input buffers have single-threaded ownership, so this pass observes the
        // same bytes that were validated above.
        startCode = findStartCode(buffer, originalPosition, originalLimit)
        while (startCode >= 0) {
            val startCodeOneOffset = unpackStartCodeOneOffset(startCode)
            val nalHeaderOffset = startCodeOneOffset + 1
            val payloadOffset = nalHeaderOffset + 2
            val nextStartCode = findStartCode(buffer, payloadOffset, originalLimit)
            val nalEnd =
                if (nextStartCode >= 0) {
                    unpackStartCodeOffset(nextStartCode)
                } else {
                    originalLimit
                }
            val nalType = (buffer.get(nalHeaderOffset).toInt() and 0x7E) ushr 1
            if (nalType == NAL_PREFIX_SEI || nalType == NAL_SUFFIX_SEI) {
                // This cannot fail after the validation pass unless another thread mutates a codec
                // buffer it does not own. Keep the defensive result for API consistency.
                if (
                    scanSeiNal(buffer, payloadOffset, nalEnd, neutralize = true) ==
                    SeiNalAnalysis.MALFORMED
                ) {
                    return Result.MALFORMED
                }
            }
            startCode = nextStartCode
        }
        return Result.FILTERED
    }

    private fun scanSeiNal(
        buffer: ByteBuffer,
        rbspStart: Int,
        rbspEnd: Int,
        neutralize: Boolean,
    ): SeiNalAnalysis {
        var cursor = rbspStart
        var foundHdr10Plus = false

        while (true) {
            when (trailingBitsStatus(buffer, cursor, rbspStart, rbspEnd)) {
                1 -> return if (foundHdr10Plus) SeiNalAnalysis.HDR10_PLUS else SeiNalAnalysis.UNCHANGED

                -1 -> return SeiNalAnalysis.MALFORMED
            }

            val payloadTypeOffset = cursor
            var payloadType = 0
            var value: Int
            do {
                val read = readRbspByte(buffer, cursor, rbspStart, rbspEnd)
                if (read < 0) return SeiNalAnalysis.MALFORMED
                cursor = unpackReadOffset(read)
                value = unpackReadValue(read)
                if (payloadType > Int.MAX_VALUE - value) return SeiNalAnalysis.MALFORMED
                payloadType += value
            } while (value == 0xFF)

            var payloadSize = 0
            do {
                val read = readRbspByte(buffer, cursor, rbspStart, rbspEnd)
                if (read < 0) return SeiNalAnalysis.MALFORMED
                cursor = unpackReadOffset(read)
                value = unpackReadValue(read)
                if (payloadSize > Int.MAX_VALUE - value) return SeiNalAnalysis.MALFORMED
                payloadSize += value
            } while (value == 0xFF)

            // Each logical payload byte occupies at least one physical byte.
            if (payloadSize > rbspEnd - cursor) return SeiNalAnalysis.MALFORMED

            var signatureMatches =
                payloadType == SEI_USER_DATA_REGISTERED_ITU_T_T35 && payloadSize >= 7
            var payloadIndex = 0
            while (payloadIndex < payloadSize) {
                val read = readRbspByte(buffer, cursor, rbspStart, rbspEnd)
                if (read < 0) return SeiNalAnalysis.MALFORMED
                cursor = unpackReadOffset(read)
                val payloadByte = unpackReadValue(read)
                if (payloadIndex < 7 && !matchesHdr10PlusSignatureByte(payloadIndex, payloadByte)) {
                    signatureMatches = false
                }
                payloadIndex++
            }

            if (signatureMatches) {
                foundHdr10Plus = true
                if (neutralize) {
                    // Payload type 4 has a one-byte encoding. Replacing it with the reserved type
                    // 254 cannot introduce an Annex-B start code or require emulation prevention.
                    val encodedPayloadType = buffer.get(payloadTypeOffset).toInt() and 0xFF
                    if (encodedPayloadType != SEI_USER_DATA_REGISTERED_ITU_T_T35) {
                        return SeiNalAnalysis.MALFORMED
                    }
                    buffer.put(payloadTypeOffset, 0xFE.toByte())
                }
            }
        }
    }

    /** Returns 1 for trailing bits, 0 for more data, and -1 for malformed escaping. */
    private fun trailingBitsStatus(
        buffer: ByteBuffer,
        offset: Int,
        rbspStart: Int,
        rbspEnd: Int,
    ): Int {
        val first = readRbspByte(buffer, offset, rbspStart, rbspEnd)
        if (first == RBSP_MALFORMED) return -1
        if (first == RBSP_EOF || unpackReadValue(first) != 0x80) return 0

        var cursor = unpackReadOffset(first)
        while (cursor < rbspEnd) {
            val read = readRbspByte(buffer, cursor, rbspStart, rbspEnd)
            if (read < 0) return -1
            if (unpackReadValue(read) != 0) return 0
            cursor = unpackReadOffset(read)
        }
        return 1
    }

    private fun readRbspByte(
        buffer: ByteBuffer,
        offset: Int,
        rbspStart: Int,
        rbspEnd: Int,
    ): Long {
        if (offset >= rbspEnd) return RBSP_EOF
        var physicalOffset = offset
        val followsTwoZeros =
            physicalOffset >= rbspStart + 2 &&
            buffer.get(physicalOffset - 1).toInt() == 0 &&
            buffer.get(physicalOffset - 2).toInt() == 0
        val value = buffer.get(physicalOffset).toInt() and 0xFF
        if (followsTwoZeros && value == 0x03) {
            physicalOffset++
            if (physicalOffset >= rbspEnd || buffer.get(physicalOffset).toInt() and 0xFF > 0x03) {
                return RBSP_MALFORMED
            }
        } else if (followsTwoZeros && value <= 0x02) {
            return RBSP_MALFORMED
        }
        return packRead(physicalOffset + 1, buffer.get(physicalOffset).toInt() and 0xFF)
    }

    private fun matchesHdr10PlusSignatureByte(
        index: Int,
        value: Int,
    ): Boolean =
        when (index) {
            0 -> value == 0xB5 // itu_t_t35_country_code: United States
            1 -> value == 0x00
            2 -> value == 0x3C // terminal_provider_code: Samsung Electronics America
            3 -> value == 0x00
            4 -> value == 0x01 // terminal_provider_oriented_code: SMPTE ST 2094-40
            5 -> value == 0x04 // application_identifier
            6 -> value <= 0x01 // application_version
            else -> error("HDR10+ signature index out of range")
        }

    private fun findStartCode(
        buffer: ByteBuffer,
        from: Int,
        limit: Int,
    ): Long {
        var zeroRunStart = -1
        var zeroCount = 0
        var index = from
        while (index < limit) {
            when (buffer.get(index).toInt() and 0xFF) {
                0 -> {
                    if (zeroCount == 0) zeroRunStart = index
                    zeroCount++
                }

                1 -> {
                    if (zeroCount >= 2) return packStartCode(zeroRunStart, index)
                    zeroCount = 0
                }

                else -> zeroCount = 0
            }
            index++
        }
        return -1
    }

    private fun packStartCode(
        offset: Int,
        oneOffset: Int,
    ): Long = (offset.toLong() shl 32) or (oneOffset.toLong() and 0xFFFFFFFFL)

    private fun unpackStartCodeOffset(value: Long): Int = (value ushr 32).toInt()

    private fun unpackStartCodeOneOffset(value: Long): Int = value.toInt()

    private fun packRead(
        nextOffset: Int,
        value: Int,
    ): Long = (nextOffset.toLong() shl 8) or value.toLong()

    private fun unpackReadOffset(value: Long): Int = (value ushr 8).toInt()

    private fun unpackReadValue(value: Long): Int = (value and 0xFF).toInt()
}
