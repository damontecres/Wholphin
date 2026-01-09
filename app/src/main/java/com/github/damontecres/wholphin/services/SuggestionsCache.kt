package com.github.damontecres.wholphin.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.io.File
import java.util.Collections
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CachedSuggestions(
    val ids: List<String>,
)

@Singleton
class SuggestionsCache
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        private val memoryCache: MutableMap<String, CachedSuggestions> =
            Collections.synchronizedMap(
                object : LinkedHashMap<String, CachedSuggestions>(
                    MAX_MEMORY_CACHE_SIZE,
                    0.75f,
                    true,
                ) {
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSuggestions>?) =
                        size > MAX_MEMORY_CACHE_SIZE
                },
            )

        private fun cacheKey(
            userId: UUID,
            libraryId: UUID,
            itemKind: BaseItemKind,
        ) = "${userId}_${libraryId}_${itemKind.serialName}"

        private fun cacheFile(
            userId: UUID,
            libraryId: UUID,
            itemKind: BaseItemKind,
        ) = File(context.cacheDir, "suggestions")
            .apply {
                if (!mkdirs() && !exists()) {
                    Timber.w("Failed to create suggestions cache directory")
                }
            }.resolve("${cacheKey(userId, libraryId, itemKind)}.json")

        suspend fun get(
            userId: UUID,
            libraryId: UUID,
            itemKind: BaseItemKind,
        ): CachedSuggestions? {
            val key = cacheKey(userId, libraryId, itemKind)
            memoryCache[key]?.let { return it }

            return withContext(Dispatchers.IO) {
                runCatching {
                    cacheFile(userId, libraryId, itemKind)
                        .takeIf { it.exists() }
                        ?.readText()
                        ?.let { json.decodeFromString<CachedSuggestions>(it) }
                        ?.also { memoryCache[key] = it }
                }.onFailure { Timber.w(it, "Failed to read suggestions cache") }
                    .getOrNull()
            }
        }

        suspend fun put(
            userId: UUID,
            libraryId: UUID,
            itemKind: BaseItemKind,
            ids: List<String>,
        ) {
            val key = cacheKey(userId, libraryId, itemKind)
            val cached = CachedSuggestions(ids)
            memoryCache[key] = cached

            withContext(Dispatchers.IO) {
                runCatching { cacheFile(userId, libraryId, itemKind).writeText(json.encodeToString(cached)) }
                    .onFailure { Timber.w(it, "Failed to write suggestions cache") }
            }
        }

        suspend fun clear() {
            memoryCache.clear()
            withContext(Dispatchers.IO) {
                runCatching {
                    File(context.cacheDir, "suggestions").deleteRecursively()
                }.onFailure {
                    Timber.w(it, "Failed to clear suggestions cache")
                }
            }
        }

        companion object {
            private const val MAX_MEMORY_CACHE_SIZE = 8
        }
    }
