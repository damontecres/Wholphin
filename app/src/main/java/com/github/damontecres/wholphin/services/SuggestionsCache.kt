@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.ui.toServerString
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CachedSuggestions(
    val ids: List<UUID>,
)

@Singleton
class SuggestionsCache
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val json = Json { ignoreUnknownKeys = true }
        private val _cacheVersion = MutableStateFlow(0L)
        val cacheVersion: StateFlow<Long> = _cacheVersion.asStateFlow()

        private val memoryCache: MutableMap<String, CachedSuggestions> =
            LinkedHashMap(MAX_MEMORY_CACHE_SIZE, 0.75f, true)

        @Volatile
        private var diskCacheLoadedUserId: UUID? = null
        private val dirtyKeys: MutableSet<String> = mutableSetOf()
        private val mutex = Mutex()

        @OptIn(ExperimentalSerializationApi::class)
        private fun writeEntryToDisk(
            key: String,
            cached: CachedSuggestions,
        ) {
            runCatching {
                val suggestionsDir = cacheDir.apply { mkdirs() }
                File(suggestionsDir, "$key.json")
                    .outputStream()
                    .use { json.encodeToStream(cached, it) }
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
        ) = "${userId.toServerString()}_${libraryId.toServerString()}_${itemKind.serialName}"

        private val cacheDir: File
            get() = File(context.cacheDir, "suggestions")

        @OptIn(ExperimentalSerializationApi::class)
        private suspend fun loadFromDisk(userId: UUID) {
            if (diskCacheLoadedUserId == userId) return
            mutex.withLock {
                if (diskCacheLoadedUserId == userId) return@withLock
                withContext(Dispatchers.IO) {
                    val suggestionsDir = cacheDir
                    if (!suggestionsDir.exists()) {
                        diskCacheLoadedUserId = userId
                        return@withContext
                    }
                    memoryCache.clear()
                    suggestionsDir
                        .listFiles {
                            it.name.startsWith(userId.toServerString())
                        }.orEmpty()
                        .take(MAX_MEMORY_CACHE_SIZE)
                        .forEach { file ->
                            runCatching {
                                val key = file.nameWithoutExtension
                                val cached =
                                    file
                                        .inputStream()
                                        .use { json.decodeFromStream<CachedSuggestions>(it) }
                                memoryCache[key] = cached
                            }.onFailure { Timber.w(it, "Failed to read cache file: ${file.name}") }
                        }
                    diskCacheLoadedUserId = userId
                }
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        suspend fun get(
            userId: UUID,
            libraryId: UUID,
            itemKind: BaseItemKind,
        ): CachedSuggestions? {
            loadFromDisk(userId)
            val key = cacheKey(userId, libraryId, itemKind)
            memoryCache[key]?.let { return it }
            return withContext(Dispatchers.IO) {
                runCatching {
                    File(cacheDir, "$key.json")
                        .takeIf { it.exists() }
                        ?.inputStream()
                        ?.use {
                            json.decodeFromStream<CachedSuggestions>(it)
                        }?.also { memoryCache[key] = it }
                }.onFailure { Timber.w(it, "Failed to read cache: $key") }
                    .getOrNull()
            }
        }

        suspend fun put(
            userId: UUID,
            libraryId: UUID,
            itemKind: BaseItemKind,
            ids: List<UUID>,
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

        @OptIn(ExperimentalSerializationApi::class)
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
                        File(suggestionsDir, "$key.json")
                            .outputStream()
                            .use { json.encodeToStream(value, it) }
                    }.onFailure { Timber.w(it, "Failed to write cache: $key") }
                }
            }
        }

        suspend fun clear() {
            mutex.withLock {
                memoryCache.clear()
                dirtyKeys.clear()
                _cacheVersion.update { it + 1 }
                diskCacheLoadedUserId = null
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
