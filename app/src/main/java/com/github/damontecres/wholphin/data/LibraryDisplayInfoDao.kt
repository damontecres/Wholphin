package com.github.damontecres.wholphin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.LibraryDisplayInfo
import com.github.damontecres.wholphin.ui.toServerString
import java.util.UUID

@Dao
interface LibraryDisplayInfoDao {
    fun getItem(
        user: JellyfinUser,
        itemId: UUID,
    ): LibraryDisplayInfo? = getItem(user.rowId, itemId.toServerString())

    fun getItem(
        user: JellyfinUser,
        itemId: String,
    ): LibraryDisplayInfo? = getItem(user.rowId, itemId)

    @Query("SELECT * from LibraryDisplayInfo WHERE userId=:userId AND itemId=:itemId")
    fun getItem(
        userId: Int,
        itemId: String,
    ): LibraryDisplayInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveItem(item: LibraryDisplayInfo): Long

    @Query("SELECT * from LibraryDisplayInfo WHERE userId=:userId")
    fun getItems(userId: Int): List<LibraryDisplayInfo>
}
