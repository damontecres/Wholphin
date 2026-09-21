package com.github.damontecres.wholphin.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.damontecres.wholphin.data.model.PlaybackLanguageChoice
import java.util.UUID

@Deprecated("Use SeriesTrackChoiceDao")
@Dao
interface PlaybackLanguageChoiceDao {
    @Deprecated("Use SeriesTrackChoiceDao")
    @Query("SELECT * FROM PlaybackLanguageChoice WHERE userId=:userId AND seriesId=:seriesId")
    suspend fun get(
        userId: Int,
        seriesId: UUID,
    ): PlaybackLanguageChoice?

    @Deprecated("Use SeriesTrackChoiceDao")
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(plc: PlaybackLanguageChoice): Long

    @Deprecated("Use SeriesTrackChoiceDao")
    @Delete
    fun delete(plc: PlaybackLanguageChoice)

    @Query("SELECT * FROM PlaybackLanguageChoice ORDER BY userId, seriesId LIMIT :limit OFFSET :offset")
    suspend fun getAll(
        limit: Int,
        offset: Int,
    ): List<PlaybackLanguageChoice>
}
