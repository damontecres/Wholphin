@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

/**
 * Stores the language choices for a series so they can be applied automatically to other episodes
 * without the user needing to explicitly choose the tracks
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
    primaryKeys = ["userId", "seriesId"],
)
@Serializable
data class PlaybackLanguageChoice(
    val userId: Int,
    val seriesId: UUID,
    val itemId: UUID? = null,
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    val subtitlesDisabled: Boolean? = null,
)
