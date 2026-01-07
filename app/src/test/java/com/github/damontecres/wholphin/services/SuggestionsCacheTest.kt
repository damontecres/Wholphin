package com.github.damontecres.wholphin.services

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.io.path.createTempDirectory

class SuggestionsCacheTest {
    private val tempDir = createTempDirectory("suggestions-cache-test").toFile()

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
    fun putThenGet_returnsCachedSuggestions() =
        runBlocking {
            val cache = testCacheWithTempDir()
            val libId = UUID.randomUUID()

            // Put an empty list
            cache.put(libId, BaseItemKind.MOVIE, emptyList())

            val loaded = cache.get(libId, BaseItemKind.MOVIE)
            assertNotNull(loaded)
            assertEquals(0, loaded!!.items.size)
        }

    @Test
    fun get_readsFromDisk_whenMemoryAbsent() =
        runBlocking {
            val cache1 = testCacheWithTempDir()
            val libId = UUID.randomUUID()

            cache1.put(libId, BaseItemKind.MOVIE, emptyList())

            // Create a fresh instance which won't have the memory entry
            val cache2 = testCacheWithTempDir()
            // memoryCache should be empty
            assertTrue(memoryCacheOf(cache2).isEmpty())

            val loaded = cache2.get(libId, BaseItemKind.MOVIE)
            assertNotNull(loaded)
            assertEquals(0, loaded!!.items.size)
            // After read, memory cache should contain the entry
            assertTrue(memoryCacheOf(cache2).isNotEmpty())
        }

    // LRU behavior is not enforced in production; keep tests focused on public behavior.
    @Test
    fun memoryCache_respectsLruLimit() =
        runBlocking {
            val cache = testCacheWithTempDir()

            // Insert MAX + 2 entries and ensure size never exceeds limit
            val limit = 8 // keep in sync with implementation
            val ids = mutableListOf<UUID>()
            for (i in 0 until (limit + 2)) {
                val id = UUID.randomUUID()
                ids.add(id)
                cache.put(id, BaseItemKind.MOVIE, emptyList())
            }

            // memoryCache should be bounded to the limit
            val mem = memoryCacheOf(cache)
            assertTrue(mem.size <= limit)
            // The oldest (first inserted) should be evicted from memory cache
            val firstKey = "${ids.first()}_${BaseItemKind.MOVIE.serialName}"
            assertFalse(mem.containsKey(firstKey))
        }
}
