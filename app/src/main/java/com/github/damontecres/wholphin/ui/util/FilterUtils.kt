package com.github.damontecres.wholphin.ui.util

import com.github.damontecres.wholphin.data.filter.CommunityRatingFilter
import com.github.damontecres.wholphin.data.filter.DecadeFilter
import com.github.damontecres.wholphin.data.filter.FavoriteFilter
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.FilterVideoType
import com.github.damontecres.wholphin.data.filter.GenreFilter
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.filter.OfficialRatingFilter
import com.github.damontecres.wholphin.data.filter.PlayedFilter
import com.github.damontecres.wholphin.data.filter.VideoTypeFilter
import com.github.damontecres.wholphin.data.filter.YearFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.localizationApi
import org.jellyfin.sdk.api.client.extensions.yearsApi
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.util.TreeSet
import java.util.UUID

object FilterUtils {
    /**
     * Gets the possible values for a filter
     *
     * For example, the possible genres in the parent ID
     */
    suspend fun getFilterOptionValues(
        api: ApiClient,
        userId: UUID?,
        parentId: UUID?,
        filterOption: ItemFilterBy<*>,
    ): List<FilterValueOption> =
        withContext(Dispatchers.IO) {
            try {
                when (filterOption) {
                    GenreFilter -> {
                        api.genresApi
                            .getGenres(
                                parentId = parentId,
                                userId = userId,
                            ).content.items
                            .map { FilterValueOption(it.name ?: "", it.id) }
                    }

                    FavoriteFilter,
                    PlayedFilter,
                    -> {
                        listOf(
                            FilterValueOption("True", null),
                            FilterValueOption("False", null),
                        )
                    }

                    OfficialRatingFilter -> {
                        api.localizationApi.getParentalRatings().content.map {
                            FilterValueOption(it.name ?: "", it.value)
                        }
                    }

                    VideoTypeFilter -> {
                        FilterVideoType.entries.map {
                            FilterValueOption(it.readable, it)
                        }
                    }

                    YearFilter -> {
                        api.yearsApi
                            .getYears(
                                parentId = parentId,
                                userId = userId,
                                sortBy = listOf(ItemSortBy.SORT_NAME),
                                sortOrder = listOf(SortOrder.ASCENDING),
                            ).content.items
                            .mapNotNull {
                                it.name?.toIntOrNull()?.let { FilterValueOption(it.toString(), it) }
                            }
                    }

                    DecadeFilter -> {
                        val items = TreeSet<Int>()
                        api.yearsApi
                            .getYears(
                                parentId = parentId,
                                userId = userId,
                                sortBy = listOf(ItemSortBy.SORT_NAME),
                                sortOrder = listOf(SortOrder.ASCENDING),
                            ).content.items
                            .mapNotNullTo(items) {
                                it.name
                                    ?.toIntOrNull()
                                    ?.div(10)
                                    ?.times(10)
                            }
                        items.toList().sorted().map { FilterValueOption("$it's", it) }
                    }

                    CommunityRatingFilter -> {
                        (1..10).map {
                            FilterValueOption("$it", it)
                        }
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Exception get filter value options for $filterOption")
                listOf()
            }
        }
}
