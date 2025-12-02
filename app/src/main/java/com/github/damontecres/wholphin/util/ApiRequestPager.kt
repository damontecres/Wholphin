package com.github.damontecres.wholphin.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.DEFAULT_PAGE_SIZE
import com.google.common.cache.CacheBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.client.extensions.personsApi
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.api.client.extensions.suggestionsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.GetProgramsDto
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetGenresRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetPersonsRequest
import org.jellyfin.sdk.model.api.request.GetPlaylistItemsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetSuggestionsRequest
import timber.log.Timber
import java.util.UUID
import java.util.function.Predicate

/**
 * Handles paging for an API request. You must call [init] prior to use.
 *
 * Initially, items returned will be null, but requesting the items triggers API calls in the given [CoroutineScope].
 * Since the items are stored in [androidx.compose.runtime.MutableState], it will automatically trigger recompositions when the items are ultimately fetched.
 *
 * Finally, items are cached allow for backward and forward scrolling.
 */
class ApiRequestPager<T>(
    val api: ApiClient,
    val request: T,
    val requestHandler: RequestHandler<T>,
    private val scope: CoroutineScope,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    cacheSize: Long = 8,
    private val useSeriesForPrimary: Boolean = false,
) : AbstractList<BaseItem?>(),
    BlockingList<BaseItem?> {
    private var items by mutableStateOf(ItemList<BaseItem>(0, pageSize, mapOf()))
    private var totalCount by mutableIntStateOf(-1)
    private val mutex = Mutex()
    private val cachedPages =
        CacheBuilder
            .newBuilder()
            .maximumSize(cacheSize)
            .build<Int, MutableList<BaseItem>>()

    suspend fun init(initialPosition: Int = 0): ApiRequestPager<T> {
        if (totalCount < 0) {
            fetchPage(initialPosition, true).join()
        }
        return this
    }

    override operator fun get(index: Int): BaseItem? {
        if (index in 0..<totalCount) {
            val item = items[index]
            if (item == null) {
                fetchPage(index, false)
            }
            return item
        } else {
            throw IndexOutOfBoundsException("$index of $totalCount")
        }
    }

    override suspend fun getBlocking(index: Int): BaseItem? {
        if (index in 0..<totalCount) {
            val item = items[index]
            if (item == null) {
                fetchPage(index, false).join()
                return items[index]
            }
            return item
        } else {
            throw IndexOutOfBoundsException("$index of $totalCount")
        }
    }

    override suspend fun indexOfBlocking(predicate: Predicate<BaseItem?>): Int {
        init()
        for (i in 0 until totalCount) {
            val currentItem = getBlocking(i)
            if (currentItem != null && predicate.test(currentItem)) {
                return i
            }
        }
        return -1
    }

    override val size: Int
        get() = totalCount

    private fun fetchPage(
        position: Int,
        setTotalCount: Boolean,
    ): Job =
        scope.launch(ExceptionHandler() + Dispatchers.IO) {
            mutex.withLock {
                val pageNumber = position / pageSize
                if (cachedPages.getIfPresent(pageNumber) == null) {
                    if (DEBUG) Timber.v("fetchPage: $pageNumber")
                    val newRequest =
                        requestHandler.prepare(
                            request,
                            pageNumber * pageSize,
                            pageSize,
                            setTotalCount,
                        )
                    val result = requestHandler.execute(api, newRequest).content
                    if (setTotalCount) {
                        totalCount = result.totalRecordCount
                    }
                    val data = mutableListOf<BaseItem>()
                    result.items.forEach { data.add(BaseItem.from(it, api, useSeriesForPrimary)) }
                    cachedPages.put(pageNumber, data)
                    items = ItemList(totalCount, pageSize, cachedPages.asMap())
                }
            }
        }

    suspend fun refreshItem(
        position: Int,
        itemId: UUID,
    ) {
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
        mutex.withLock {
            val page = cachedPages.getIfPresent(pageNumber)
            if (page != null && index in page.indices) {
                page[index] = item
                cachedPages.put(pageNumber, page)
                items = ItemList(totalCount, pageSize, cachedPages.asMap())
            }
        }
    }

    companion object {
        private const val DEBUG = false
    }

    class ItemList<T>(
        val size: Int,
        val pageSize: Int,
        val pages: Map<Int, List<T>>,
    ) {
        operator fun get(position: Int): T? {
            val page = position / pageSize
            val data = pages[page]
            if (data != null) {
                val index = position % pageSize
                if (index in data.indices) {
                    return data[index]
                } else {
                    // This can happen when items are removed while scrolling
                    Timber.w(
                        "Index $index not in data: position=$position, data.size=${data.size}",
                    )
                    return null
                }
            } else {
                return null
            }
        }
    }
}

/**
 * Specifies how the [ApiRequestPager] should prepare and execute API calls
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
