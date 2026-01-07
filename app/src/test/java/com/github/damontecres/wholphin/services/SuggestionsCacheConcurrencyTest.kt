package com.github.damontecres.wholphin.services

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createTempDirectory

class SuggestionsCacheConcurrencyTest {
    private val tempDir = createTempDirectory("suggestions-cache-concurrency-test").toFile()

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun testCacheWithTempDir(): SuggestionsCache {
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.cacheDir } returns tempDir
        return SuggestionsCache(mockContext)
    }

    private fun memoryCacheOf(cache: SuggestionsCache): MutableMap<String, CachedSuggestions> {
        val field = SuggestionsCache::class.java.getDeclaredField("memoryCache")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(cache) as MutableMap<String, CachedSuggestions>
    }

    @Test
    fun concurrentPutGet_noCrashes_and_consistentState() =
        runBlocking {
            val cache = testCacheWithTempDir()

            // small set of library IDs to operate on
            val libIds = List(20) { UUID.randomUUID() }
            val putIds = Collections.newSetFromMap(ConcurrentHashMap<UUID, Boolean>())

            // launch a number of concurrent coroutines to stress get/put
            val coroutines = mutableListOf<kotlinx.coroutines.Job>()

            // Use a timeout to avoid flakiness / infinite hangs
            withTimeout(30_000) {
                repeat(50) {
                    val job =
                        launch(Dispatchers.Default) {
                            repeat(200) {
                                val id = libIds.random()
                                if (Math.random() < 0.5) {
                                    // perform put with an empty list (cheap)
                                    cache.put(id, BaseItemKind.MOVIE, emptyList())
                                    putIds.add(id)
                                } else {
                                    // perform get
                                    try {
                                        cache.get(id, BaseItemKind.MOVIE)
                                    } catch (ex: Exception) {
                                        // Fail early on unexpected exceptions
                                        throw ex
                                    }
                                }
                            }
                        }
                    coroutines.add(job)
                }

                coroutines.joinAll()
            }

            // After concurrent operations, validate no obvious corruption:
            // 1) memory cache size must be bounded by the configured max
            val mem = memoryCacheOf(cache)
            assertTrue("memory cache exceeded limit: ${mem.size}", mem.size <= 8)

            // 2) for each id we successfully put at least once, we should be able to read from disk/non-null
            for (id in putIds) {
                val loaded = cache.get(id, BaseItemKind.MOVIE)
                assertNotNull("Expected cached file/read for $id", loaded)
            }
        }
}
