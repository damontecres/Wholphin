package com.github.damontecres.wholphin.data.model

import androidx.room.Embedded
import androidx.room.Entity
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

@Entity(tableName = "playback_effects", primaryKeys = ["jellyfinUserRowId", "itemId", "type"])
data class PlaybackEffect(
    val jellyfinUserRowId: Int,
    val itemId: UUID,
    val type: BaseItemKind,
    @Embedded val videoFilter: VideoFilter,
)
