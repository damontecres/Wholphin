@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.util

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.NalUnitUtil
import java.nio.ByteBuffer

/**
 * Masks HDR10+ (SMPTE ST 2094-40) SEI messages in HEVC access units so that decoders ignore them.
 *
 * Some decoder pipelines (notably on MediaTek based TVs) latch on to HDR10+ when a stream carries
 * both Dolby Vision RPUs and HDR10+ dynamic metadata, even when a Dolby Vision decoder is selected.
 * Overwriting the SEI payload type of the HDR10+ messages with an undefined value makes decoders
 * skip them without changing the size of the access unit, so no NAL units need to be removed or
 * rewritten.
 */
object Hdr10PlusSeiFilter {
    /**
     * Highest VCL NAL unit type in H.265 Table 7-1. Prefix SEI NAL units always precede the VCL
     * NAL units of an access unit, so the scan can stop at the first one.
     */
    private const val MAX_VCL_NAL_UNIT_TYPE = 31

    /** `user_data_registered_itu_t_t35`, the SEI payload type used by HDR10+ (H.265 D.2.1). */
    private const val PAYLOAD_TYPE_USER_DATA_REGISTERED_ITU_T_T35 = 4

    /**
     * An SEI payload type H.265 does not define. H.265 D.3.1 requires a decoder to ignore SEI
     * messages whose payload type it does not recognise, so writing this over the payload type
     * makes the decoder skip the message without anything being removed or resized.
     */
    private const val PAYLOAD_TYPE_MASKED = 254

    /**
     * ITU-T T.35 header identifying an HDR10+ message: `itu_t_t35_country_code` 0xB5 (United
     * States), `itu_t_t35_terminal_provider_code` 0x003C (Samsung, which defined HDR10+) and
     * `itu_t_t35_terminal_provider_oriented_code` 0x0001. FFmpeg declares the first two as
     * `ITU_T_T35_COUNTRY_CODE_US` and `ITU_T_T35_PROVIDER_CODE_SAMSUNG` in `libavcodec/itut35.h`.
     */
    private val HDR10_PLUS_T35_HEADER = intArrayOf(0xB5, 0x00, 0x3C, 0x00, 0x01)

    /**
     * Masks every HDR10+ SEI message found in the HEVC access unit in `[offset, offset + size)` by
     * overwriting its SEI payload type in place. The access unit must contain Annex B start code
     * delimited NAL units (which is what media3's extractors emit); buffers with any other layout
     * are left untouched. The buffer's position and limit are not modified.
     *
     * @return the number of masked SEI messages
     */
    fun maskHdr10PlusSeiMessages(
        data: ByteBuffer,
        offset: Int,
        size: Int,
    ): Int {
        val end = offset + size
        if (!startsWithStartCode(data, offset, end)) {
            return 0
        }
        var masked = 0
        var searchFrom = offset
        while (searchFrom < end) {
            val nalStart = findNalUnitStart(data, searchFrom, end)
            if (nalStart < 0 || nalStart >= end) {
                break
            }
            val nalEnd = findNalUnitEnd(data, nalStart, end)
            // nal_unit_type is the 6 bits after forbidden_zero_bit, H.265 section 7.3.1.2
            val nalUnitType = (data.get(nalStart).toInt() shr 1) and 0x3F
            if (nalUnitType <= MAX_VCL_NAL_UNIT_TYPE) {
                // HDR10+ SEI messages precede the VCL NAL units, so stop scanning here
                break
            }
            if (nalUnitType == NalUnitUtil.H265_NAL_UNIT_TYPE_PREFIX_SEI) {
                masked += maskInSeiNalUnit(data, nalStart + 2, nalEnd)
            }
            searchFrom = nalEnd
        }
        return masked
    }

    private fun startsWithStartCode(
        data: ByteBuffer,
        offset: Int,
        end: Int,
    ): Boolean {
        if (end - offset < 4) {
            return false
        }
        return data.get(offset) == 0.toByte() &&
            data.get(offset + 1) == 0.toByte() &&
            (
                data.get(offset + 2) == 1.toByte() ||
                    (data.get(offset + 2) == 0.toByte() && data.get(offset + 3) == 1.toByte())
            )
    }

    /**
     * Returns the index of the first NAL unit byte after the next start code, or -1 if none.
     * Start codes are the Annex B byte stream format.
     */
    private fun findNalUnitStart(
        data: ByteBuffer,
        from: Int,
        end: Int,
    ): Int {
        for (i in from..end - 4) {
            if (data.get(i) == 0.toByte() &&
                data.get(i + 1) == 0.toByte() &&
                data.get(i + 2) == 1.toByte()
            ) {
                return i + 3
            }
        }
        return -1
    }

    /** Returns the index just past the NAL unit starting at [from], i.e. the next start code or [end]. */
    private fun findNalUnitEnd(
        data: ByteBuffer,
        from: Int,
        end: Int,
    ): Int {
        for (i in from..end - 3) {
            if (data.get(i) == 0.toByte() &&
                data.get(i + 1) == 0.toByte() &&
                data.get(i + 2) == 1.toByte()
            ) {
                return i
            }
        }
        return end
    }

    /**
     * Parses the SEI messages in the NAL unit payload in `[payloadStart, nalEnd)` per H.265
     * section D.2.1 and masks the payload type of each HDR10+ message. Parsing is conservative:
     * on any malformed data it stops without touching the remainder.
     */
    private fun maskInSeiNalUnit(
        data: ByteBuffer,
        payloadStart: Int,
        nalEnd: Int,
    ): Int {
        // Trim trailing_zero_8bits so the last byte is the RBSP trailing bits
        var end = nalEnd
        while (end > payloadStart && data.get(end - 1) == 0.toByte()) {
            end--
        }
        val reader = RbspReader(data, payloadStart, end)
        var masked = 0
        while (reader.bytesRemaining > 1) {
            var payloadType = 0
            var b = reader.readByte()
            while (b == 0xFF) {
                payloadType += 255
                b = reader.readByte()
            }
            if (b < 0) {
                return masked
            }
            val payloadTypePosition = reader.lastBytePosition
            payloadType += b

            var payloadSize = 0
            b = reader.readByte()
            while (b == 0xFF) {
                payloadSize += 255
                b = reader.readByte()
            }
            if (b < 0) {
                return masked
            }
            payloadSize += b
            if (payloadSize > reader.bytesRemaining) {
                return masked
            }

            var remainingPayload = payloadSize
            var isHdr10Plus =
                payloadType == PAYLOAD_TYPE_USER_DATA_REGISTERED_ITU_T_T35 &&
                    payloadSize >= HDR10_PLUS_T35_HEADER.size
            if (isHdr10Plus) {
                for (expected in HDR10_PLUS_T35_HEADER) {
                    remainingPayload--
                    if (reader.readByte() != expected) {
                        isHdr10Plus = false
                        break
                    }
                }
            }
            repeat(remainingPayload) {
                if (reader.readByte() < 0) {
                    return masked
                }
            }
            if (isHdr10Plus) {
                data.put(payloadTypePosition, PAYLOAD_TYPE_MASKED.toByte())
                masked++
            }
        }
        return masked
    }

    /**
     * Reads RBSP bytes from `[position, end)`, skipping the `emulation_prevention_three_byte`
     * that H.265 section 7.4.2 inserts after two consecutive zero bytes.
     */
    private class RbspReader(
        private val data: ByteBuffer,
        private var position: Int,
        private val end: Int,
    ) {
        private var zeroRun = 0

        /** Raw buffer index of the byte returned by the last [readByte] call. */
        var lastBytePosition = -1
            private set

        /** Upper bound of the remaining RBSP bytes. */
        val bytesRemaining: Int
            get() = end - position

        /** Returns the next RBSP byte, or -1 if the end was reached. */
        fun readByte(): Int {
            if (position >= end) {
                return -1
            }
            var b = data.get(position).toInt() and 0xFF
            if (zeroRun >= 2 && b == 0x03) {
                // Skip the emulation_prevention_three_byte
                zeroRun = 0
                position++
                if (position >= end) {
                    return -1
                }
                b = data.get(position).toInt() and 0xFF
            }
            zeroRun = if (b == 0) zeroRun + 1 else 0
            lastBytePosition = position
            position++
            return b
        }
    }
}
