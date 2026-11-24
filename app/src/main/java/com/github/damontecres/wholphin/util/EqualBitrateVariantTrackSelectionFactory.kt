@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.util

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.BandwidthMeter

/**
 * Wraps a [ExoTrackSelection.Factory] so that adaptive video selections never include tracks which
 * have the same bitrate, resolution, and frame rate as an earlier track in the same group.
 *
 * When the server can remux an HDR video, its HLS master playlist also lists SDR transcode variants
 * with an identical `BANDWIDTH`, expecting clients to choose between them by `VIDEO-RANGE`.
 * ExoPlayer ignores `VIDEO-RANGE` for non-Dolby Vision variants, so the variants look identical and
 * [AdaptiveTrackSelection] picks the last one whenever the bandwidth estimate is below the
 * bitrate. That requests a tone-mapped transcode instead of the remux.
 *
 * The server lists the variant matching the device profile first, so only that one is kept.
 */
class EqualBitrateVariantTrackSelectionFactory(
    private val delegate: ExoTrackSelection.Factory = AdaptiveTrackSelection.Factory(),
) : ExoTrackSelection.Factory {
    override fun createTrackSelections(
        definitions: Array<out ExoTrackSelection.Definition?>,
        bandwidthMeter: BandwidthMeter,
        mediaPeriodId: MediaSource.MediaPeriodId,
        timeline: Timeline,
    ): Array<ExoTrackSelection?> =
        delegate.createTrackSelections(
            definitions.map { it?.withoutEqualBitrateVideoVariants() }.toTypedArray(),
            bandwidthMeter,
            mediaPeriodId,
            timeline,
        )
}

internal fun ExoTrackSelection.Definition.withoutEqualBitrateVideoVariants(): ExoTrackSelection.Definition {
    if (group.type != C.TRACK_TYPE_VIDEO || tracks.size < 2) return this

    val seen = mutableSetOf<VariantKey>()
    val keep =
        tracks
            .sorted()
            .filter { index ->
                val format = group.getFormat(index)
                format.bitrate == Format.NO_VALUE || seen.add(VariantKey(format))
            }.toSet()
    if (keep.size == tracks.size) return this

    return ExoTrackSelection.Definition(group, tracks.filter { it in keep }.toIntArray(), type)
}

private data class VariantKey(
    val bitrate: Int,
    val width: Int,
    val height: Int,
    val frameRate: Float,
) {
    constructor(format: Format) : this(format.bitrate, format.width, format.height, format.frameRate)
}
