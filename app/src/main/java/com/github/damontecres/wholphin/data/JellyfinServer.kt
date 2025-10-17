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

@Entity(tableName = "servers")
data class JellyfinServer(
    @PrimaryKey val id: String,
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
    val id: String,
    val name: String?,
    @ColumnInfo(index = true)
    val serverId: String,
    val accessToken: String?,
)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addUser(user: JellyfinUser)

    @Query("DELETE FROM servers WHERE id = :serverId")
    fun deleteServer(serverId: String)

    @Query("DELETE FROM users WHERE serverId = :serverId AND id = :userId")
    fun deleteUser(
        serverId: String,
        userId: String,
    )

    @Transaction
    @Query("SELECT * FROM servers")
    fun getServers(): List<JellyfinServerUsers>

    @Transaction
    @Query("SELECT * FROM servers WHERE id = :serverId")
    fun getServer(serverId: String): JellyfinServerUsers?
}
