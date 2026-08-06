package com.github.damontecres.wholphin.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.damontecres.wholphin.data.model.SeriesTrackChoice
import java.util.UUID

@Dao
interface SeriesTrackChoiceDao {
    @Query(
        "SELECT * from SeriesTrackChoice WHERE userId=:userId AND (parentId=:seasonId OR parentId=:seriesId) ORDER BY parentId=:seasonId DESC",
    )
    suspend fun get(
        userId: Int,
        seasonId: UUID,
        seriesId: UUID,
    ): List<SeriesTrackChoice>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(stc: SeriesTrackChoice): Long

    @Delete
    fun delete(stc: SeriesTrackChoice)
}
