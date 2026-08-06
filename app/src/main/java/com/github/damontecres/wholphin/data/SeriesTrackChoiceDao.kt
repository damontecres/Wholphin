package com.github.damontecres.wholphin.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.damontecres.wholphin.data.model.SeriesTrackChoice
import com.github.damontecres.wholphin.data.model.SeriesTrackChoiceType
import java.util.UUID

@Dao
interface SeriesTrackChoiceDao {
    @Query(
        "SELECT * from SeriesTrackChoice WHERE userId=:userId AND type=:type AND (parentId=:seasonId OR parentId=:seriesId) ORDER BY parentId=:seasonId DESC",
    )
    suspend fun get(
        userId: Int,
        seasonId: UUID,
        seriesId: UUID,
        type: SeriesTrackChoiceType,
    ): List<SeriesTrackChoice>

    @Query("SELECT * from SeriesTrackChoice WHERE userId=:userId AND type=:type AND parentId=:seasonId")
    suspend fun getBySeasonId(
        userId: Int,
        seasonId: UUID,
        type: SeriesTrackChoiceType,
    ): List<SeriesTrackChoice>

    @Query("SELECT * from SeriesTrackChoice WHERE userId=:userId AND type=:type AND parentId=:seriesId")
    suspend fun getBySeriesId(
        userId: Int,
        seriesId: UUID,
        type: SeriesTrackChoiceType,
    ): List<SeriesTrackChoice>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(stc: SeriesTrackChoice): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(stc: List<SeriesTrackChoice>): List<Long>

    @Delete
    fun delete(stc: List<SeriesTrackChoice>)

    @Query("SELECT * from SeriesTrackChoice WHERE userId=:userId")
    fun getAll(userId: Int): List<SeriesTrackChoice>
}
