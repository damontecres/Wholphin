package com.github.damontecres.wholphin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.damontecres.wholphin.data.model.ItemPlayback
import java.util.UUID

@Dao
interface ItemPlaybackDao {
    @Query("SELECT * from ItemPlayback WHERE serverId=:serverId AND userId=:userId AND itemId=:itemId")
    fun getItem(
        serverId: UUID,
        userId: UUID,
        itemId: UUID,
    ): ItemPlayback?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveItem(item: ItemPlayback)
}
