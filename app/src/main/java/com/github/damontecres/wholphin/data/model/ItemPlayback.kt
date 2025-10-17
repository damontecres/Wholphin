@file:UseSerializers(UuidSerializer::class)

package com.github.damontecres.wholphin.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.github.damontecres.wholphin.data.JellyfinUser
import com.github.damontecres.wholphin.util.UuidSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.UUID

@Entity(
    primaryKeys = ["serverId", "userId", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = JellyfinUser::class,
            parentColumns = ["serverId", "id"],
            childColumns = ["serverId", "userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("serverId", "userId", "itemId")],
)
@Serializable
data class ItemPlayback(
    val serverId: UUID,
    val userId: UUID,
    val itemId: UUID,
    val sourceId: UUID? = null,
    val audioIndex: Int = TrackIndex.UNSPECIFIED,
    val subtitleIndex: Int = TrackIndex.UNSPECIFIED,
) {
    @Transient val audioIndexEnabled = audioIndex >= 0

    @Transient val subtitleIndexEnabled = subtitleIndex >= 0
}

object TrackIndex {
    /**
     * The user has not explicitly specified a track to use
     */
    const val UNSPECIFIED = -1

    /**
     * The user has explicitly disabled the tracks (eg turned off subtitles)
     */
    const val DISABLED = -2
}
