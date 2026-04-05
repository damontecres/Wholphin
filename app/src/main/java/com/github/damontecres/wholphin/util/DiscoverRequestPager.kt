package com.github.damontecres.wholphin.util

import com.github.damontecres.wholphin.api.seerr.model.TvResult
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.services.SeerrApi
import kotlinx.coroutines.CoroutineScope

/**
 * A [RequestPager] for Seerr server queries
 */
class DiscoverRequestPager<T>(
    private val api: SeerrApi,
    val requestHandler: DiscoverRequestHandler<T>,
    val transform: suspend (T) -> DiscoverItem,
    scope: CoroutineScope,
    pageSize: Int = 20,
    cacheSize: Long = 16,
) : RequestPager<DiscoverItem>(scope, pageSize, cacheSize) {
    override suspend fun init(initialPosition: Int): DiscoverRequestPager<T> = super.init(initialPosition) as DiscoverRequestPager<T>

    override suspend fun fetchPage(
        pageNumber: Int,
        includeTotalCount: Boolean,
    ): QueryResult<DiscoverItem> {
        val result = requestHandler.execute(api, pageNumber)
        val transformed = result.items.map { transform.invoke(it) }
        return QueryResult(transformed, result.totalCount)
    }
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
