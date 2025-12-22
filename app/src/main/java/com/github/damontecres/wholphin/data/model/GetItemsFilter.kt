@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.data.filter.FilterVideoType
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
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
data class CollectionFolderFilter(
    val nameOverride: String? = null,
    val filter: GetItemsFilter = GetItemsFilter(),
    /**
     * Whether to use the libray's saved sort & filter
     */
    val useSavedLibraryDisplayInfo: Boolean = true,
)

@Serializable
data class GetItemsFilter(
    val favorite: Boolean? = null,
    val genres: List<UUID>? = null,
    val minCommunityRating: Double? = null,
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
    /**
     * Returns how many of filters are actually being used in this [GetItemsFilter]
     */
    fun countFilters(filterOptions: List<ItemFilterBy<*>>): Int {
        var count = 0
        filterOptions.forEach {
            if (it.get(this) != null) count++
        }
        return count
    }

    /**
     * Clear all of the values for the given filters
     */
    fun delete(filterOptions: List<ItemFilterBy<*>>): GetItemsFilter {
        var newFilter = this
        filterOptions.forEach {
            newFilter = it.set(null, newFilter)
        }
        return newFilter
    }

    /**
     * Add the filtering from this into the [GetItemsRequest], overwriting the fields
     *
     * @param req the [GetItemsRequest]
     * @param overwriteIncludeTypes whether the includeItemTypes field should be overwritten (used from this) or used as-is from the [GetItemsRequest]
     *
     */
    fun applyTo(
        req: GetItemsRequest,
        overwriteIncludeTypes: Boolean = true,
    ) = req.copy(
        includeItemTypes = if (overwriteIncludeTypes) includeItemTypes else req.includeItemTypes,
        isFavorite = favorite,
        genreIds = genres,
        minCommunityRating = minCommunityRating,
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
            minCommunityRating = minCommunityRating ?: filter.minCommunityRating,
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
