package com.github.damontecres.wholphin.util

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.model.MovieResult
import com.github.damontecres.wholphin.api.seerr.model.TvResult
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.services.SeerrApi
import com.github.damontecres.wholphin.services.SeerrSearchResult
import kotlinx.coroutines.CoroutineScope

/**
 * A [RequestPager] for Seerr server queries
 */
class DiscoverRequestPager<T>(
    private val api: SeerrApi,
    val requestHandler: DiscoverRequestHandler<T>,
    val transform: suspend (T) -> DiscoverItem,
    scope: CoroutineScope,
    pageSize: Int = SEERR_PAGE_SIZE,
    cacheSize: Long = 16,
) : RequestPager<DiscoverItem>(scope, pageSize, cacheSize) {
    override suspend fun init(initialPosition: Int): DiscoverRequestPager<T> = super.init(initialPosition) as DiscoverRequestPager<T>

    override suspend fun fetchPage(
        pageNumber: Int,
        includeTotalCount: Boolean,
    ): QueryResult<DiscoverItem> {
        val result = requestHandler.execute(api, pageNumber + 1) // Seerr pages are 1-indexed
        val transformed = result.items.map { transform.invoke(it) }
        return QueryResult(transformed, result.totalCount)
    }
}

const val SEERR_PAGE_SIZE = 20

enum class DiscoverRequestType(
    @param:StringRes val stringRes: Int,
) {
    DISCOVER_TV(R.string.discover_tv),
    DISCOVER_MOVIES(R.string.discover_movies),
    TRENDING(R.string.trending),
    UPCOMING_TV(R.string.upcoming_tv),
    UPCOMING_MOVIES(R.string.upcoming_movies),
    UNKNOWN(R.string.unknown),
}

/**
 * Specifies how a [RequestPager] should prepare and execute API calls
 */
interface DiscoverRequestHandler<T> {
    suspend fun execute(
        api: SeerrApi,
        pageNumber: Int,
    ): QueryResult<T>
}

val DiscoverTvRequestHandler =
    object : DiscoverRequestHandler<TvResult> {
        override suspend fun execute(
            api: SeerrApi,
            pageNumber: Int,
        ): QueryResult<TvResult> =
            api.api.searchApi.discoverTvGet(page = pageNumber).let {
                QueryResult(it.results.orEmpty(), it.totalResults ?: 0)
            }
    }

val DiscoverMovieRequestHandler =
    object : DiscoverRequestHandler<MovieResult> {
        override suspend fun execute(
            api: SeerrApi,
            pageNumber: Int,
        ): QueryResult<MovieResult> =
            api.api.searchApi.discoverMoviesGet(page = pageNumber).let {
                QueryResult(it.results.orEmpty(), it.totalResults ?: 0)
            }
    }

val TrendingRequestHandler =
    object : DiscoverRequestHandler<SeerrSearchResult> {
        override suspend fun execute(
            api: SeerrApi,
            pageNumber: Int,
        ): QueryResult<SeerrSearchResult> =
            api.api.searchApi.discoverTrendingGet(page = pageNumber).let {
                QueryResult(it.results.orEmpty(), it.totalResults ?: 0)
            }
    }

val UpcomingTvRequestHandler =
    object : DiscoverRequestHandler<TvResult> {
        override suspend fun execute(
            api: SeerrApi,
            pageNumber: Int,
        ): QueryResult<TvResult> =
            api.api.searchApi.discoverTvUpcomingGet(page = pageNumber).let {
                QueryResult(it.results.orEmpty(), it.totalResults ?: 0)
            }
    }

val UpcomingMovieRequestHandler =
    object : DiscoverRequestHandler<MovieResult> {
        override suspend fun execute(
            api: SeerrApi,
            pageNumber: Int,
        ): QueryResult<MovieResult> =
            api.api.searchApi.discoverMoviesUpcomingGet(page = pageNumber).let {
                QueryResult(it.results.orEmpty(), it.totalResults ?: 0)
            }
    }
