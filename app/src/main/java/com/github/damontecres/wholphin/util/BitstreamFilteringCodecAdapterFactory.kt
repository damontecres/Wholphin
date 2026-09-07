@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.util

import android.media.MediaCodec
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.ForwardingMediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * Rewrites the bitstream of an access unit after the extractor and before it reaches the decoder.
 *
 * Implementations edit the buffer in place. Overwriting bytes leaves the access unit the size it
 * was, removing bytes does not: a filter which drops part of an access unit has to move the bytes
 * it keeps so that they stay contiguous from `offset`, and report the shorter length. The buffer's
 * position and limit are not to be modified.
 */
fun interface BitstreamFilter {
    /**
     * Filters the access unit in `[offset, offset + size)`.
     *
     * @return the new size of the access unit, which is never larger than [size]. Filters which
     *   only overwrite bytes return [size] unchanged.
     */
    fun filter(
        data: ByteBuffer,
        offset: Int,
        size: Int,
    ): Int
}

/**
 * A [MediaCodecAdapter.Factory] which runs [filters] over the samples of Dolby Vision streams
 * before they are queued to the decoder.
 *
 * The wrapper is only applied when a Dolby Vision decoder was actually selected, so playback which
 * falls back to a regular HEVC/AVC decoder sees the bitstream as it came out of the extractor.
 */
class BitstreamFilteringCodecAdapterFactory(
    private val delegate: MediaCodecAdapter.Factory,
    private val filters: List<BitstreamFilter>,
) : MediaCodecAdapter.Factory {
    override fun createAdapter(configuration: MediaCodecAdapter.Configuration): MediaCodecAdapter {
        val adapter = delegate.createAdapter(configuration)
        return if (filters.isNotEmpty() && configuration.codecInfo.mimeType == MimeTypes.VIDEO_DOLBY_VISION) {
            Timber.i(
                "Filtering the bitstream of Dolby Vision playback on %s with %s",
                configuration.codecInfo.name,
                filters.joinToString { it.javaClass.simpleName },
            )
            BitstreamFilteringCodecAdapter(adapter, filters)
        } else {
            adapter
        }
    }
}

/**
 * Runs each filter over the access unit in `[offset, offset + size)`, handing the next filter the
 * length the previous one reported, and returns the length that is left to queue. A filter which
 * reports a length it cannot have produced is ignored rather than trusted, since queueing the
 * wrong length would feed the decoder a truncated or over long access unit.
 */
internal fun applyBitstreamFilters(
    filters: List<BitstreamFilter>,
    data: ByteBuffer,
    offset: Int,
    size: Int,
): Int {
    var filteredSize = size
    for (filter in filters) {
        if (filteredSize <= 0) {
            break
        }
        val newSize = filter.filter(data, offset, filteredSize)
        if (newSize in 0..filteredSize) {
            filteredSize = newSize
        } else {
            Timber.w(
                "%s returned %d bytes for a %d byte access unit, keeping the access unit as it is",
                filter.javaClass.simpleName,
                newSize,
                filteredSize,
            )
        }
    }
    return filteredSize
}

/**
 * Filters each input buffer before it is queued. Encrypted samples go through
 * [MediaCodecAdapter.queueSecureInputBuffer] and are forwarded untouched.
 */
private class BitstreamFilteringCodecAdapter(
    delegate: MediaCodecAdapter,
    private val filters: List<BitstreamFilter>,
) : ForwardingMediaCodecAdapter(delegate) {
    override fun queueInputBuffer(
        index: Int,
        offset: Int,
        size: Int,
        presentationTimeUs: Long,
        flags: Int,
    ) {
        var filteredSize = size
        if (size > 0 && (flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            getInputBuffer(index)?.let { buffer ->
                filteredSize = applyBitstreamFilters(filters, buffer, offset, size)
            }
        }
        super.queueInputBuffer(index, offset, filteredSize, presentationTimeUs, flags)
    }

    override fun release() {
        Timber.d("Releasing bitstream filtering adapter, %s", filters.joinToString())
        super.release()
    }
}
