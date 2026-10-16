@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.util

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ForwardingExtractor
import androidx.media3.extractor.ForwardingExtractorOutput
import androidx.media3.extractor.ForwardingExtractorsFactory
import androidx.media3.extractor.ForwardingTrackOutput
import androidx.media3.extractor.TrackOutput
import timber.log.Timber

/**
 * Advertises Dolby Vision profile 7 video tracks as profile 8.1, which is what
 * [DolbyVisionProfile7Filter] turns their samples into before the decoder sees them.
 *
 * media3 chooses the decoder by `Format.codecs`, and a decoder which lists profile 8 but not
 * profile 7 answers "not supported" to `dvhe.07`; with decoder fallback on, playback then lands on
 * whichever decoder comes first in the list, with a warning that the format exceeds its
 * capabilities. Rewriting the codec string at the extractor routes the track to the profile 8
 * decoder, and it also sets `MediaFormat.KEY_PROFILE` right, which media3 derives from the same
 * string.
 *
 * Only the codec string changes. Sample data is not touched here: the two convenience overloads of
 * [TrackOutput.sampleData] go straight to the delegate, which is exactly what is wanted.
 */
class DolbyVisionProfile7ExtractorsFactory(
    delegate: ExtractorsFactory,
) : ForwardingExtractorsFactory(delegate) {
    override fun createExtractors(): Array<Extractor> = wrap(super.createExtractors())

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>,
    ): Array<Extractor> = wrap(super.createExtractors(uri, responseHeaders))

    private fun wrap(extractors: Array<Extractor>): Array<Extractor> =
        Array(extractors.size) { Profile8AdvertisingExtractor(extractors[it]) }
}

private class Profile8AdvertisingExtractor(
    delegate: Extractor,
) : ForwardingExtractor(delegate) {
    override fun init(output: ExtractorOutput) {
        super.init(Profile8AdvertisingExtractorOutput(output))
    }
}

private class Profile8AdvertisingExtractorOutput(
    output: ExtractorOutput,
) : ForwardingExtractorOutput(output) {
    override fun track(
        id: Int,
        type: Int,
    ): TrackOutput {
        val trackOutput = super.track(id, type)
        return if (type == C.TRACK_TYPE_VIDEO) Profile8AdvertisingTrackOutput(trackOutput) else trackOutput
    }
}

/** Rewrites the codec string of every profile 7 format it is handed; everything else passes unchanged. */
internal class Profile8AdvertisingTrackOutput(
    delegate: TrackOutput,
) : ForwardingTrackOutput(delegate) {
    override fun format(format: Format) {
        val advertised = DolbyVisionProfile7Conversion.advertiseAsProfile8(format)
        if (advertised !== format) {
            Timber.i("Advertising Dolby Vision track %s as %s for conversion", format.codecs, advertised.codecs)
        }
        super.format(advertised)
    }
}
