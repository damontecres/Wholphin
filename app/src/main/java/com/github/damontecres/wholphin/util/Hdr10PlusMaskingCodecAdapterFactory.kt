@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.util

import android.media.MediaCodec
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.ForwardingMediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import timber.log.Timber

/**
 * A [MediaCodecAdapter.Factory] which masks the HDR10+ metadata of Dolby Vision streams before the
 * samples are queued to the decoder.
 *
 * Some decoder pipelines (notably on MediaTek based TVs) engage HDR10+ instead of Dolby Vision
 * when a stream carries both kinds of dynamic HDR metadata. The wrapper is only applied when a
 * Dolby Vision decoder was actually selected, so playback which falls back to a regular HEVC/AVC
 * decoder keeps its HDR10+ metadata.
 */
class Hdr10PlusMaskingCodecAdapterFactory(
    private val delegate: MediaCodecAdapter.Factory,
) : MediaCodecAdapter.Factory {
    override fun createAdapter(configuration: MediaCodecAdapter.Configuration): MediaCodecAdapter {
        val adapter = delegate.createAdapter(configuration)
        return if (configuration.codecInfo.mimeType == MimeTypes.VIDEO_DOLBY_VISION) {
            Timber.i(
                "Masking HDR10+ SEI messages for Dolby Vision playback on %s",
                configuration.codecInfo.name,
            )
            Hdr10PlusMaskingCodecAdapter(adapter)
        } else {
            adapter
        }
    }
}

/**
 * Masks HDR10+ SEI messages in each input buffer before it is queued. The masking happens in
 * place and does not change the buffer size, so it is safe for both the synchronous and
 * asynchronous [MediaCodecAdapter] implementations. Encrypted samples go through
 * [MediaCodecAdapter.queueSecureInputBuffer] and are forwarded untouched.
 */
private class Hdr10PlusMaskingCodecAdapter(
    delegate: MediaCodecAdapter,
) : ForwardingMediaCodecAdapter(delegate) {
    private var maskedCount = 0

    override fun queueInputBuffer(
        index: Int,
        offset: Int,
        size: Int,
        presentationTimeUs: Long,
        flags: Int,
    ) {
        if (size > 0 && (flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            getInputBuffer(index)?.let { buffer ->
                maskedCount += Hdr10PlusSeiFilter.maskHdr10PlusSeiMessages(buffer, offset, size)
            }
        }
        super.queueInputBuffer(index, offset, size, presentationTimeUs, flags)
    }

    override fun release() {
        if (maskedCount > 0) {
            Timber.d("Masked %d HDR10+ SEI messages", maskedCount)
        }
        super.release()
    }
}
