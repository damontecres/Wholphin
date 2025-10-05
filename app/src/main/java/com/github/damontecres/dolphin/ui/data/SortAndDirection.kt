package com.github.damontecres.dolphin.ui.data

import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

data class SortAndDirection(
    val sort: ItemSortBy,
    val direction: SortOrder,
) {
    fun flip() = copy(direction = direction.flip())
}

fun SortOrder.flip() = if (this == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING

val MovieSortOptions =
    listOf(
        ItemSortBy.SORT_NAME,
        ItemSortBy.PRODUCTION_YEAR,
        ItemSortBy.COMMUNITY_RATING,
        ItemSortBy.DATE_CREATED,
        ItemSortBy.DATE_PLAYED,
        ItemSortBy.PLAY_COUNT,
        ItemSortBy.STUDIO,
        ItemSortBy.OFFICIAL_RATING,
        ItemSortBy.RANDOM,
    )

val SeriesSortOptions =
    listOf(
        ItemSortBy.SORT_NAME,
        ItemSortBy.PREMIERE_DATE,
        ItemSortBy.COMMUNITY_RATING,
        ItemSortBy.DATE_CREATED,
        ItemSortBy.DATE_LAST_CONTENT_ADDED,
        ItemSortBy.DATE_PLAYED,
        ItemSortBy.STUDIO,
        ItemSortBy.OFFICIAL_RATING,
        ItemSortBy.RANDOM,
    )

val VideoSortOptions =
    listOf(
        ItemSortBy.SORT_NAME,
        ItemSortBy.DATE_CREATED,
        ItemSortBy.DATE_PLAYED,
        ItemSortBy.RANDOM,
    )
