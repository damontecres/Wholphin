package com.github.damontecres.dolphin.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.ui.DEFAULT_PAGE_SIZE
import com.google.common.cache.CacheBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.function.Predicate

interface RequestHandler<T> {
    fun prepare(
        request: T,
        startIndex: Int,
        limit: Int,
        enableTotalRecordCount: Boolean,
    ): T

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

class ApiRequestPager<T>(
    val api: ApiClient,
    val request: T,
    val requestHandler: RequestHandler<T>,
    private val scope: CoroutineScope,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    cacheSize: Long = 8,
) : AbstractList<BaseItem?>(),
    BlockingList<BaseItem?> {
    private var items by mutableStateOf(ItemList<BaseItem>(0, pageSize, mapOf()))
    private var totalCount by mutableIntStateOf(-1)
    private val mutex = Mutex()
    private val cachedPages =
        CacheBuilder
            .newBuilder()
            .maximumSize(cacheSize)
            .build<Int, List<BaseItem>>()

    suspend fun init(): ApiRequestPager<T> {
        if (totalCount < 0) {
            val newRequest = requestHandler.prepare(request, 0, 1, true)
            val result = requestHandler.execute(api, newRequest).content
            totalCount = result.totalRecordCount
        }
        return this
    }

    override operator fun get(index: Int): BaseItem? {
        if (index in 0..<totalCount) {
            val item = items[index]
            if (item == null) {
                fetchPage(index)
            }
            return item
        } else {
            throw IndexOutOfBoundsException("$index of $totalCount")
        }
    }

    override suspend fun getBlocking(position: Int): BaseItem? {
        if (position in 0..<totalCount) {
            val item = items[position]
            if (item == null) {
                fetchPage(position).join()
                return items[position]
            }
            return item
        } else {
            throw IndexOutOfBoundsException("$position of $totalCount")
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

    private fun fetchPage(position: Int): Job =
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
                            false,
                        )
                    val result = requestHandler.execute(api, newRequest).content
                    val data = result.items.map { BaseItem.from(it, api) }
                    cachedPages.put(pageNumber, data)
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
