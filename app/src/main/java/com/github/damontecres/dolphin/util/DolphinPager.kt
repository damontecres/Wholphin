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
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber

class DolphinPager(
    val api: ApiClient,
    val request: GetItemsRequest,
    private val scope: CoroutineScope,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    itemCount: Int? = null,
    cacheSize: Long = 8,
) : AbstractList<BaseItem?>() {
    private var items by mutableStateOf(ItemList<BaseItem>(0, pageSize, mapOf()))
    private var totalCount by mutableIntStateOf(itemCount ?: -1)
    private val mutex = Mutex()
    private val cachedPages =
        CacheBuilder
            .newBuilder()
            .maximumSize(cacheSize)
            .build<Int, List<BaseItem>>()

    suspend fun init() {
        if (totalCount < 0) {
            val result =
                api.itemsApi
                    .getItems(
                        request.copy(
                            startIndex = 0,
                            limit = 1,
                            enableTotalRecordCount = true,
                        ),
                    ).content
            totalCount = result.totalRecordCount
        }
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

    suspend fun getBlocking(position: Int): BaseItem? {
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

    override val size: Int
        get() = totalCount

    private fun fetchPage(position: Int): Job =
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val pageNumber = position / pageSize
                if (cachedPages.getIfPresent(pageNumber) == null) {
                    if (DEBUG) Timber.v("fetchPage: $pageNumber")
                    val result =
                        api.itemsApi
                            .getItems(
                                request.copy(
                                    startIndex = pageNumber * pageSize,
                                    limit = pageSize,
                                    enableTotalRecordCount = false,
                                ),
                            ).content
                    val data = result.items.map { BaseItem.from(it, api) }
                    cachedPages.put(pageNumber, data)
                    items = ItemList(totalCount, pageSize, cachedPages.asMap())
                }
            }
        }

    companion object {
        private const val TAG = "DolphinPager"
        private const val DEBUG = true
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
