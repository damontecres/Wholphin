package com.github.damontecres.wholphin.data

import androidx.room.Dao
import androidx.room.Query
import com.github.damontecres.wholphin.data.model.NavDrawerPinnedItem

@Dao
interface ServerPreferencesDao {
    fun getNavDrawerPinnedItems(user: JellyfinUser): List<NavDrawerPinnedItem> = getNavDrawerPinnedItems(user.rowId)

    @Query("SELECT * from NavDrawerPinnedItem WHERE userId=:userId")
    fun getNavDrawerPinnedItems(userId: Int): List<NavDrawerPinnedItem>
}
