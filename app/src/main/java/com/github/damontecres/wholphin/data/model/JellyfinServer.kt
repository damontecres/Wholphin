package com.github.damontecres.wholphin.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import java.util.UUID

@Entity(tableName = "servers")
data class JellyfinServer(
    @PrimaryKey val id: UUID,
    val name: String?,
    val url: String,
)

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
data class JellyfinUser(
    @PrimaryKey(autoGenerate = true)
    val rowId: Int = 0,
    @ColumnInfo(index = true)
    val id: UUID,
    val name: String?,
    @ColumnInfo(index = true)
    val serverId: UUID,
    val accessToken: String?,
) {
    override fun toString(): String =
        "JellyfinUser(rowId=$rowId, id=$id, name=$name, serverId=$serverId, accessToken=${accessToken.isNotNullOrBlank()})"
}

data class JellyfinServerUsers(
    @Embedded val server: JellyfinServer,
    @Relation(
        parentColumn = "id",
        entityColumn = "serverId",
    )
    val users: List<JellyfinUser>,
)
