package com.github.damontecres.wholphin.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.io.File
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
        private val _cacheVersion = MutableStateFlow(0L)
        val cacheVersion: StateFlow<Long> = _cacheVersion.asStateFlow()

        private val memoryCache: MutableMap<String, CachedSuggestions> =
            LinkedHashMap(MAX_MEMORY_CACHE_SIZE, 0.75f, true)

        @Volatile
        private var diskCacheLoaded = false
        private val dirtyKeys: MutableSet<String> = mutableSetOf()
        private val mutex = Mutex()

        private fun writeEntryToDisk(
            key: String,
            cached: CachedSuggestions,
        ) {
            runCatching {
                val suggestionsDir = cacheDir.apply { mkdirs() }
                File(suggestionsDir, "$key.json").writeText(json.encodeToString(cached))
            }.onFailure { Timber.w(it, "Failed to write evicted cache: $key") }
        }

        private fun checkForEviction(newKey: String): Pair<String, CachedSuggestions>? {
            if (memoryCache.containsKey(newKey) || memoryCache.size < MAX_MEMORY_CACHE_SIZE) {
                return null
            }
            val eldest = memoryCache.entries.firstOrNull() ?: return null
            memoryCache.remove(eldest.key)
            return if (dirtyKeys.remove(eldest.key)) eldest.key to eldest.value else null
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
            mutex.withLock {
                if (diskCacheLoaded) return@withLock
                withContext(Dispatchers.IO) {
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

        suspend fun put(
            userId: UUID,
            libraryId: UUID,
            itemKind: BaseItemKind,
            ids: List<String>,
        ) {
            val key = cacheKey(userId, libraryId, itemKind)
            val cached = CachedSuggestions(ids)
            val evictedEntry =
                mutex.withLock {
                    val evicted = checkForEviction(key)
                    memoryCache[key] = cached
                    dirtyKeys.add(key)
                    _cacheVersion.update { it + 1 }
                    evicted
                }
            evictedEntry?.let { (evictedKey, evictedValue) ->
                withContext(Dispatchers.IO) {
                    writeEntryToDisk(evictedKey, evictedValue)
                }
            }
        }

        suspend fun isEmpty(): Boolean =
            mutex.withLock {
                if (memoryCache.isNotEmpty() || dirtyKeys.isNotEmpty()) {
                    return@withLock false
                }
                withContext(Dispatchers.IO) {
                    val files = cacheDir.listFiles()
                    files == null || files.isEmpty()
                }
            }

        suspend fun save() {
            val entriesToSave =
                mutex.withLock {
                    if (dirtyKeys.isEmpty()) return
                    val entries =
                        dirtyKeys.mapNotNull { key ->
                            memoryCache[key]?.let { key to it }
                        }
                    dirtyKeys.clear()
                    entries
                }

            withContext(Dispatchers.IO) {
                val suggestionsDir =
                    cacheDir.apply {
                        if (!mkdirs() && !exists()) Timber.w("Failed to create suggestions cache directory")
                    }
                entriesToSave.forEach { (key, value) ->
                    runCatching {
                        File(suggestionsDir, "$key.json").writeText(json.encodeToString(value))
                    }.onFailure { Timber.w(it, "Failed to write cache: $key") }
                }
            }
        }

        suspend fun clear() {
            mutex.withLock {
                memoryCache.clear()
                dirtyKeys.clear()
                _cacheVersion.update { it + 1 }
                diskCacheLoaded = false
            }
            withContext(Dispatchers.IO) {
                runCatching { cacheDir.deleteRecursively() }
                    .onFailure { Timber.w(it, "Failed to clear suggestions cache") }
            }
        }

        companion object {
            private const val MAX_MEMORY_CACHE_SIZE = 8
        }
    }
