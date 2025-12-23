package com.github.damontecres.wholphin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrServerUsers
import com.github.damontecres.wholphin.data.model.SeerrUser

@Dao
interface SeerrServerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addServer(server: SeerrServer): Long

    @Update
    suspend fun updateServer(server: SeerrServer): Int

    @Transaction
    suspend fun addOrUpdateServer(server: SeerrServer) {
        val result = addServer(server)
        if (result == -1L) {
            updateServer(server)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addUser(user: SeerrUser): Long

    suspend fun updateUser(user: SeerrUser) = addUser(user)

    @Query("SELECT * FROM seerr_users WHERE serverId = :serverId AND jellyfinUserRowId = :jellyfinUserRowId")
    suspend fun getUser(
        serverId: Int,
        jellyfinUserRowId: Int,
    ): SeerrUser?

    @Query("SELECT * FROM seerr_users WHERE jellyfinUserRowId = :jellyfinUserRowId")
    suspend fun getUsersByJellyfinUser(jellyfinUserRowId: Int): List<SeerrUser>

    @Query("DELETE FROM seerr_servers WHERE id = :serverId")
    suspend fun deleteServer(serverId: Int)

    @Query("DELETE FROM seerr_users WHERE serverId = :serverId AND jellyfinUserRowId = :jellyfinUserRowId")
    suspend fun deleteUser(
        serverId: Int,
        jellyfinUserRowId: Int,
    )

    suspend fun deleteUser(user: SeerrUser) = deleteUser(user.serverId, user.jellyfinUserRowId)

    @Transaction
    @Query("SELECT * FROM seerr_servers")
    suspend fun getServers(): List<SeerrServerUsers>

    @Transaction
    @Query("SELECT * FROM seerr_servers WHERE id = :serverId")
    suspend fun getServer(serverId: Int): SeerrServerUsers?

    @Transaction
    @Query("SELECT * FROM seerr_servers WHERE url = :url")
    suspend fun getServer(url: String): SeerrServerUsers?
}
