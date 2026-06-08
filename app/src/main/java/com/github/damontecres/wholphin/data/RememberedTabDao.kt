package com.github.damontecres.wholphin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.damontecres.wholphin.data.model.RememberedTab

@Dao
interface RememberedTabDao {
    @Query("SELECT * from RememberedTab WHERE userId=:userId AND itemId=:itemId")
    suspend fun getRememberedTab(
        userId: Int,
        itemId: String,
    ): RememberedTab?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(item: RememberedTab): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(items: List<RememberedTab>)
}
