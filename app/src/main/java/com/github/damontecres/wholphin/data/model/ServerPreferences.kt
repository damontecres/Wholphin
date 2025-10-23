package com.github.damontecres.wholphin.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import com.github.damontecres.wholphin.data.JellyfinUser

enum class NavPinType {
    PINNED,
    UNPINNED,
}

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
)
