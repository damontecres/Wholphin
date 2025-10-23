package com.github.damontecres.wholphin.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.damontecres.wholphin.data.JellyfinUser
import com.github.damontecres.wholphin.ui.toServerString
import org.jellyfin.sdk.model.api.BaseItemDto

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
    indices = [Index("userId", "itemId", unique = true)],
)
data class NavDrawerPinnedItem(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,
    val userId: Int,
    val itemId: String,
    val type: NavPinType,
) {
    companion object {
        fun idFor(dto: BaseItemDto) = "s_${dto.id.toServerString()}"

        const val FAVORITES_ID = "a_favorites"
    }
}
