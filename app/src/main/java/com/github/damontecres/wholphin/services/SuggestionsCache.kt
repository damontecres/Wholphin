@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.ui.toServerString
import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.ObjectKache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        private val cacheDir: File
            get() = File(context.cacheDir, "suggestions")
        private val json = Json { ignoreUnknownKeys = true }
        private val mutex = Mutex()

        private val memoryCache =
            InMemoryKache<String, CachedSuggestions>(maxSize = 8) {
                creationScope = CoroutineScope(Dispatchers.IO)
            }

        private fun cacheKey(
            userId: UUID,
            libraryId: UUID,
            itemKind: BaseItemKind,
        ) = "${userId.toServerString()}_${libraryId.toServerString()}_${itemKind.serialName}"

        @OptIn(ExperimentalSerializationApi::class)
        suspend fun get(
            userId: UUID,
            libraryId: UUID,
            itemKind: BaseItemKind,
        ): CachedSuggestions? {
            val key = cacheKey(userId, libraryId, itemKind)
            return memoryCache.getOrPut(key) {
                try {
                    mutex.withLock {
                        File(cacheDir, "$key.json").inputStream().use {
                            json.decodeFromStream<CachedSuggestions>(it)
                        }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception reading from disk cache")
                    null
                }
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        suspend fun put(
            userId: UUID,
            libraryId: UUID,
            itemKind: BaseItemKind,
            ids: List<UUID>,
        ) {
            val key = cacheKey(userId, libraryId, itemKind)
            val suggestions = CachedSuggestions(ids)
            memoryCache.put(key, suggestions)
            try {
                cacheDir.mkdirs()
                mutex.withLock {
                    File(cacheDir, "$key.json").outputStream().use {
                        json.encodeToStream(suggestions, it)
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Exception writing to disk cache")
            }
        }
    }

fun ObjectKache<*, *>.isEmpty(): Boolean = this.size == 0L

fun ObjectKache<*, *>.isNotEmpty(): Boolean = !isEmpty()

suspend fun <T : Any> ObjectKache<T, *>.containsKey(key: T): Boolean = get(key) != null
