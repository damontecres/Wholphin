package com.github.damontecres.wholphin.ui.playback

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.util.TrackSupport
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.TranscodingInfo

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
)
