package com.github.damontecres.wholphin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinServerUsers
import com.github.damontecres.wholphin.data.model.JellyfinUser
import java.util.UUID

@Dao
interface JellyfinServerDao {
    @Insert(onConflict = OnConflictStrategy.Companion.IGNORE)
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

    @Insert(onConflict = OnConflictStrategy.Companion.IGNORE)
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
