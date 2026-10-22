package com.github.damontecres.wholphin.util.profile

import org.jellyfin.sdk.model.api.VideoRangeType

/**
 * Complete list of video range types, including ones not yet present in the pinned Jellyfin SDK's
 * [VideoRangeType] enum (added server-side in Jellyfin 10.11).
 *
 * TODO Remove this and use [VideoRangeType] directly once the SDK is upgraded to a version built against Jellyfin 10.11+
 */
enum class WholphinVideoRangeType(
    val serialName: String,
) {
    UNKNOWN(VideoRangeType.UNKNOWN.serialName),
    SDR(VideoRangeType.SDR.serialName),
    HDR10(VideoRangeType.HDR10.serialName),
    HLG(VideoRangeType.HLG.serialName),
    DOVI(VideoRangeType.DOVI.serialName),
    DOVI_WITH_HDR10(VideoRangeType.DOVI_WITH_HDR10.serialName),
    DOVI_WITH_HLG(VideoRangeType.DOVI_WITH_HLG.serialName),
    DOVI_WITH_SDR(VideoRangeType.DOVI_WITH_SDR.serialName),
    HDR10_PLUS(VideoRangeType.HDR10_PLUS.serialName),
    DOVI_INVALID("DOVIInvalid"),
    DOVI_WITH_EL("DOVIWithEL"),
    DOVI_WITH_EL_HDR10_PLUS("DOVIWithELHDR10Plus"),
    DOVI_WITH_HDR10_PLUS("DOVIWithHDR10Plus"),
}
