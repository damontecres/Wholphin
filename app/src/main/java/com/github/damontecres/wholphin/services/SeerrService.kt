package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.SearchGet200ResponseResultsInner
import com.github.damontecres.wholphin.data.model.DiscoverItem
import javax.inject.Inject
import javax.inject.Singleton

typealias SeerrSearchResult = SearchGet200ResponseResultsInner

@Singleton
class SeerrService
    @Inject
    constructor(
        private val seerApi: SeerrApi,
    ) {
        val api: SeerrApiClient get() = seerApi.api

        val active: Boolean get() = seerApi.active

        suspend fun search(
            query: String,
            page: Int = 1,
        ): List<SeerrSearchResult> =
            api.searchApi
                .searchGet(query = query, page = page)
                .results
                .orEmpty()

        suspend fun discoverTv(page: Int = 1): List<DiscoverItem> =
            api.searchApi
                .discoverTvGet(page = page)
                .results
                ?.map(::DiscoverItem)
                .orEmpty()

        suspend fun discoverMovies(page: Int = 1): List<DiscoverItem> =
            api.searchApi
                .discoverMoviesGet(page = page)
                .results
                ?.map(::DiscoverItem)
                .orEmpty()
    }
