@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

@Serializable
data class GetItemsFilter(
    val favorite: Boolean? = null,
    val genres: List<UUID>? = null,
    val minCriticRating: Double? = null,
    val persons: List<UUID>? = null,
    val played: Boolean? = null,
    val studios: List<UUID>? = null,
    val tags: List<String>? = null,
    val includeItemTypes: List<BaseItemKind>? = null,
) {
    fun applyTo(req: GetItemsRequest) =
        req.copy(
//            includeItemTypes = includeItemTypes,
            isFavorite = favorite,
            genreIds = genres,
            minCriticRating = minCriticRating,
            personIds = persons,
            isPlayed = played,
            studioIds = studios,
            tags = tags,
        )
}
