package com.github.damontecres.wholphin.ui.playback

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.util.TrackSupport
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.TranscodingInfo
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.util.UUID
import kotlin.time.Duration

/**
 * Information about how the current media is being played such transcoding and decoder info
 *
 * @see CurrentMediaInfo
 */
data class CurrentPlayback(
    val item: BaseItem,
    val tracks: List<TrackSupport>,
    val backend: PlayerBackend,
    val playMethod: PlayMethod,
    val playSessionId: String?,
    val liveStreamId: String?,
    val mediaSourceInfo: MediaSourceInfo,
    val videoDecoder: String? = null,
    val audioDecoder: String? = null,
    val transcodeInfo: TranscodingInfo? = null,
    val subtitleDelay: Duration = Duration.ZERO,
    val audioIndex: Int = TrackIndex.UNSPECIFIED,
    val subtitleIndex: Int = TrackIndex.UNSPECIFIED,
) {
    val audioIndexEnabled = audioIndex >= 0
    val subtitleIndexEnabled = subtitleIndex >= 0

    val itemId: UUID get() = item.id
    val sourceId: UUID? = mediaSourceInfo.id?.toUUIDOrNull()
}
