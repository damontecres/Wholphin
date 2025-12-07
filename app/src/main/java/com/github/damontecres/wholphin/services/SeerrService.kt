package com.github.damontecres.wholphin.services

import com.github.damontecres.api.seerr.SeerrApiClient
import com.github.damontecres.api.seerr.model.SearchGet200ResponseResultsInner
import javax.inject.Inject
import javax.inject.Singleton

typealias SearchResult = SearchGet200ResponseResultsInner

@Singleton
class SeerrService
    @Inject
    constructor(
        private val seerApi: SeerrApi,
    ) {
        private val api: SeerrApiClient get() = seerApi.api

        suspend fun init() {
            api.searchApi.discoverTvGet(1)
        }

        suspend fun search(
            query: String,
            page: Int = 1,
        ): List<SearchResult> =
            api.searchApi
                .searchGet(query = query, page = page)
                .results
                .orEmpty()

        suspend fun discoverTv(page: Int = 1) =
            api.searchApi
                .discoverTvGet(page = page)
                .results
                .orEmpty()

        suspend fun discoverMovies(page: Int = 1) =
            api.searchApi
                .discoverMoviesGet(page = page)
                .results
                .orEmpty()
    }
