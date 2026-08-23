package com.github.damontecres.wholphin.util.media

import java.nio.ByteBuffer

/**
 * Removes HDR10+ metadata from HEVC Annex-B samples while leaving all other NAL units and SEI
 * messages unchanged.
 *
 * The input is parsed without modifying it first. This makes malformed input fail open: if any SEI
 * NAL unit cannot be parsed, the original sample is returned unchanged. The no-match path does not
 * allocate or copy sample data.
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
        REBUILD,
        DROP,
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
     * On success, filtered data starts at the original position and the limit is reduced to the new
     * sample size. The position is preserved for every result.
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
                when (analyzeSeiNal(buffer, nalHeaderOffset + 2, nalEnd)) {
                    SeiNalAnalysis.MALFORMED -> return Result.MALFORMED
                    SeiNalAnalysis.REBUILD,
                    SeiNalAnalysis.DROP,
                    -> containsHdr10Plus = true

                    SeiNalAnalysis.UNCHANGED -> Unit
                }
            }
            startCode = nextStartCode
        }

        if (!containsHdr10Plus) return Result.UNCHANGED
        if (buffer.isReadOnly) return Result.READ_ONLY

        val output = EbspOutput(ByteArray(originalLimit - originalPosition))
        var copiedUntil = originalPosition
        startCode = findStartCode(buffer, originalPosition, originalLimit)
        while (startCode >= 0) {
            val startCodeOffset = unpackStartCodeOffset(startCode)
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
            val analysis =
                if (nalType == NAL_PREFIX_SEI || nalType == NAL_SUFFIX_SEI) {
                    analyzeSeiNal(buffer, payloadOffset, nalEnd)
                } else {
                    SeiNalAnalysis.UNCHANGED
                }

            output.copyFrom(buffer, copiedUntil, startCodeOffset)
            when (analysis) {
                SeiNalAnalysis.UNCHANGED -> output.copyFrom(buffer, startCodeOffset, nalEnd)
                SeiNalAnalysis.DROP -> Unit
                SeiNalAnalysis.REBUILD -> {
                    output.copyFrom(buffer, startCodeOffset, payloadOffset)
                    val rbsp = unescapeRbsp(buffer, payloadOffset, nalEnd) ?: return Result.MALFORMED
                    if (!writeFilteredSeiRbsp(rbsp, output)) return Result.MALFORMED
                }

                SeiNalAnalysis.MALFORMED -> return Result.MALFORMED
            }
            copiedUntil = nalEnd
            startCode = nextStartCode
        }
        output.copyFrom(buffer, copiedUntil, originalLimit)

        if (output.overflowed || output.size > originalLimit - originalPosition) return Result.MALFORMED
        var index = 0
        while (index < output.size) {
            buffer.put(originalPosition + index, output.bytes[index])
            index++
        }
        buffer.limit(originalPosition + output.size)
        buffer.position(originalPosition)
        return Result.FILTERED
    }

    private fun analyzeSeiNal(
        buffer: ByteBuffer,
        rbspStart: Int,
        rbspEnd: Int,
    ): SeiNalAnalysis {
        var cursor = rbspStart
        var foundHdr10Plus = false
        var keptMessageCount = 0

        while (true) {
            when (trailingBitsStatus(buffer, cursor, rbspStart, rbspEnd)) {
                1 -> {
                    return when {
                        !foundHdr10Plus -> SeiNalAnalysis.UNCHANGED
                        keptMessageCount == 0 -> SeiNalAnalysis.DROP
                        else -> SeiNalAnalysis.REBUILD
                    }
                }

                -1 -> return SeiNalAnalysis.MALFORMED
            }

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

            var signatureMatches = payloadType == SEI_USER_DATA_REGISTERED_ITU_T_T35 && payloadSize >= 7
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
            } else {
                keptMessageCount++
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

    private fun unescapeRbsp(
        buffer: ByteBuffer,
        start: Int,
        end: Int,
    ): ByteArray? {
        val rbsp = ByteArray(end - start)
        var cursor = start
        var size = 0
        while (cursor < end) {
            val read = readRbspByte(buffer, cursor, start, end)
            if (read < 0) return null
            cursor = unpackReadOffset(read)
            rbsp[size++] = unpackReadValue(read).toByte()
        }
        return if (size == rbsp.size) rbsp else rbsp.copyOf(size)
    }

    private fun writeFilteredSeiRbsp(
        rbsp: ByteArray,
        output: EbspOutput,
    ): Boolean {
        var cursor = 0
        output.startEbsp()
        while (!isTrailingBits(rbsp, cursor)) {
            val messageStart = cursor
            var payloadType = 0
            var value: Int
            do {
                if (cursor >= rbsp.size) return false
                value = rbsp[cursor++].toInt() and 0xFF
                if (payloadType > Int.MAX_VALUE - value) return false
                payloadType += value
            } while (value == 0xFF)

            var payloadSize = 0
            do {
                if (cursor >= rbsp.size) return false
                value = rbsp[cursor++].toInt() and 0xFF
                if (payloadSize > Int.MAX_VALUE - value) return false
                payloadSize += value
            } while (value == 0xFF)
            if (payloadSize > rbsp.size - cursor) return false

            var signatureMatches = payloadType == SEI_USER_DATA_REGISTERED_ITU_T_T35 && payloadSize >= 7
            var payloadIndex = 0
            while (payloadIndex < payloadSize) {
                val payloadByte = rbsp[cursor + payloadIndex].toInt() and 0xFF
                if (payloadIndex < 7 && !matchesHdr10PlusSignatureByte(payloadIndex, payloadByte)) {
                    signatureMatches = false
                }
                payloadIndex++
            }
            cursor += payloadSize

            if (!signatureMatches) {
                var messageOffset = messageStart
                while (messageOffset < cursor) {
                    output.writeRbspByte(rbsp[messageOffset].toInt() and 0xFF)
                    messageOffset++
                }
            }
        }

        while (cursor < rbsp.size) {
            output.writeRbspByte(rbsp[cursor].toInt() and 0xFF)
            cursor++
        }
        return true
    }

    private fun isTrailingBits(
        rbsp: ByteArray,
        offset: Int,
    ): Boolean {
        if (offset >= rbsp.size || rbsp[offset].toInt() and 0xFF != 0x80) return false
        var cursor = offset + 1
        while (cursor < rbsp.size) {
            if (rbsp[cursor].toInt() != 0) return false
            cursor++
        }
        return true
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

    private class EbspOutput(
        val bytes: ByteArray,
    ) {
        var size = 0
            private set
        var overflowed = false
            private set
        private var consecutiveZeros = 0

        fun copyFrom(
            buffer: ByteBuffer,
            start: Int,
            end: Int,
        ) {
            var inputOffset = start
            while (inputOffset < end) {
                if (size == bytes.size) {
                    overflowed = true
                    return
                }
                bytes[size++] = buffer.get(inputOffset++)
            }
        }

        fun startEbsp() {
            consecutiveZeros = 0
        }

        fun writeRbspByte(value: Int) {
            if (consecutiveZeros >= 2 && value <= 0x03) {
                if (size == bytes.size) {
                    overflowed = true
                    return
                }
                bytes[size++] = 0x03
                consecutiveZeros = 0
            }
            if (size == bytes.size) {
                overflowed = true
                return
            }
            bytes[size++] = value.toByte()
            consecutiveZeros = if (value == 0) consecutiveZeros + 1 else 0
        }
    }
}
