package com.github.damontecres.wholphin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.damontecres.wholphin.data.model.ItemPlayback
import java.util.UUID

@Dao
interface ItemPlaybackDao {
    fun getItem(
        user: JellyfinUser,
        itemId: UUID,
    ): ItemPlayback? = getItem(user.rowId, itemId)

    @Query("SELECT * from ItemPlayback WHERE userId=:userId AND itemId=:itemId")
    fun getItem(
        userId: Int,
        itemId: UUID,
    ): ItemPlayback?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveItem(item: ItemPlayback): Long
}
