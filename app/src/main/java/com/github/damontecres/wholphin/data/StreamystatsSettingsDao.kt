package com.github.damontecres.wholphin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.damontecres.wholphin.data.model.StreamystatsSettings
import java.util.UUID

@Dao
interface StreamystatsSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(settings: StreamystatsSettings)

    @Query(
        """
        SELECT * FROM streamystats_settings
        WHERE jellyfinUserRowId = :jellyfinUserRowId
          AND jellyfinServerId = :jellyfinServerId
        """,
    )
    suspend fun get(
        jellyfinUserRowId: Int,
        jellyfinServerId: UUID,
    ): StreamystatsSettings?

    @Query(
        """
        DELETE FROM streamystats_settings
        WHERE jellyfinUserRowId = :jellyfinUserRowId
          AND jellyfinServerId = :jellyfinServerId
        """,
    )
    suspend fun delete(
        jellyfinUserRowId: Int,
        jellyfinServerId: UUID,
    ): Int
}
