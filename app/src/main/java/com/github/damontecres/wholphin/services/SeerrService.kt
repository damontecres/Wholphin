package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.CreditCast
import com.github.damontecres.wholphin.api.seerr.model.CreditCrew
import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.api.seerr.model.MovieResult
import com.github.damontecres.wholphin.api.seerr.model.RootFolder
import com.github.damontecres.wholphin.api.seerr.model.SearchGet200ResponseResultsInner
import com.github.damontecres.wholphin.api.seerr.model.ServiceProfile
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.api.seerr.model.TvResult
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.SeerrPermission
import com.github.damontecres.wholphin.data.model.hasPermission
import com.github.damontecres.wholphin.ui.detail.discover.SeerrProfile
import com.github.damontecres.wholphin.ui.detail.discover.SeerrRequestData
import com.github.damontecres.wholphin.ui.detail.discover.SeerrRootFolder
import com.github.damontecres.wholphin.ui.formatBytes
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.toLocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
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
        private val imageUrlService: ImageUrlService,
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
                ?.map { createDiscoverItem(it) }
                .orEmpty()

        suspend fun discoverMovies(page: Int = 1): List<DiscoverItem> =
            api.searchApi
                .discoverMoviesGet(page = page)
                .results
                ?.map { createDiscoverItem(it) }
                .orEmpty()

        suspend fun trending(page: Int = 1): List<DiscoverItem> =
            api.searchApi
                .discoverTrendingGet(page = page)
                .results
                ?.map { createDiscoverItem(it) }
                .orEmpty()

        suspend fun upcomingMovies(page: Int = 1): List<DiscoverItem> =
            api.searchApi
                .discoverMoviesUpcomingGet(page = page)
                .results
                ?.map { createDiscoverItem(it) }
                .orEmpty()

        suspend fun upcomingTv(page: Int = 1): List<DiscoverItem> =
            api.searchApi
                .discoverTvUpcomingGet(page = page)
                .results
                ?.map { createDiscoverItem(it) }
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
                                    ?.map { createDiscoverItem(it) }
                            }

                            BaseItemKind.SERIES, BaseItemKind.SEASON, BaseItemKind.EPISODE -> {
                                api.tvApi
                                    .tvTvIdSimilarGet(tvId = it)
                                    .results
                                    ?.map { createDiscoverItem(it) }
                            }

                            BaseItemKind.PERSON -> {
                                api.personApi
                                    .personPersonIdCombinedCreditsGet(personId = it)
                                    .let { credits ->
                                        val cast =
                                            credits.cast
                                                ?.take(25)
                                                ?.map { createDiscoverItem(it) }
                                                .orEmpty()
                                        val crew =
                                            credits.crew
                                                ?.take(25)
                                                ?.map { createDiscoverItem(it) }
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

        suspend fun getTvSeries(item: BaseItem): TvDetails? =
            if (active.first()) {
                item.data.providerIds
                    ?.get("Tmdb")
                    ?.toIntOrNull()
                    ?.let { tvId ->
                        api.tvApi.tvTvIdGet(tvId = tvId)
                    }
            } else {
                null
            }

        suspend fun createImageUrl(
            imageType: ImageType,
            path: String?,
            mediaInfo: MediaInfo?,
            backdropHeight: Int = 1080,
            backdropWidth: Int = 1920,
        ): String? {
            if (mediaInfo != null) {
                val itemId =
                    if (mediaInfo.jellyfinMediaId.isNotNullOrBlank()) {
                        mediaInfo.jellyfinMediaId.toUUIDOrNull()
                    } else if (mediaInfo.jellyfinMediaId4k.isNotNullOrBlank()) {
                        mediaInfo.jellyfinMediaId4k.toUUIDOrNull()
                    } else {
                        null
                    }
                if (itemId != null) {
                    return imageUrlService.getItemImageUrl(
                        itemId = itemId,
                        imageType = imageType,
                    )
                }
            }
            val current = seerrServerRepository.current.firstOrNull() ?: return null
            val cacheImages = current.serverConfig.cacheImages == true
            val base =
                if (cacheImages) {
                    current.server.url.removeSuffix("/") + "/imageproxy/tmdb"
                } else {
                    "https://image.tmdb.org"
                }
            val prefix =
                when (imageType) {
                    ImageType.PRIMARY -> "/t/p/w500"
                    ImageType.BACKDROP -> "/t/p/w${backdropWidth}_and_h${backdropHeight}_multi_faces"
                    else -> throw IllegalArgumentException("Image type not supported: $imageType")
                }
            return "${base}${prefix}$path"
        }

        suspend fun getProfilesAndFolders(type: SeerrItemType): SeerrRequestData {
            var result = SeerrRequestData()
            val current = seerrServerRepository.current.firstOrNull()
            if (current != null && current.config.hasPermission(SeerrPermission.REQUEST_ADVANCED)) {
                if (type == SeerrItemType.MOVIE) {
                    val radarrs = api.serviceApi.serviceRadarrGet()
                    val radarr =
                        radarrs.firstOrNull { it.isDefault && !it.is4k }
                            ?: radarrs.firstOrNull { !it.is4k }
                    val radarr4k =
                        radarrs.firstOrNull { it.isDefault && it.is4k }
                            ?: radarrs.firstOrNull { it.is4k }

                    if (radarr != null && radarr.id != null) {
                        val settings = api.serviceApi.serviceRadarrRadarrIdGet(radarr.id)

                        setProfilesAndFolder(
                            settings.profiles,
                            settings.rootFolders,
                            settings.server?.activeProfileId,
                            settings.server?.activeDirectory,
                        ) { profiles, rootFolders ->
                            result =
                                result.copy(
                                    serverId = radarr.id,
                                    profiles = profiles,
                                    rootFolders = rootFolders,
                                )
                        }
                    }
                    if (radarr4k != null && radarr4k.id != null) {
                        val settings = api.serviceApi.serviceRadarrRadarrIdGet(radarr4k.id)
                        setProfilesAndFolder(
                            settings.profiles,
                            settings.rootFolders,
                            settings.server?.activeProfileId,
                            settings.server?.activeDirectory,
                        ) { profiles, rootFolders ->
                            result =
                                result.copy(
                                    server4kId = radarr4k.id,
                                    profiles4k = profiles,
                                    rootFolders4k = rootFolders,
                                )
                        }
                    }
                } else {
                    val sonarrs = api.serviceApi.serviceSonarrGet()
                    val sonarr =
                        sonarrs.firstOrNull { it.isDefault && !it.is4k }
                            ?: sonarrs.firstOrNull { !it.is4k }
                    val sonarr4k =
                        sonarrs.firstOrNull { it.isDefault && it.is4k }
                            ?: sonarrs.firstOrNull { it.is4k }

                    if (sonarr != null && sonarr.id != null) {
                        val settings = api.serviceApi.serviceSonarrSonarrIdGet(sonarr.id)

                        setProfilesAndFolder(
                            settings.profiles,
                            settings.rootFolders,
                            settings.server?.activeProfileId,
                            settings.server?.activeDirectory,
                        ) { profiles, rootFolders ->
                            result =
                                result.copy(
                                    serverId = sonarr.id,
                                    profiles = profiles,
                                    rootFolders = rootFolders,
                                )
                        }
                    }
                    if (sonarr4k != null && sonarr4k.id != null) {
                        val settings = api.serviceApi.serviceSonarrSonarrIdGet(sonarr4k.id)
                        setProfilesAndFolder(
                            settings.profiles,
                            settings.rootFolders,
                            settings.server?.activeProfileId,
                            settings.server?.activeDirectory,
                        ) { profiles, rootFolders ->
                            result =
                                result.copy(
                                    server4kId = sonarr4k.id,
                                    profiles4k = profiles,
                                    rootFolders4k = rootFolders,
                                )
                        }
                    }
                }
            }
            return result
        }

        private fun setProfilesAndFolder(
            profiles: List<ServiceProfile>?,
            rootFolders: List<RootFolder>?,
            activeProfileId: Int?,
            activeDirectory: String?,
            setter: (List<SeerrProfile>, List<SeerrRootFolder>) -> Unit,
        ) {
            val profiles =
                profiles.orEmpty().mapNotNull {
                    if (it.id != null && it.name != null) {
                        SeerrProfile(it.id, it.name, activeProfileId == it.id)
                    } else {
                        null
                    }
                }
            val rootFolders =
                rootFolders.orEmpty().mapNotNull {
                    if (it.id != null && it.path != null) {
                        val freeSpace = it.freeSpace?.let { formatBytes(it) } ?: ""
                        SeerrRootFolder(it.id, it.path, freeSpace, activeDirectory == it.path)
                    } else {
                        null
                    }
                }
            setter.invoke(profiles, rootFolders)
        }

        suspend fun createDiscoverItem(item: Any): DiscoverItem =
            when (item) {
                is MovieResult -> createDiscoverItem(item)
                is MovieDetails -> createDiscoverItem(item)
                is TvResult -> createDiscoverItem(item)
                is TvDetails -> createDiscoverItem(item)
                is SeerrSearchResult -> createDiscoverItem(item)
                is CreditCast -> createDiscoverItem(item)
                is CreditCrew -> createDiscoverItem(item)
                else -> throw IllegalArgumentException("Unsupported type ${item::class.qualifiedName}")
            }

        suspend fun createDiscoverItem(movie: MovieResult): DiscoverItem =
            DiscoverItem(
                id = movie.id,
                type = SeerrItemType.MOVIE,
                title = movie.title,
                subtitle = null,
                overview = movie.overview,
                availability =
                    SeerrAvailability.from(movie.mediaInfo?.status)
                        ?: SeerrAvailability.UNKNOWN,
                releaseDate = toLocalDate(movie.releaseDate),
                posterUrl = createImageUrl(ImageType.PRIMARY, movie.posterPath, movie.mediaInfo),
                backDropUrl = createImageUrl(ImageType.BACKDROP, movie.backdropPath, movie.mediaInfo),
                jellyfinItemId = movie.mediaInfo?.jellyfinMediaId?.toUUIDOrNull(),
            )

        suspend fun createDiscoverItem(movie: MovieDetails): DiscoverItem =
            DiscoverItem(
                id = movie.id ?: -1,
                type = SeerrItemType.MOVIE,
                title = movie.title,
                subtitle = null,
                overview = movie.overview,
                availability =
                    SeerrAvailability.from(movie.mediaInfo?.status)
                        ?: SeerrAvailability.UNKNOWN,
                releaseDate = toLocalDate(movie.releaseDate),
                posterUrl = createImageUrl(ImageType.PRIMARY, movie.posterPath, movie.mediaInfo),
                backDropUrl = createImageUrl(ImageType.BACKDROP, movie.backdropPath, movie.mediaInfo),
                jellyfinItemId = movie.mediaInfo?.jellyfinMediaId?.toUUIDOrNull(),
            )

        suspend fun createDiscoverItem(tv: TvResult): DiscoverItem =
            DiscoverItem(
                id = tv.id!!,
                type = SeerrItemType.TV,
                title = tv.name,
                subtitle = null,
                overview = tv.overview,
                availability =
                    SeerrAvailability.from(tv.mediaInfo?.status)
                        ?: SeerrAvailability.UNKNOWN,
                releaseDate = toLocalDate(tv.firstAirDate),
                posterUrl = createImageUrl(ImageType.PRIMARY, tv.posterPath, tv.mediaInfo),
                backDropUrl = createImageUrl(ImageType.BACKDROP, tv.backdropPath, tv.mediaInfo),
                jellyfinItemId = tv.mediaInfo?.jellyfinMediaId?.toUUIDOrNull(),
            )

        suspend fun createDiscoverItem(tv: TvDetails): DiscoverItem =
            DiscoverItem(
                id = tv.id!!,
                type = SeerrItemType.TV,
                title = tv.name,
                subtitle = null,
                overview = tv.overview,
                availability =
                    SeerrAvailability.from(tv.mediaInfo?.status)
                        ?: SeerrAvailability.UNKNOWN,
                releaseDate = toLocalDate(tv.firstAirDate),
                posterUrl = createImageUrl(ImageType.PRIMARY, tv.posterPath, tv.mediaInfo),
                backDropUrl = createImageUrl(ImageType.BACKDROP, tv.backdropPath, tv.mediaInfo),
                jellyfinItemId = tv.mediaInfo?.jellyfinMediaId?.toUUIDOrNull(),
            )

        suspend fun createDiscoverItem(search: SeerrSearchResult): DiscoverItem =
            DiscoverItem(
                id = search.id,
                type = SeerrItemType.fromString(search.mediaType),
                title = search.title ?: search.name,
                subtitle = null,
                overview = search.overview,
                availability =
                    SeerrAvailability.from(search.mediaInfo?.status)
                        ?: SeerrAvailability.UNKNOWN,
                releaseDate = toLocalDate(search.releaseDate ?: search.firstAirDate),
                posterUrl = createImageUrl(ImageType.PRIMARY, search.posterPath, search.mediaInfo),
                backDropUrl = createImageUrl(ImageType.BACKDROP, search.backdropPath, search.mediaInfo),
                jellyfinItemId = search.mediaInfo?.jellyfinMediaId?.toUUIDOrNull(),
            )

        suspend fun createDiscoverItem(credit: CreditCast): DiscoverItem =
            DiscoverItem(
                id = credit.id!!,
                type = SeerrItemType.fromString(credit.mediaType, SeerrItemType.PERSON),
                title = credit.name ?: credit.title,
                subtitle = credit.character,
                overview = credit.overview,
                availability =
                    SeerrAvailability.from(credit.mediaInfo?.status)
                        ?: SeerrAvailability.UNKNOWN,
                releaseDate = toLocalDate(credit.firstAirDate),
                posterUrl =
                    createImageUrl(
                        ImageType.PRIMARY,
                        credit.posterPath ?: credit.profilePath,
                        credit.mediaInfo,
                    ),
                backDropUrl =
                    createImageUrl(
                        ImageType.BACKDROP,
                        credit.backdropPath,
                        credit.mediaInfo,
                    ),
                jellyfinItemId = credit.mediaInfo?.jellyfinMediaId?.toUUIDOrNull(),
            )

        suspend fun createDiscoverItem(credit: CreditCrew): DiscoverItem =
            DiscoverItem(
                id = credit.id!!,
                type = SeerrItemType.fromString(credit.mediaType, SeerrItemType.PERSON),
                title = credit.name ?: credit.title,
                subtitle = credit.job,
                overview = credit.overview,
                availability =
                    SeerrAvailability.from(credit.mediaInfo?.status)
                        ?: SeerrAvailability.UNKNOWN,
                releaseDate = toLocalDate(credit.firstAirDate),
                posterUrl =
                    createImageUrl(
                        ImageType.PRIMARY,
                        credit.posterPath ?: credit.profilePath,
                        credit.mediaInfo,
                    ),
                backDropUrl =
                    createImageUrl(
                        ImageType.BACKDROP,
                        credit.backdropPath,
                        credit.mediaInfo,
                    ),
                jellyfinItemId = credit.mediaInfo?.jellyfinMediaId?.toUUIDOrNull(),
            )
    }
