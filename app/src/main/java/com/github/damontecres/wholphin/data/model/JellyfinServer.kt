@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

@Entity(tableName = "servers")
@Serializable
data class JellyfinServer(
    @PrimaryKey val id: UUID,
    val name: String?,
    val url: String,
    val version: String?,
) {
    @get:Ignore
    val serverVersion: ServerVersion? by lazy { version?.let(ServerVersion::fromString) }
}

@Entity(
    tableName = "users",
    foreignKeys = [
        ForeignKey(
            entity = JellyfinServer::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("serverId"),
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("id", "serverId", unique = true)],
)
@Serializable
data class JellyfinUser(
    @PrimaryKey(autoGenerate = true)
    val rowId: Int = 0,
    @ColumnInfo(index = true)
    val id: UUID,
    val name: String?,
    @ColumnInfo(index = true)
    val serverId: UUID,
    val accessToken: String?,
    val pin: String? = null,
) {
    val hasPin: Boolean get() = pin.isNotNullOrBlank()

    override fun toString(): String =
        "JellyfinUser(rowId=$rowId, id=$id, name=$name, serverId=$serverId, accessToken?=${accessToken.isNotNullOrBlank()}, pin?=${pin.isNotNullOrBlank()})"
}

data class JellyfinServerUsers(
    @Embedded val server: JellyfinServer,
    @Relation(
        parentColumn = "id",
        entityColumn = "serverId",
    )
    val users: List<JellyfinUser>,
)
