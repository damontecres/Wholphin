@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

/**
 * Streamystats configuration for a single Jellyfin user on a single Jellyfin server.
 */
@Entity(
    tableName = "streamystats_settings",
    primaryKeys = ["jellyfinUserRowId", "jellyfinServerId"],
    foreignKeys = [
        ForeignKey(
            entity = JellyfinUser::class,
            parentColumns = arrayOf("rowId"),
            childColumns = arrayOf("jellyfinUserRowId"),
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = JellyfinServer::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("jellyfinServerId"),
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("jellyfinUserRowId"),
        Index("jellyfinServerId"),
    ],
)
@Serializable
data class StreamystatsSettings(
    val jellyfinUserRowId: Int,
    val jellyfinServerId: UUID,
    val serverUrl: String,
)
