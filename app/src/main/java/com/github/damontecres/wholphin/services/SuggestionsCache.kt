package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.data.model.BaseItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.io.File
import java.util.Collections
import java.util.LinkedHashMap
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CachedSuggestions(
    val items: List<BaseItem>,
    val timestamp: Long,
)

@Singleton
class SuggestionsCache
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val json = Json { ignoreUnknownKeys = true }
        private val mutex = Mutex()

        private val memoryCache: MutableMap<String, CachedSuggestions> =
            Collections.synchronizedMap(
                object : LinkedHashMap<String, CachedSuggestions>(MAX_MEMORY_CACHE_SIZE, 0.75f, true) {
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSuggestions>?): Boolean =
                        size > MAX_MEMORY_CACHE_SIZE
                },
            )

        private fun cacheKey(libraryId: UUID, itemKind: BaseItemKind): String =
            "${libraryId}_${itemKind.serialName}"

        private fun cacheFile(libraryId: UUID, itemKind: BaseItemKind): File =
            File(context.cacheDir, "suggestions").also { it.mkdirs() }
                .resolve("${cacheKey(libraryId, itemKind)}.json")

        suspend fun get(libraryId: UUID, itemKind: BaseItemKind): CachedSuggestions? {
            val key = cacheKey(libraryId, itemKind)
            memoryCache[key]?.let { return it }

            return withContext(Dispatchers.IO) {
                runCatching {
                    cacheFile(libraryId, itemKind)
                        .takeIf { it.exists() }
                        ?.let { json.decodeFromString<CachedSuggestions>(it.readText()) }
                        ?.also { memoryCache[key] = it }
                }.onFailure { Timber.w(it, "Failed to read suggestions cache") }
                    .getOrNull()
            }
        }

        suspend fun put(libraryId: UUID, itemKind: BaseItemKind, items: List<BaseItem>) {
            val key = cacheKey(libraryId, itemKind)
            val cached = CachedSuggestions(items, System.currentTimeMillis())
            memoryCache[key] = cached

            withContext(Dispatchers.IO) {
                mutex.withLock {
                    runCatching { cacheFile(libraryId, itemKind).writeText(json.encodeToString(cached)) }
                        .onFailure { Timber.w(it, "Failed to write suggestions cache") }
                }
            }
        }

        suspend fun clear() {
            memoryCache.clear()
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    File(context.cacheDir, "suggestions").deleteRecursively()
                }
            }
        }

        companion object {
            private const val MAX_MEMORY_CACHE_SIZE = 8
        }
    }
