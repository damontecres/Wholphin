package com.github.damontecres.wholphin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.damontecres.wholphin.data.model.PlaybackLanguageChoice
import java.util.UUID

@Dao
interface PlaybackLanguageChoiceDao {
    @Query("SELECT * FROM PlaybackLanguageChoice WHERE userId=:userId AND seriesId=:seriesId")
    suspend fun get(
        userId: Int,
        seriesId: UUID,
    ): PlaybackLanguageChoice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(plc: PlaybackLanguageChoice): Long
}
