package com.github.damontecres.wholphin.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

enum class NavPinType {
    PINNED,
    UNPINNED,
}

/**
 * Stores preference information about nav drawer items such as its order and whether to show or put in More
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = JellyfinUser::class,
            parentColumns = arrayOf("rowId"),
            childColumns = arrayOf("userId"),
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    primaryKeys = ["userId", "itemId"],
)
data class NavDrawerPinnedItem(
    val userId: Int,
    val itemId: String,
    val type: NavPinType,
    @ColumnInfo(defaultValue = "-1") val order: Int,
)
