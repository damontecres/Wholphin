package com.github.damontecres.wholphin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.damontecres.wholphin.data.model.RememberedTab
import org.jellyfin.sdk.model.UUID

@Dao
interface RememberedTabDao {
    @Query("SELECT * from RememberedTab WHERE userId=:userId AND itemId=:itemId")
    suspend fun getRememberedTab(
        userId: Int,
        itemId: UUID,
    ): RememberedTab?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(item: RememberedTab): Long
}
