@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.serializer.UUIDSerializer

@Entity(
    tableName = "seerr_servers",
    indices = [Index("url", unique = true)],
)
@Serializable
data class SeerrServer(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val url: String,
    val name: String? = null,
    val version: String? = null,
)

@Entity(
    tableName = "seerr_users",
    foreignKeys = [
        ForeignKey(
            entity = SeerrServer::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("serverId"),
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = JellyfinUser::class,
            parentColumns = arrayOf("rowId"),
            childColumns = arrayOf("jellyfinRowId"),
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    primaryKeys = ["jellyfinRowId", "serverId"],
)
data class SeerrUser(
    val jellyfinRowId: Int,
    val serverId: Int,
    val authMethod: SeerrAuthMethod,
    val username: String?,
    val password: String?,
    val credential: String?,
) {
    override fun toString(): String =
        "SeerrUser(jellyfinRowId=$jellyfinRowId, serverId=$serverId, authMethod=$authMethod, username=$username, password?=${password.isNotNullOrBlank()}, credential?=${credential.isNotNullOrBlank()})"
}

enum class SeerrAuthMethod {
    LOCAL,
    JELLYFIN,
    API_KEY,
}

data class SeerrServerUsers(
    @Embedded val server: SeerrServer,
    @Relation(
        parentColumn = "id",
        entityColumn = "serverId",
    )
    val users: List<SeerrUser>,
)
