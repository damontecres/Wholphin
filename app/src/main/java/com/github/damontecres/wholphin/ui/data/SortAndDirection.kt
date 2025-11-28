package com.github.damontecres.wholphin.ui.data

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

@Serializable
data class SortAndDirection(
    val sort: ItemSortBy,
    val direction: SortOrder,
) {
    fun flip() = copy(direction = direction.flip())

    companion object {
        val DEFAULT =
            SortAndDirection(
                ItemSortBy.SORT_NAME,
                SortOrder.ASCENDING,
            )
    }
}

fun SortOrder.flip() = if (this == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING

val MovieSortOptions =
    listOf(
        ItemSortBy.SORT_NAME,
        ItemSortBy.PREMIERE_DATE,
        ItemSortBy.DATE_CREATED,
        ItemSortBy.DATE_PLAYED,
        ItemSortBy.COMMUNITY_RATING,
        ItemSortBy.CRITIC_RATING,
        ItemSortBy.OFFICIAL_RATING,
        ItemSortBy.RUNTIME,
        ItemSortBy.PLAY_COUNT,
        ItemSortBy.RANDOM,
    )

val SeriesSortOptions =
    listOf(
        ItemSortBy.SORT_NAME,
        ItemSortBy.PREMIERE_DATE,
        ItemSortBy.DATE_CREATED,
        ItemSortBy.DATE_LAST_CONTENT_ADDED,
        ItemSortBy.DATE_PLAYED,
        ItemSortBy.COMMUNITY_RATING,
        ItemSortBy.OFFICIAL_RATING,
        ItemSortBy.RANDOM,
    )

val EpisodeSortOptions =
    listOf(
        ItemSortBy.SORT_NAME,
        ItemSortBy.DATE_CREATED,
        ItemSortBy.DATE_PLAYED,
        ItemSortBy.AIRED_EPISODE_ORDER,
        ItemSortBy.OFFICIAL_RATING,
        ItemSortBy.RUNTIME,
        ItemSortBy.RANDOM,
    )

val VideoSortOptions =
    listOf(
        ItemSortBy.SORT_NAME,
        ItemSortBy.DATE_CREATED,
        ItemSortBy.DATE_PLAYED,
        ItemSortBy.RUNTIME,
        ItemSortBy.PLAY_COUNT,
        ItemSortBy.RANDOM,
    )

val PlaylistSortOptions =
    listOf(
        ItemSortBy.SORT_NAME,
        ItemSortBy.PREMIERE_DATE,
        ItemSortBy.DATE_CREATED,
        ItemSortBy.DATE_PLAYED,
        ItemSortBy.COMMUNITY_RATING,
        ItemSortBy.CRITIC_RATING,
        ItemSortBy.RUNTIME,
        ItemSortBy.PLAY_COUNT,
        ItemSortBy.RANDOM,
    )

val BoxSetSortOptions =
    listOf(
        ItemSortBy.DEFAULT,
        ItemSortBy.SORT_NAME,
        ItemSortBy.PREMIERE_DATE,
        ItemSortBy.DATE_CREATED,
        ItemSortBy.DATE_PLAYED,
        ItemSortBy.COMMUNITY_RATING,
        ItemSortBy.CRITIC_RATING,
        ItemSortBy.OFFICIAL_RATING,
        ItemSortBy.RUNTIME,
        ItemSortBy.PLAY_COUNT,
        ItemSortBy.RANDOM,
    )

@StringRes
fun getStringRes(sort: ItemSortBy): Int =
    when (sort) {
        ItemSortBy.SORT_NAME,
        ItemSortBy.SERIES_SORT_NAME,
        -> R.string.sort_by_name

        ItemSortBy.PREMIERE_DATE -> R.string.sort_by_date_released
        ItemSortBy.DATE_CREATED -> R.string.sort_by_date_added
        ItemSortBy.DATE_LAST_CONTENT_ADDED -> R.string.sort_by_date_episode_added

        ItemSortBy.DATE_PLAYED,
        ItemSortBy.SERIES_DATE_PLAYED,
        -> R.string.sort_by_date_played

        ItemSortBy.RANDOM -> R.string.sort_by_random
        ItemSortBy.COMMUNITY_RATING -> R.string.community_rating
        ItemSortBy.CRITIC_RATING -> R.string.critic_rating
        ItemSortBy.OFFICIAL_RATING -> R.string.official_rating
        ItemSortBy.PLAY_COUNT -> R.string.play_count
        ItemSortBy.AIRED_EPISODE_ORDER -> R.string.aired_episode_order
        ItemSortBy.RUNTIME -> R.string.runtime_sort
        ItemSortBy.DEFAULT -> R.string.default_track
        else -> throw IllegalArgumentException("Unsupported sort option: $sort")
    }
