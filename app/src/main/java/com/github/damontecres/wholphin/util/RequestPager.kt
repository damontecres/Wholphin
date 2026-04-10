package com.github.damontecres.wholphin.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.damontecres.wholphin.ui.DEFAULT_PAGE_SIZE
import com.google.common.cache.CacheBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.function.Predicate

/**
 * Handles paging for an API request. You must call [init] prior to use.
 *
 * Initially, items returned will be null, but requesting the items triggers API calls in the given [CoroutineScope].
 * Since the items are stored in [androidx.compose.runtime.MutableState], it will automatically trigger recompositions when the items are ultimately fetched.
 *
 * Finally, items are cached allow for backward and forward scrolling.
 */
abstract class RequestPager<T>(
    protected val scope: CoroutineScope,
    protected val pageSize: Int = DEFAULT_PAGE_SIZE,
    cacheSize: Long = 8,
) : AbstractList<T?>(),
    BlockingList<T?> {
    protected var totalCount by mutableIntStateOf(-1)
    protected var items by mutableStateOf(ItemList<T>(0, pageSize, mapOf()))
    protected val mutex = Mutex()
    protected val cachedPages =
        CacheBuilder
            .newBuilder()
            .maximumSize(cacheSize)
            .build<Int, MutableList<T>>()

    open suspend fun init(initialPosition: Int = 0): RequestPager<T> {
        if (totalCount < 0) {
            fetchPageBlocking(initialPosition, true)
        }
        return this
    }

    override operator fun get(index: Int): T? {
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

    override suspend fun getBlocking(index: Int): T? {
        if (index in 0..<totalCount) {
            val item = items[index]
            if (item == null) {
                fetchPageBlocking(index, false)
                return items[index]
            }
            return item
        } else {
            throw IndexOutOfBoundsException("$index of $totalCount")
        }
    }

    override suspend fun indexOfBlocking(predicate: Predicate<T?>): Int {
        init(0)
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
            fetchPageBlocking(position, false)
        }

    protected suspend fun fetchPageBlocking(
        position: Int,
        setTotalCount: Boolean,
    ) {
        mutex.withLock {
            val pageNumber = position / pageSize
            if (cachedPages.getIfPresent(pageNumber) == null || setTotalCount) {
                if (DEBUG) Timber.v("fetchPage: $pageNumber")
                val result = fetchPage(pageNumber, setTotalCount)
                if (setTotalCount && result.totalCount != null) {
                    totalCount = result.totalCount.coerceAtLeast(0)
                }
                cachedPages.put(pageNumber, result.items.toMutableList())
                items = ItemList(totalCount, pageSize, cachedPages.asMap())
            }
        }
    }

    suspend fun refresh() {
        cachedPages.asMap().keys.forEachIndexed { index, pageNum ->
            if (DEBUG) Timber.v("refresh: pageNum=%s", pageNum)
            fetchPageBlocking(pageNum * pageSize, index == 0)
        }
    }

    protected abstract suspend fun fetchPage(
        pageNumber: Int,
        includeTotalCount: Boolean,
    ): QueryResult<T>

    companion object {
        internal const val DEBUG = false
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

data class QueryResult<T>(
    val items: List<T>,
    val totalCount: Int?,
)
