@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.data.filter.FilterVideoType
import com.github.damontecres.wholphin.ui.letNotEmpty
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.VideoType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetPersonsRequest
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

@Serializable
data class GetItemsFilter(
    val favorite: Boolean? = null,
    val genres: List<UUID>? = null,
    val minCriticRating: Double? = null,
    val officialRatings: List<String>? = null,
    val persons: List<UUID>? = null,
    val played: Boolean? = null,
    val studios: List<UUID>? = null,
    val tags: List<String>? = null,
    val includeItemTypes: List<BaseItemKind>? = null,
    val videoTypes: List<FilterVideoType>? = null,
    val years: List<Int>? = null,
    val decades: List<Int>? = null,
    val override: GetItemsFilterOverride = GetItemsFilterOverride.NONE,
) {
    val hasFilters: Boolean
        get() =
            favorite != null ||
                genres != null ||
                minCriticRating != null ||
                officialRatings != null ||
                persons != null ||
                played != null ||
                studios != null ||
                tags != null ||
                videoTypes != null ||
                years != null ||
                decades != null

    fun applyTo(req: GetItemsRequest) =
        req.copy(
            includeItemTypes = includeItemTypes,
            isFavorite = favorite,
            genreIds = genres,
            minCriticRating = minCriticRating,
            personIds = persons,
            isPlayed = played,
            studioIds = studios,
            tags = tags,
            officialRatings = officialRatings,
            years =
                buildSet {
                    years?.letNotEmpty(::addAll)
                    decades?.forEach { addAll(it..<(it + 10)) }
                },
            is4k =
                videoTypes?.letNotEmpty {
                    videoTypes.contains(FilterVideoType.FOUR_K).takeIf { it }
                },
            isHd =
                videoTypes?.letNotEmpty {
                    if (videoTypes.contains(FilterVideoType.HD)) {
                        true
                    } else if (videoTypes.contains(FilterVideoType.SD)) {
                        false
                    } else {
                        null
                    }
                },
            is3d =
                videoTypes?.letNotEmpty {
                    videoTypes.contains(FilterVideoType.THREE_D).takeIf { it }
                },
            videoTypes =
                videoTypes?.letNotEmpty {
                    it.mapNotNull {
                        when (it) {
                            FilterVideoType.FOUR_K,
                            FilterVideoType.HD,
                            FilterVideoType.SD,
                            FilterVideoType.THREE_D,
                            -> null

                            FilterVideoType.BLU_RAY -> VideoType.BLU_RAY
                            FilterVideoType.DVD -> VideoType.DVD
                        }
                    }
                },
        )

    fun applyTo(req: GetPersonsRequest) =
        req.copy(
            isFavorite = favorite,
        )

    fun merge(filter: GetItemsFilter): GetItemsFilter =
        this.copy(
            favorite = favorite ?: filter.favorite,
            genres = genres ?: filter.genres,
            minCriticRating = minCriticRating ?: filter.minCriticRating,
            officialRatings = officialRatings ?: filter.officialRatings,
            persons = persons ?: filter.persons,
            played = played ?: filter.played,
            studios = studios ?: filter.studios,
            tags = tags ?: filter.tags,
            includeItemTypes = includeItemTypes ?: filter.includeItemTypes,
            videoTypes = videoTypes ?: filter.videoTypes,
            years = years ?: filter.years,
            decades = decades ?: filter.decades,
            override = override,
        )
}

enum class GetItemsFilterOverride {
    NONE,
    PERSON,
}
