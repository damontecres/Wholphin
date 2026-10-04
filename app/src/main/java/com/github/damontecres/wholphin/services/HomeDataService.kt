package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.ServerUserConfig
import com.github.damontecres.wholphin.data.model.createGenreDestination
import com.github.damontecres.wholphin.data.model.createStudioDestination
import com.github.damontecres.wholphin.preferences.HomePagePreferences
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.ui.HomeItemFields
import com.github.damontecres.wholphin.ui.ProgramItemFields
import com.github.damontecres.wholphin.ui.components.getGenreImageMap
import com.github.damontecres.wholphin.ui.formatTypeName
import com.github.damontecres.wholphin.ui.main.settings.Library
import com.github.damontecres.wholphin.ui.playback.getTypeFor
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.ui.util.ResArgStringProvider
import com.github.damontecres.wholphin.ui.util.ResProviderStringProvider
import com.github.damontecres.wholphin.ui.util.ResStringProvider
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetArtistsHandler
import com.github.damontecres.wholphin.util.GetGenresRequestHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.GetLiveTvChannelsRequestHandler
import com.github.damontecres.wholphin.util.GetPersonsHandler
import com.github.damontecres.wholphin.util.GetProgramsDtoHandler
import com.github.damontecres.wholphin.util.GetRecordingsRequestHandler
import com.github.damontecres.wholphin.util.GetStudiosRequestHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.HomeRowLoadingState.Success
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.GetProgramsDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetArtistsRequest
import org.jellyfin.sdk.model.api.request.GetGenresRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetLiveTvChannelsRequest
import org.jellyfin.sdk.model.api.request.GetPersonsRequest
import org.jellyfin.sdk.model.api.request.GetRecordingsRequest
import org.jellyfin.sdk.model.api.request.GetStudiosRequest
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeDataService
    @Inject
    constructor(
        private val api: ApiClient,
        @param:AuthOkHttpClient private val authOkHttpClient: OkHttpClient,
        private val serverRepository: ServerRepository,
        private val latestNextUpService: LatestNextUpService,
        private val imageUrlService: ImageUrlService,
        private val suggestionService: SuggestionService,
    ) {
        /**
         * Fetch the data from the server for a given [HomeRowConfig]
         */
        suspend fun fetchDataForRow(
            row: HomeRowConfig,
            scope: CoroutineScope,
            prefs: HomePagePreferences,
            userDto: ServerUserConfig,
            libraries: List<Library>,
            limit: Int = prefs.maxItemsPerRow,
            isRefresh: Boolean,
            usePaging: Boolean = false,
        ): HomeRowLoadingState =
            when (row) {
                is HomeRowConfig.ContinueWatching -> {
                    val resume =
                        latestNextUpService.getResume(
                            userDto.id,
                            limit,
                            true,
                            row.viewOptions.useSeries,
                        )

                    Success(
                        title = ResStringProvider(R.string.continue_watching),
                        items = resume,
                        viewOptions = row.viewOptions,
                        rowType = row,
                        showViewMore = resume.size >= limit,
                    )
                }

                is HomeRowConfig.NextUp -> {
                    val nextUp =
                        latestNextUpService.getNextUp(
                            userDto.id,
                            limit,
                            prefs.enableRewatchingNextUp,
                            false,
                            prefs.maxDaysNextUp,
                            row.viewOptions.useSeries,
                        )

                    Success(
                        title = ResStringProvider(R.string.next_up),
                        items = nextUp,
                        viewOptions = row.viewOptions,
                        rowType = row,
                        showViewMore = nextUp.size >= limit,
                    )
                }

                is HomeRowConfig.ContinueWatchingCombined -> {
                    val resume =
                        latestNextUpService.getResume(
                            userDto.id,
                            limit,
                            true,
                            row.viewOptions.useSeries,
                        )
                    val nextUp =
                        latestNextUpService.getNextUp(
                            userDto.id,
                            limit,
                            prefs.enableRewatchingNextUp,
                            false,
                            prefs.maxDaysNextUp,
                            row.viewOptions.useSeries,
                        )
                    val combined = latestNextUpService.buildCombined(resume, nextUp)

                    Success(
                        title = ResStringProvider(R.string.continue_watching),
                        items = combined.take(limit),
                        viewOptions = row.viewOptions,
                        rowType = row,
                        showViewMore = combined.size >= limit,
                    )
                }

                is HomeRowConfig.Genres -> {
                    val request =
                        GetGenresRequest(
                            parentId = row.parentId,
                            userId = userDto.id,
                            limit = limit,
                        )
                    val items =
                        GetGenresRequestHandler
                            .execute(api, request)
                            .content.items
                    val genreIds = items.map { it.id }
                    val genreImages =
                        getGenreImageMap(
                            api = api,
                            userId = serverRepository.currentUser?.id,
                            scope = scope,
                            imageUrlService = imageUrlService,
                            genres = genreIds,
                            parentId = row.parentId,
                            includeItemTypes = null,
                            cardWidthPx = null,
                            useCache = isRefresh,
                        )
                    val library =
                        libraries
                            .firstOrNull { it.itemId == row.parentId }

                    val title =
                        library?.name?.let { ResArgStringProvider(R.string.genres_in, it) }
                            ?: ResStringProvider(R.string.genres)
                    val genres =
                        items.map {
                            BaseItem(
                                it,
                                false,
                                genreImages[it.id],
                                createGenreDestination(
                                    genreId = it.id,
                                    genreName = it.name ?: "",
                                    parentId = row.parentId,
                                    parentName = library?.name,
                                    includeItemTypes = library?.includeItemTypes,
                                    collectionType =
                                        library?.collectionType
                                            ?: CollectionType.UNKNOWN,
                                ),
                            )
                        }

                    Success(
                        title,
                        genres,
                        viewOptions = row.viewOptions,
                        rowType = row,
                        showViewMore = genres.size >= limit,
                    )
                }

                is HomeRowConfig.Studios -> {
                    val request =
                        GetStudiosRequest(
                            parentId = row.parentId,
                            userId = userDto.id,
                            limit = limit,
                            includeItemTypes = listOf(BaseItemKind.SERIES),
                        )
                    val items =
                        GetStudiosRequestHandler
                            .execute(api, request)
                            .content.items
                    val library =
                        libraries
                            .firstOrNull { it.itemId == row.parentId }
                    val title =
                        library?.name?.let { ResArgStringProvider(R.string.studios_in, it) }
                            ?: ResStringProvider(R.string.studios)
                    val studios =
                        items.map {
                            val imageUrl =
                                imageUrlService.getItemImageUrl(
                                    itemId = it.id,
                                    imageType = ImageType.THUMB,
                                )
                            BaseItem(
                                it,
                                false,
                                imageUrl,
                                createStudioDestination(
                                    studioId = it.id,
                                    name = it.name ?: "",
                                    parentId = row.parentId,
                                    parentName = library?.name,
                                    includeItemTypes = library?.includeItemTypes,
                                ),
                            )
                        }

                    Success(
                        title,
                        studios,
                        viewOptions = row.viewOptions,
                        showViewMore = studios.size >= limit,
                    )
                }

                is HomeRowConfig.RecentlyAdded -> {
                    val library = libraries.firstOrNull { it.itemId == row.parentId }
                    val title = getRecentlyAddedTitle(library?.name)
                    val request =
                        GetLatestMediaRequest(
                            fields = library.itemFields,
                            imageTypeLimit = 1,
                            parentId = row.parentId,
                            groupItems = true,
                            limit = limit,
                            isPlayed = null, // Server will handle user's preference
                        )
                    val latest =
                        api.userLibraryApi
                            .getLatestMedia(request)
                            .content
                            .map { BaseItem(it, row.viewOptions.useSeries) }
                            .let {
                                Success(
                                    title,
                                    it,
                                    row.viewOptions,
                                    rowType = row,
                                    showViewMore = it.size >= limit,
                                )
                            }
                    latest
                }

                is HomeRowConfig.RecentlyReleased -> {
                    val library = libraries.firstOrNull { it.itemId == row.parentId }
                    val title =
                        library?.name?.let {
                            ResArgStringProvider(R.string.recently_released_in, it)
                        } ?: ResStringProvider(R.string.recently_released)
                    val request =
                        GetItemsRequest(
                            parentId = row.parentId,
                            limit = limit,
                            sortBy =
                                listOf(
                                    ItemSortBy.PREMIERE_DATE,
                                    ItemSortBy.SERIES_SORT_NAME,
                                    ItemSortBy.AIRED_EPISODE_ORDER,
                                ),
                            sortOrder =
                                listOf(
                                    SortOrder.DESCENDING,
                                    SortOrder.ASCENDING,
                                    SortOrder.DESCENDING,
                                ),
                            fields = library.itemFields,
                            recursive = true,
                            maxPremiereDate = LocalDateTime.now(),
                            isUnaired = false,
                        )
                    if (usePaging) {
                        ApiRequestPager(
                            api,
                            request,
                            GetItemsRequestHandler,
                            scope,
                            useSeriesForPrimary = row.viewOptions.useSeries,
                        ).init()
                    } else {
                        GetItemsRequestHandler
                            .execute(api, request)
                            .content.items
                            .map { BaseItem(it, row.viewOptions.useSeries) }
                    }.let {
                        Success(
                            title,
                            it,
                            row.viewOptions,
                            rowType = row,
                            showViewMore = it.size >= limit,
                        )
                    }
                }

                is HomeRowConfig.ByParent -> {
                    val library = libraries.firstOrNull { it.itemId == row.parentId }
                    val request =
                        GetItemsRequest(
                            userId = userDto.id,
                            parentId = row.parentId,
                            recursive = row.recursive,
                            sortBy =
                                row.sort?.let {
                                    buildList {
                                        if (it.sort == ItemSortBy.RANDOM) {
                                            add(ItemSortBy.SORT_NAME)
                                            add(ItemSortBy.RANDOM)
                                        } else {
                                            add(it.sort)
                                            if (it.sort != ItemSortBy.SORT_NAME) {
                                                add(ItemSortBy.SORT_NAME)
                                            }
                                        }
                                    }
                                },
                            sortOrder =
                                row.sort?.let {
                                    buildList {
                                        if (it.sort == ItemSortBy.RANDOM) {
                                            add(SortOrder.ASCENDING)
                                            add(it.direction)
                                        } else {
                                            add(it.direction)
                                            if (it.sort != ItemSortBy.SORT_NAME) {
                                                add(SortOrder.ASCENDING)
                                            }
                                        }
                                    }
                                },
                            limit = limit,
                            fields = library.itemFields,
                        )

                    // Not using getItemName because we want to throw the 404
                    val title =
                        api.userLibraryApi
                            .getItem(
                                userId = serverRepository.currentUser?.id,
                                itemId = row.parentId,
                            ).content.name
                            ?.let { StringStringProvider(it) }
                            ?: ResStringProvider(R.string.collection)
                    if (usePaging) {
                        ApiRequestPager(
                            api,
                            request,
                            GetItemsRequestHandler,
                            scope,
                            useSeriesForPrimary = row.viewOptions.useSeries,
                        ).init()
                    } else {
                        GetItemsRequestHandler
                            .execute(api, request)
                            .content.items
                            .map { BaseItem(it, row.viewOptions.useSeries) }
                    }.let {
                        Success(
                            title,
                            it,
                            row.viewOptions,
                            rowType = row,
                            showViewMore = it.size >= limit,
                        )
                    }
                }

                is HomeRowConfig.GetItems -> {
                    val request =
                        row.filter.asGetItemsRequest().copy(
                            userId = userDto.id,
                            limit = limit,
                            fields = HomeItemFieldsBoxSets,
                        )
                    if (usePaging) {
                        ApiRequestPager(
                            api,
                            request,
                            GetItemsRequestHandler,
                            scope,
                            useSeriesForPrimary = row.viewOptions.useSeries,
                        ).init()
                    } else {
                        GetItemsRequestHandler
                            .execute(api, request)
                            .content.items
                            .map { BaseItem(it, row.viewOptions.useSeries) }
                    }.let {
                        Success(
                            StringStringProvider(row.name),
                            it,
                            row.viewOptions,
                            rowType = row,
                            showViewMore = it.size >= limit,
                        )
                    }
                }

                is HomeRowConfig.Favorite -> {
                    val title =
                        ResProviderStringProvider(
                            R.string.favorite_items_title,
                            ResStringProvider(formatTypeName(row.kind)),
                        )
                    val resultList =
                        when (row.kind) {
                            BaseItemKind.PERSON -> {
                                val request =
                                    GetPersonsRequest(
                                        userId = userDto.id,
                                        limit = limit,
                                        fields = HomeItemFields,
                                        isFavorite = true,
                                        enableImages = true,
                                        enableImageTypes = listOf(ImageType.PRIMARY),
                                    )

                                GetPersonsHandler.execute(api, request).toBaseItems()
                            }

                            BaseItemKind.MUSIC_ARTIST -> {
                                val request =
                                    GetArtistsRequest(
                                        userId = userDto.id,
                                        limit = limit,
                                        fields = HomeItemFields,
                                        isFavorite = true,
                                    )
                                if (usePaging) {
                                    ApiRequestPager(
                                        api,
                                        request,
                                        GetArtistsHandler,
                                        scope,
                                        useSeriesForPrimary = row.viewOptions.useSeries,
                                    ).init()
                                } else {
                                    GetArtistsHandler
                                        .execute(api, request)
                                        .toBaseItems(row.viewOptions.useSeries)
                                }
                            }

                            else -> {
                                val fields =
                                    if (row.kind == BaseItemKind.BOX_SET) {
                                        HomeItemFieldsBoxSets
                                    } else {
                                        HomeItemFields
                                    }
                                val request =
                                    GetItemsRequest(
                                        userId = userDto.id,
                                        recursive = true,
                                        limit = limit,
                                        fields = fields,
                                        includeItemTypes = listOf(row.kind),
                                        isFavorite = true,
                                    )
                                if (usePaging) {
                                    ApiRequestPager(
                                        api,
                                        request,
                                        GetItemsRequestHandler,
                                        scope,
                                        useSeriesForPrimary = row.viewOptions.useSeries,
                                    ).init()
                                } else {
                                    GetItemsRequestHandler
                                        .execute(api, request)
                                        .toBaseItems(row.viewOptions.useSeries)
                                }
                            }
                        }
                    resultList.let {
                        Success(
                            title,
                            it,
                            row.viewOptions,
                            rowType = row,
                            showViewMore = it.size >= limit,
                        )
                    }
                }

                is HomeRowConfig.Recordings -> {
                    val request =
                        GetRecordingsRequest(
                            userId = userDto.id,
                            isInProgress = true,
                            fields = HomeItemFields,
                            limit = limit,
                            enableImages = true,
                            enableUserData = true,
                            enableTotalRecordCount = false,
                        )
                    if (usePaging) {
                        ApiRequestPager(
                            api,
                            request,
                            GetRecordingsRequestHandler,
                            scope,
                            useSeriesForPrimary = row.viewOptions.useSeries,
                        ).init()
                    } else {
                        api.liveTvApi
                            .getRecordings(request)
                            .content.items
                            .map { BaseItem(it, row.viewOptions.useSeries) }
                    }.let {
                        Success(
                            ResStringProvider(R.string.active_recordings),
                            it,
                            row.viewOptions,
                            rowType = row,
                            showViewMore = it.size >= limit,
                        )
                    }
                }

                is HomeRowConfig.TvPrograms -> {
                    val request =
                        GetProgramsDto(
                            userId = userDto.id,
                            fields = ProgramItemFields,
                            limit = limit,
                            enableUserData = true,
                            enableImages = true,
                            enableImageTypes = listOf(ImageType.PRIMARY, ImageType.LOGO),
                            imageTypeLimit = 1,
                            isAiring = true,
                            minEndDate = DateTime.now().plusMinutes(1),
                        )
                    if (usePaging) {
                        ApiRequestPager(
                            api,
                            request,
                            GetProgramsDtoHandler,
                            scope,
                            useSeriesForPrimary = row.viewOptions.useSeries,
                        ).init()
                    } else {
                        api.liveTvApi
                            .getPrograms(request)
                            .content.items
                            .map { BaseItem(it, row.viewOptions.useSeries) }
                    }.let {
                        Success(
                            ResStringProvider(R.string.watch_live),
                            it,
                            row.viewOptions,
                            rowType = row,
                            showViewMore = it.size >= limit,
                        )
                    }
                }

                is HomeRowConfig.TvChannels -> {
                    val request =
                        GetLiveTvChannelsRequest(
                            userId = userDto.id,
                            fields = HomeItemFields,
                            limit = limit,
                            enableImages = true,
                        )
                    if (usePaging) {
                        ApiRequestPager(
                            api,
                            request,
                            GetLiveTvChannelsRequestHandler,
                            scope,
                            useSeriesForPrimary = row.viewOptions.useSeries,
                        ).init()
                    } else {
                        api.liveTvApi
                            .getLiveTvChannels(request)
                            .toBaseItems(api, row.viewOptions.useSeries)
                    }.let {
                        Success(
                            ResStringProvider(R.string.channels),
                            it,
                            row.viewOptions,
                            rowType = row,
                            showViewMore = it.size >= limit,
                        )
                    }
                }

                is HomeRowConfig.Suggestions -> {
                    val library =
                        api.userLibraryApi
                            .getItem(itemId = row.parentId)
                            .content
                    val title = ResArgStringProvider(R.string.suggestions_for, library.name ?: "")
                    val itemKind = SuggestionsWorker.getTypeForCollection(library.collectionType)
                    if (itemKind != null) {
                        val suggestions =
                            suggestionService
                                .getSuggestionsFlow(row.parentId, itemKind)
                                .firstOrNull()
                        when (suggestions) {
                            SuggestionsResource.Empty -> {
                                Success(
                                    title,
                                    listOf(),
                                    row.viewOptions,
                                    rowType = row,
                                )
                            }

                            is SuggestionsResource.Success -> {
                                Success(
                                    title,
                                    suggestions.items,
                                    row.viewOptions,
                                    rowType = row,
                                    showViewMore = suggestions.items.size >= limit,
                                )
                            }

                            SuggestionsResource.Loading,
                            null,
                            -> {
                                HomeRowLoadingState.Loading(title)
                            }
                        }
                    } else {
                        HomeRowLoadingState.Error(
                            title = title,
                            message = "Unsupported type ${library.collectionType}",
                        )
                    }
                }

                is HomeRowConfig.CustomEndpoint -> {
                    val title = StringStringProvider(row.title)
                    try {
                        val items =
                            fetchCustomEndpointItems(row)
                                .map { BaseItem(it, row.viewOptions.useSeries) }
                        Success(title, items, row.viewOptions, rowType = row)
                    } catch (ex: Exception) {
                        Timber.w(ex, "Custom endpoint %s failed", row.endpoint)
                        HomeRowLoadingState.Error(title, exception = ex)
                    }
                }
            }

        private suspend fun fetchCustomEndpointItems(row: HomeRowConfig.CustomEndpoint): List<BaseItemDto> {
            val base =
                api.baseUrl
                    ?: throw IllegalStateException("Jellyfin baseUrl not set")
            if (!row.endpoint.startsWith("/") || row.endpoint.startsWith("//")) {
                throw IllegalArgumentException("Custom endpoint must be an absolute path relative to Jellyfin baseUrl: ${row.endpoint}")
            }
            val resolved =
                base
                    .toHttpUrl()
                    .resolve(row.endpoint)
                    ?: throw IllegalStateException("Could not resolve endpoint ${row.endpoint} against $base")
            val params =
                buildMap {
                    serverRepository.currentUser
                        ?.id
                        ?.toString()
                        ?.let { put("userId", it) }
                    row.query?.forEach { put(it.key, it.value) }
                }
            val urlBuilder = resolved.newBuilder()
            params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
            val requestBuilder = Request.Builder().url(urlBuilder.build()).get()
            row.headers?.forEach { requestBuilder.header(it.key, it.value) }
            val response =
                authOkHttpClient
                    .newCall(requestBuilder.build())
                    .execute()
            return response.use {
                if (!it.isSuccessful) {
                    throw InvalidStatusException(it.code, null)
                }
                HomeSettingsService.jsonParser.decodeFromString<BaseItemDtoQueryResult>(it.body.string()).items
            }
        }
    }

private val Library?.itemFields: List<ItemFields>
    get() =
        if (this?.type == BaseItemKind.COLLECTION_FOLDER && this.collectionType == CollectionType.BOXSETS) {
            // Get child count to show in the header
            HomeItemFieldsBoxSets
        } else {
            HomeItemFields
        }

private val Library.includeItemTypes: List<BaseItemKind>?
    get() =
        collectionType.let { collectionType ->
            getTypeFor(collectionType)?.let { type -> listOf(type) }
        }

private val HomeItemFieldsBoxSets get() = HomeItemFields + listOf(ItemFields.CHILD_COUNT)
