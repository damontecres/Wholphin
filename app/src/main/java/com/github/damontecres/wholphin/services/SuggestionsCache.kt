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
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSuggestions>?): Boolean {
                        if (size <= MAX_MEMORY_CACHE_SIZE || eldest == null) return false
                        if (dirtyKeys.remove(eldest.key)) {
                            writeEntryToDisk(eldest.key, eldest.value)
                        }
                        return true
                    }
                },
            )

        @Volatile
        private var diskCacheLoaded = false
        private val dirtyKeys: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

        private fun writeEntryToDisk(
            key: String,
            cached: CachedSuggestions,
        ) {
            runCatching {
                val suggestionsDir = cacheDir.apply { mkdirs() }
                File(suggestionsDir, "$key.json").writeText(json.encodeToString(cached))
            }.onFailure { Timber.w(it, "Failed to write evicted cache: $key") }
        }

        private fun cacheKey(
            userId: UUID,
            libraryId: UUID,
            itemKind: BaseItemKind,
        ) = "${userId}_${libraryId}_${itemKind.serialName}"

        private val cacheDir: File
            get() = File(context.cacheDir, "suggestions")

        private suspend fun loadFromDisk() {
            if (diskCacheLoaded) return
            withContext(Dispatchers.IO) {
                synchronized(this@SuggestionsCache) {
                    if (diskCacheLoaded) return@withContext
                    val suggestionsDir = cacheDir
                    if (!suggestionsDir.exists()) {
                        diskCacheLoaded = true
                        return@withContext
                    }
                    suggestionsDir.listFiles()?.forEach { file ->
                        runCatching {
                            val key = file.nameWithoutExtension
                            val cached = json.decodeFromString<CachedSuggestions>(file.readText())
                            memoryCache[key] = cached
                        }.onFailure { Timber.w(it, "Failed to read cache file: ${file.name}") }
                    }
                    diskCacheLoaded = true
                }
            }
        }

        suspend fun get(
            userId: UUID,
            libraryId: UUID,
            itemKind: BaseItemKind,
        ): CachedSuggestions? {
            loadFromDisk()
            val key = cacheKey(userId, libraryId, itemKind)
            memoryCache[key]?.let { return it }
            return withContext(Dispatchers.IO) {
                runCatching {
                    File(cacheDir, "$key.json")
                        .takeIf { it.exists() }
                        ?.readText()
                        ?.let { json.decodeFromString<CachedSuggestions>(it) }
                        ?.also { memoryCache[key] = it }
                }.onFailure { Timber.w(it, "Failed to read cache: $key") }
                    .getOrNull()
            }
        }

        fun put(
            userId: UUID,
            libraryId: UUID,
            itemKind: BaseItemKind,
            ids: List<String>,
        ) {
            val key = cacheKey(userId, libraryId, itemKind)
            val cached = CachedSuggestions(ids)
            memoryCache[key] = cached
            dirtyKeys.add(key)
        }

        suspend fun save() {
            if (dirtyKeys.isEmpty()) return
            val toSave = synchronized(dirtyKeys) { dirtyKeys.toList().also { dirtyKeys.clear() } }
            withContext(Dispatchers.IO) {
                val suggestionsDir =
                    cacheDir.apply {
                        if (!mkdirs() && !exists()) Timber.w("Failed to create suggestions cache directory")
                    }
                toSave.forEach { key ->
                    memoryCache[key]?.let { cached ->
                        runCatching {
                            File(suggestionsDir, "$key.json").writeText(json.encodeToString(cached))
                        }.onFailure { Timber.w(it, "Failed to write cache: $key") }
                    }
                }
            }
        }

        suspend fun clear() {
            memoryCache.clear()
            dirtyKeys.clear()
            diskCacheLoaded = false
            withContext(Dispatchers.IO) {
                runCatching { cacheDir.deleteRecursively() }
                    .onFailure { Timber.w(it, "Failed to clear suggestions cache") }
            }
        }

        companion object {
            private const val MAX_MEMORY_CACHE_SIZE = 8
        }
    }
