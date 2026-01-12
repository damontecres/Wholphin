@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = JellyfinUser::class,
            parentColumns = arrayOf("rowId"),
            childColumns = arrayOf("userId"),
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("userId", "itemId", unique = true)],
)
@Serializable
data class ItemPlayback(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,
    val userId: Int,
    val itemId: UUID,
    val sourceId: UUID? = null,
    val audioIndex: Int = TrackIndex.UNSPECIFIED,
    val subtitleIndex: Int = TrackIndex.UNSPECIFIED,
) {
    @Transient
    @Ignore
    val audioIndexEnabled = audioIndex >= 0

    @Transient
    @Ignore
    val subtitleIndexEnabled = subtitleIndex >= 0
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

    /**
     * The user wants only forced subtitles (for foreign language dialogue, signs, etc.)
     */
    const val ONLY_FORCED = -3
}
