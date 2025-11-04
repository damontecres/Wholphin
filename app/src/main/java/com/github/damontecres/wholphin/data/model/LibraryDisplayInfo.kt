@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.toServerString
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

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
    indices = [Index("userId", "itemId", unique = true)],
)
@Serializable
data class LibraryDisplayInfo(
    val userId: Int,
    val itemId: String,
    val sort: ItemSortBy,
    val direction: SortOrder,
) {
    constructor(
        userId: Int,
        itemId: UUID,
        sort: ItemSortBy,
        direction: SortOrder,
    ) : this(userId, itemId.toServerString(), sort, direction)

    @Ignore @Transient
    val sortAndDirection = SortAndDirection(sort, direction)
}
