package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.api.seerr.model.TvShowStatus
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.filter.CommunityRatingFilter
import com.github.damontecres.wholphin.data.filter.DecadeFilter
import com.github.damontecres.wholphin.data.filter.DiscoverFilterBy
import com.github.damontecres.wholphin.data.filter.DiscoverMovieContentRatingFilter
import com.github.damontecres.wholphin.data.filter.DiscoverMovieGenreFilter
import com.github.damontecres.wholphin.data.filter.DiscoverMovieStudiosFilter
import com.github.damontecres.wholphin.data.filter.DiscoverRatingFilter
import com.github.damontecres.wholphin.data.filter.DiscoverTvContentRatingFilter
import com.github.damontecres.wholphin.data.filter.DiscoverTvGenreFilter
import com.github.damontecres.wholphin.data.filter.DiscoverTvStatusFilter
import com.github.damontecres.wholphin.data.filter.DiscoverTvStudiosFilter
import com.github.damontecres.wholphin.data.filter.FavoriteFilter
import com.github.damontecres.wholphin.data.filter.FilterBy
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.FilterVideoType
import com.github.damontecres.wholphin.data.filter.GenreFilter
import com.github.damontecres.wholphin.data.filter.OfficialRatingFilter
import com.github.damontecres.wholphin.data.filter.PlayedFilter
import com.github.damontecres.wholphin.data.filter.StudioFilter
import com.github.damontecres.wholphin.data.filter.VideoTypeFilter
import com.github.damontecres.wholphin.data.filter.YearFilter
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import com.github.damontecres.wholphin.ui.showToast
import com.mayakapps.kache.InMemoryKache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.filterApi
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.localizationApi
import org.jellyfin.sdk.api.client.extensions.studiosApi
import org.jellyfin.sdk.api.client.extensions.yearsApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.util.TreeSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours

/**
 * Get the possible values for filters in a library
 */
@Singleton
class FilterOptionCache
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoCoroutineScope private val ioCoroutineScope: CoroutineScope,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val seerrService: SeerrService,
    ) {
        private val cache =
            InMemoryKache<FilterOptionCacheKey, List<FilterValueOption>>(16) {
                creationScope = ioCoroutineScope
                expireAfterWriteDuration = 1.hours
            }

        /**
         * Gets the possible values for a filter
         *
         * For example, the possible genres in the parent ID
         */
        suspend fun getFilterOptionValues(
            parentId: UUID?,
            filterOption: FilterBy<*, *>,
        ): List<FilterValueOption> {
            val cacheKey = FilterOptionCacheKey(serverRepository.currentUser?.id, parentId, filterOption)
            return try {
                cache
                    .getOrPut(cacheKey) { (userId, parentId, filterOption) ->
                        getFilterOptionValues(userId, parentId, filterOption)
                    }.orEmpty()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error fetching options for %s", filterOption)
                showToast(context, "Error occurred: ${ex.localizedMessage}")
                emptyList()
            }
        }

        private suspend fun getFilterOptionValues(
            userId: UUID?,
            parentId: UUID?,
            filterOption: FilterBy<*, *>,
        ) = when (filterOption) {
            GenreFilter -> {
                api.genresApi
                    .getGenres(
                        parentId = parentId,
                        userId = userId,
                    ).content.items
                    .map { FilterValueOption(it.name ?: "", it.id) }
            }

            StudioFilter -> {
                api.studiosApi
                    .getStudios(
                        parentId = parentId,
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.SERIES),
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
                val ratings =
                    api.localizationApi
                        .getParentalRatings()
                        .content
                        .associate {
                            it.name to it.value
                        }
                api.filterApi
                    .getQueryFiltersLegacy(
                        parentId = parentId,
                        userId = userId,
                    ).content.officialRatings
                    ?.map { r ->
                        val value = ratings[r] ?: 0
                        FilterValueOption(r, value)
                    }?.sortedBy { it.value as Int }
                    .orEmpty()
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

            is DiscoverFilterBy -> {
                getDiscoverFilterOptionValues(filterOption)
            }
        }

        private suspend fun getDiscoverFilterOptionValues(filterOption: DiscoverFilterBy<*>): List<FilterValueOption> {
            if (seerrService.active.firstOrNull() != true) {
                return emptyList()
            }
            return when (filterOption) {
                DiscoverMovieGenreFilter -> {
                    seerrService.api.searchApi
                        .discoverGenresliderMovieGet()
                        .filter { it.name != null && it.id != null }
                        .map {
                            FilterValueOption(it.name!!, it.id!!)
                        }
                }

                DiscoverTvGenreFilter -> {
                    seerrService.api.searchApi
                        .discoverGenresliderTvGet()
                        .filter { it.name != null && it.id != null }
                        .map {
                            FilterValueOption(it.name!!, it.id!!)
                        }
                }

                DiscoverMovieStudiosFilter -> {
                    // TODO region
                    seerrService.api.otherApi
                        .watchprovidersMoviesGet("US")
                        .filter { it.name != null && it.id != null }
                        // TODO not what this is for
//                        .sortedBy { it.displayPriority }
                        .map {
                            // TODO logo?
                            FilterValueOption(it.name!!, it.id!!)
                        }
                }

                DiscoverTvStudiosFilter -> {
                    // TODO region
                    seerrService.api.otherApi
                        .watchprovidersTvGet("US")
                        .filter { it.name != null && it.id != null }
//                        .sortedBy { it.displayPriority }
                        .map {
                            FilterValueOption(it.name!!, it.id!!)
                        }
                }

                DiscoverTvStatusFilter -> {
                    TvShowStatus.entries.map {
                        FilterValueOption(it.value, it)
                    }
                }

                DiscoverMovieContentRatingFilter -> {
                    listOf("NR", "G", "PG", "PG-13", "R", "NC-17")
                        .map { FilterValueOption(it, it) }
                }

                DiscoverTvContentRatingFilter -> {
                    listOf("NR", "TV-Y", "TV-Y7", "TV-G", "TV-PG", "TV-14", "TV-MA")
                        .map { FilterValueOption(it, it) }
                }

                DiscoverRatingFilter -> {
                    (1..10).map {
                        FilterValueOption("$it", it)
                    }
                }
            }
        }

        private data class FilterOptionCacheKey(
            val userId: UUID?,
            val parentId: UUID?,
            val filterOption: FilterBy<*, *>,
        )
    }
