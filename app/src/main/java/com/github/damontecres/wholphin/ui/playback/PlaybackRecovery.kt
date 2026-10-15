package com.github.damontecres.wholphin.ui.playback

import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.util.profile.Codec
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod

/** Whether the failed embedded subtitle can be retried using Jellyfin's subtitle endpoint. */
internal fun CurrentPlayback.canRetryWithExternalSubtitles(error: Throwable): Boolean {
    if (backend != PlayerBackend.EXO_PLAYER || playMethod != PlayMethod.DIRECT_PLAY ||
        mediaSourceInfo.container != Codec.Container.MKV
    ) {
        return false
    }
    val subtitle =
        mediaSourceInfo.mediaStreams?.firstOrNull {
            it.type == MediaStreamType.SUBTITLE && it.index == subtitleIndex
        } ?: return false
    return !subtitle.isExternal &&
        subtitle.deliveryMethod == SubtitleDeliveryMethod.EMBED &&
        subtitle.supportsExternalStream &&
        subtitle.codec?.lowercase() in setOf(Codec.Subtitle.SRT, Codec.Subtitle.SUBRIP) &&
        generateSequence(error) { it.cause }.any {
            it is IllegalStateException && it.message == "No valid varint length mask found"
        }
}

internal fun DeviceProfile.withExternalSubtitles(): DeviceProfile =
    copy(
        // Jellyfin considers convertible embedded formats before external delivery too.
        subtitleProfiles = subtitleProfiles.filterNot { it.method == SubtitleDeliveryMethod.EMBED },
    )
