package com.github.damontecres.dolphin.ui.data

import androidx.annotation.StringRes
import com.github.damontecres.dolphin.R
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
        ItemSortBy.PREMIERE_DATE,
        ItemSortBy.DATE_CREATED,
        ItemSortBy.DATE_PLAYED,
        ItemSortBy.RANDOM,
    )

val SeriesSortOptions =
    listOf(
        ItemSortBy.SORT_NAME,
        ItemSortBy.PREMIERE_DATE,
        ItemSortBy.DATE_CREATED,
        ItemSortBy.DATE_LAST_CONTENT_ADDED,
        ItemSortBy.DATE_PLAYED,
        ItemSortBy.RANDOM,
    )

val VideoSortOptions =
    listOf(
        ItemSortBy.SORT_NAME,
        ItemSortBy.DATE_CREATED,
        ItemSortBy.DATE_PLAYED,
        ItemSortBy.RANDOM,
    )

@StringRes
fun getStringRes(sort: ItemSortBy): Int =
    when (sort) {
        ItemSortBy.SORT_NAME -> R.string.sort_by_name
        ItemSortBy.PREMIERE_DATE -> R.string.sort_by_date_released
        ItemSortBy.DATE_CREATED -> R.string.sort_by_date_added
        ItemSortBy.DATE_LAST_CONTENT_ADDED -> R.string.sort_by_date_episode_added
        ItemSortBy.DATE_PLAYED -> R.string.sort_by_date_played
        ItemSortBy.RANDOM -> R.string.sort_by_random
        else -> throw IllegalArgumentException("Unsupported sort option: $sort")
    }
