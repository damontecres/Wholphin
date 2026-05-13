package com.github.damontecres.wholphin.util

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.DEFAULT_PAGE_SIZE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.client.extensions.personsApi
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.api.client.extensions.studiosApi
import org.jellyfin.sdk.api.client.extensions.suggestionsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.GetProgramsDto
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetGenresRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetLiveTvChannelsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetPersonsRequest
import org.jellyfin.sdk.model.api.request.GetPlaylistItemsRequest
import org.jellyfin.sdk.model.api.request.GetRecordingsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetStudiosRequest
import org.jellyfin.sdk.model.api.request.GetSuggestionsRequest
import timber.log.Timber
import java.util.UUID

/**
 * A [RequestPager] for Jellyfin server queries
 */
class ApiRequestPager<T>(
    val api: ApiClient,
    val request: T,
    val requestHandler: RequestHandler<T>,
    scope: CoroutineScope,
    pageSize: Int = DEFAULT_PAGE_SIZE,
    cacheSize: Long = 8,
    private val useSeriesForPrimary: Boolean = false,
) : RequestPager<BaseItem>(scope, pageSize, cacheSize) {
    override suspend fun init(initialPosition: Int): ApiRequestPager<T> = super.init(initialPosition) as ApiRequestPager<T>

    override suspend fun fetchPage(
        pageNumber: Int,
        includeTotalCount: Boolean,
    ): QueryResult<BaseItem> {
        val newRequest =
            requestHandler.prepare(
                request,
                pageNumber * pageSize,
                pageSize,
                includeTotalCount,
            )
        val result = requestHandler.execute(api, newRequest).content
        val data = mutableListOf<BaseItem>()
        result.items.forEach { data.add(BaseItem(it, useSeriesForPrimary)) }
        return QueryResult(data, result.totalRecordCount)
    }

    suspend fun refreshItem(
        position: Int,
        itemId: UUID,
    ) {
        mutex.withLock {
            val item =
                api.userLibraryApi.getItem(itemId).content.let {
                    BaseItem.from(
                        it,
                        api,
                        useSeriesForPrimary,
                    )
                }
            val pageNumber = position / pageSize
            val index = position - pageNumber * pageSize
            val page = cachedPages.getIfPresent(pageNumber)
            if (page != null && index in page.indices) {
                page[index] = item
                cachedPages.put(pageNumber, page)
                items = ItemList(size, pageSize, cachedPages.asMap())
            }
        }
    }

    /**
     * Dumps the cache for all the pages at or after the given position and fetches a new page
     */
    suspend fun refreshPagesAfter(position: Int) {
        val pageNumber = position / pageSize
        cachedPages.asMap().apply {
            keys.forEach { pageKey ->
                if (pageKey >= pageNumber) {
                    if (DEBUG) Timber.v("refreshPagesAfter: dropping %s", pageKey)
                    remove(pageKey)
                }
            }
        }
        fetchPageBlocking(position, true)
    }
}

/**
 * Specifies how a [RequestPager] should prepare and execute API calls
 */
interface RequestHandler<T> {
    /**
     * Prepare the given request with the specified parameters (eg which page to fetch)
     */
    fun prepare(
        request: T,
        startIndex: Int,
        limit: Int,
        enableTotalRecordCount: Boolean,
    ): T

    /**
     * Execute the given request
     */
    suspend fun execute(
        api: ApiClient,
        request: T,
    ): Response<BaseItemDtoQueryResult>
}

@Serializable
val GetItemsRequestHandler =
    object : RequestHandler<GetItemsRequest> {
        override fun prepare(
            request: GetItemsRequest,
            startIndex: Int,
            limit: Int,
            enableTotalRecordCount: Boolean,
        ): GetItemsRequest =
            request.copy(
                startIndex = startIndex,
                limit = limit,
                enableTotalRecordCount = enableTotalRecordCount,
            )

        override suspend fun execute(
            api: ApiClient,
            request: GetItemsRequest,
        ): Response<BaseItemDtoQueryResult> = api.itemsApi.getItems(request)
    }

@Serializable
val GetEpisodesRequestHandler =
    object : RequestHandler<GetEpisodesRequest> {
        override fun prepare(
            request: GetEpisodesRequest,
            startIndex: Int,
            limit: Int,
            enableTotalRecordCount: Boolean,
        ): GetEpisodesRequest =
            request.copy(
                startIndex = startIndex,
                limit = limit,
            )

        override suspend fun execute(
            api: ApiClient,
            request: GetEpisodesRequest,
        ): Response<BaseItemDtoQueryResult> = api.tvShowsApi.getEpisodes(request)
    }

@Serializable
val GetResumeItemsRequestHandler =
    object : RequestHandler<GetResumeItemsRequest> {
        override fun prepare(
            request: GetResumeItemsRequest,
            startIndex: Int,
            limit: Int,
            enableTotalRecordCount: Boolean,
        ): GetResumeItemsRequest =
            request.copy(
                startIndex = startIndex,
                limit = limit,
                enableTotalRecordCount = enableTotalRecordCount,
            )

        override suspend fun execute(
            api: ApiClient,
            request: GetResumeItemsRequest,
        ): Response<BaseItemDtoQueryResult> = api.itemsApi.getResumeItems(request)
    }

@Serializable
val GetNextUpRequestHandler =
    object : RequestHandler<GetNextUpRequest> {
        override fun prepare(
            request: GetNextUpRequest,
            startIndex: Int,
            limit: Int,
            enableTotalRecordCount: Boolean,
        ): GetNextUpRequest =
            request.copy(
                startIndex = startIndex,
                limit = limit,
                enableTotalRecordCount = enableTotalRecordCount,
            )

        override suspend fun execute(
            api: ApiClient,
            request: GetNextUpRequest,
        ): Response<BaseItemDtoQueryResult> = api.tvShowsApi.getNextUp(request)
    }

@Serializable
val GetSuggestionsRequestHandler =
    object : RequestHandler<GetSuggestionsRequest> {
        override fun prepare(
            request: GetSuggestionsRequest,
            startIndex: Int,
            limit: Int,
            enableTotalRecordCount: Boolean,
        ): GetSuggestionsRequest =
            request.copy(
                startIndex = startIndex,
                limit = limit,
                enableTotalRecordCount = enableTotalRecordCount,
            )

        override suspend fun execute(
            api: ApiClient,
            request: GetSuggestionsRequest,
        ): Response<BaseItemDtoQueryResult> = api.suggestionsApi.getSuggestions(request)
    }

@Serializable
val GetPlaylistItemsRequestHandler =
    object : RequestHandler<GetPlaylistItemsRequest> {
        override fun prepare(
            request: GetPlaylistItemsRequest,
            startIndex: Int,
            limit: Int,
            enableTotalRecordCount: Boolean,
        ): GetPlaylistItemsRequest =
            request.copy(
                startIndex = startIndex,
                limit = limit,
            )

        override suspend fun execute(
            api: ApiClient,
            request: GetPlaylistItemsRequest,
        ): Response<BaseItemDtoQueryResult> = api.playlistsApi.getPlaylistItems(request)
    }

@Serializable
val GetGenresRequestHandler =
    object : RequestHandler<GetGenresRequest> {
        override fun prepare(
            request: GetGenresRequest,
            startIndex: Int,
            limit: Int,
            enableTotalRecordCount: Boolean,
        ): GetGenresRequest =
            request.copy(
                startIndex = startIndex,
                limit = limit,
                enableTotalRecordCount = enableTotalRecordCount,
            )

        override suspend fun execute(
            api: ApiClient,
            request: GetGenresRequest,
        ): Response<BaseItemDtoQueryResult> = api.genresApi.getGenres(request)
    }

val GetProgramsDtoHandler =
    object : RequestHandler<GetProgramsDto> {
        override fun prepare(
            request: GetProgramsDto,
            startIndex: Int,
            limit: Int,
            enableTotalRecordCount: Boolean,
        ): GetProgramsDto =
            request.copy(
                startIndex = startIndex,
                limit = limit,
            )

        override suspend fun execute(
            api: ApiClient,
            request: GetProgramsDto,
        ): Response<BaseItemDtoQueryResult> = api.liveTvApi.getPrograms(request)
    }

@Serializable
val GetPersonsHandler =
    object : RequestHandler<GetPersonsRequest> {
        override fun prepare(
            request: GetPersonsRequest,
            startIndex: Int,
            limit: Int,
            enableTotalRecordCount: Boolean,
        ): GetPersonsRequest =
            request.copy(
//                startIndex = startIndex,
                limit = limit,
            )

        override suspend fun execute(
            api: ApiClient,
            request: GetPersonsRequest,
        ): Response<BaseItemDtoQueryResult> = api.personsApi.getPersons((request))
    }

val GetStudiosRequestHandler =
    object : RequestHandler<GetStudiosRequest> {
        override fun prepare(
            request: GetStudiosRequest,
            startIndex: Int,
            limit: Int,
            enableTotalRecordCount: Boolean,
        ): GetStudiosRequest =
            request.copy(
                startIndex = startIndex,
                limit = limit,
                enableTotalRecordCount = enableTotalRecordCount,
            )

        override suspend fun execute(
            api: ApiClient,
            request: GetStudiosRequest,
        ): Response<BaseItemDtoQueryResult> = api.studiosApi.getStudios(request)
    }

val GetRecordingsRequestHandler =
    object : RequestHandler<GetRecordingsRequest> {
        override fun prepare(
            request: GetRecordingsRequest,
            startIndex: Int,
            limit: Int,
            enableTotalRecordCount: Boolean,
        ): GetRecordingsRequest =
            request.copy(
                startIndex = startIndex,
                limit = limit,
                enableTotalRecordCount = enableTotalRecordCount,
            )

        override suspend fun execute(
            api: ApiClient,
            request: GetRecordingsRequest,
        ): Response<BaseItemDtoQueryResult> = api.liveTvApi.getRecordings(request)
    }

val GetLiveTvChannelsRequestHandler =
    object : RequestHandler<GetLiveTvChannelsRequest> {
        override fun prepare(
            request: GetLiveTvChannelsRequest,
            startIndex: Int,
            limit: Int,
            enableTotalRecordCount: Boolean,
        ): GetLiveTvChannelsRequest =
            request.copy(
                startIndex = startIndex,
                limit = limit,
//                enableTotalRecordCount = enableTotalRecordCount,
            )

        override suspend fun execute(
            api: ApiClient,
            request: GetLiveTvChannelsRequest,
        ): Response<BaseItemDtoQueryResult> = api.liveTvApi.getLiveTvChannels(request)
    }
