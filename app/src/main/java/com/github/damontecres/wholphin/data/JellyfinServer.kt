package com.github.damontecres.wholphin.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
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

@Dao
interface JellyfinServerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun addServer(server: JellyfinServer): Long

    @Update
    fun updateServer(server: JellyfinServer): Int

    @Transaction
    fun addOrUpdateServer(server: JellyfinServer) {
        val result = addServer(server)
        if (result == -1L) {
            updateServer(server)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun addUser(user: JellyfinUser): Long

    @Update
    fun updateUser(user: JellyfinUser): Int

    @Transaction
    fun addOrUpdateUser(user: JellyfinUser): JellyfinUser {
        val result = addUser(user)
        if (result == -1L) {
            val toSave =
                if (user.rowId <= 0) {
                    val temp = getUser(user.serverId, user.id)!!
                    user.copy(rowId = temp.rowId)
                } else {
                    user
                }
            updateUser(toSave)
            return toSave
        }
        return user.copy(rowId = result.toInt())
    }

    @Query("SELECT * FROM users WHERE serverId = :serverId AND id = :userId")
    fun getUser(
        serverId: UUID,
        userId: UUID,
    ): JellyfinUser?

    @Query("DELETE FROM servers WHERE id = :serverId")
    fun deleteServer(serverId: UUID)

    @Query("DELETE FROM users WHERE serverId = :serverId AND id = :userId")
    fun deleteUser(
        serverId: UUID,
        userId: UUID,
    )

    @Transaction
    @Query("SELECT * FROM servers")
    fun getServers(): List<JellyfinServerUsers>

    @Transaction
    @Query("SELECT * FROM servers WHERE id = :serverId")
    fun getServer(serverId: UUID): JellyfinServerUsers?
}
