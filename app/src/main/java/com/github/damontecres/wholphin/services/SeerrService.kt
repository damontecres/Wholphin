package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.SearchGet200ResponseResultsInner
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import kotlinx.coroutines.flow.first
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject
import javax.inject.Singleton

typealias SeerrSearchResult = SearchGet200ResponseResultsInner

/**
 * Main access for the current Seerr server (if any)
 *
 * Exposes a [SeerrApiClient] for queries
 */
@Singleton
class SeerrService
    @Inject
    constructor(
        private val seerApi: SeerrApi,
        private val seerrServerRepository: SeerrServerRepository,
    ) {
        val api: SeerrApiClient get() = seerApi.api

        val active get() = seerrServerRepository.active

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

        /**
         * Get [DiscoverItem]s similar to the JF items such as movies, series, or people
         *
         * If Seerr integration is not active, this short circuits to return null
         *
         * @return the discovered items or null if no Seerr server configured
         */
        suspend fun similar(item: BaseItem): List<DiscoverItem>? =
            if (active.first()) {
                item.data.providerIds
                    ?.get("Tmdb")
                    ?.toIntOrNull()
                    ?.let {
                        when (item.type) {
                            BaseItemKind.MOVIE -> {
                                api.moviesApi
                                    .movieMovieIdSimilarGet(movieId = it)
                                    .results
                                    ?.map(::DiscoverItem)
                            }

                            BaseItemKind.SERIES, BaseItemKind.SEASON, BaseItemKind.EPISODE -> {
                                api.tvApi
                                    .tvTvIdSimilarGet(tvId = it)
                                    .results
                                    ?.map(::DiscoverItem)
                            }

                            BaseItemKind.PERSON -> {
                                api.personApi
                                    .personPersonIdCombinedCreditsGet(personId = it)
                                    .let { credits ->
                                        val cast =
                                            credits.cast
                                                ?.take(25)
                                                ?.map(::DiscoverItem)
                                                .orEmpty()
                                        val crew =
                                            credits.crew
                                                ?.take(25)
                                                ?.map(::DiscoverItem)
                                                .orEmpty()
                                        cast + crew
                                    }
                            }

                            else -> {
                                null
                            }
                        }
                    }.orEmpty()
            } else {
                null
            }
    }
