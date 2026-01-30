package com.github.damontecres.wholphin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.damontecres.wholphin.data.model.PlaybackEffect
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

@Dao
interface PlaybackEffectDao {
    @Query("SELECT * FROM playback_effects WHERE jellyfinUserRowId=:jellyfinUserRowId AND itemId=:itemId AND type=:type")
    suspend fun getPlaybackEffect(
        jellyfinUserRowId: Int,
        itemId: UUID,
        type: BaseItemKind,
    ): PlaybackEffect?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playbackEffect: PlaybackEffect)
}
