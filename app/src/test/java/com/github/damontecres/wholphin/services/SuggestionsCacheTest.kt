package com.github.damontecres.wholphin.services

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
            val userId = UUID.randomUUID()
            val libId = UUID.randomUUID()

            cache.put(userId, libId, BaseItemKind.MOVIE, emptyList())

            val loaded = cache.get(userId, libId, BaseItemKind.MOVIE)
            assertNotNull(loaded)
            assertEquals(0, loaded!!.ids.size)
        }

    @Test
    fun get_readsFromDisk_whenMemoryAbsent() =
        runBlocking {
            val cache1 = testCacheWithTempDir()
            val userId = UUID.randomUUID()
            val libId = UUID.randomUUID()

            cache1.put(userId, libId, BaseItemKind.MOVIE, emptyList())

            // Create a fresh instance which won't have the memory entry
            val cache2 = testCacheWithTempDir()
            // memoryCache should be empty
            assertTrue(memoryCacheOf(cache2).isEmpty())

            val loaded = cache2.get(userId, libId, BaseItemKind.MOVIE)
            assertNotNull(loaded)
            assertEquals(0, loaded!!.ids.size)
            // After read, memory cache should contain the entry
            assertTrue(memoryCacheOf(cache2).isNotEmpty())
        }

    // LRU behavior is not enforced in production; keep tests focused on public behavior.
    @Test
    fun memoryCache_respectsLruLimit() =
        runBlocking {
            val cache = testCacheWithTempDir()
            val userId = UUID.randomUUID()

            // Insert MAX + 2 entries and ensure size never exceeds limit
            val limit = 8 // keep in sync with implementation
            val libIds = mutableListOf<UUID>()
            for (i in 0 until (limit + 2)) {
                val libId = UUID.randomUUID()
                libIds.add(libId)
                cache.put(userId, libId, BaseItemKind.MOVIE, emptyList())
            }

            // memoryCache should be bounded to the limit
            val mem = memoryCacheOf(cache)
            assertTrue(mem.size <= limit)
            // The oldest (first inserted) should be evicted from memory cache
            val firstKey = "${userId}_${libIds.first()}_${BaseItemKind.MOVIE.serialName}"
            assertFalse(mem.containsKey(firstKey))
        }

    // Library isolation tests - verify different parentIds/itemKinds don't mix

    @Test
    fun differentParentIds_returnIsolatedCacheEntries() =
        runBlocking {
            val cache = testCacheWithTempDir()
            val userId = UUID.randomUUID()
            val movieLibraryId = UUID.randomUUID()
            val tvLibraryId = UUID.randomUUID()

            val movieIds = listOf("movie-1", "movie-2")
            val tvIds = listOf("tv-1", "tv-2", "tv-3")

            cache.put(userId, movieLibraryId, BaseItemKind.MOVIE, movieIds)
            cache.put(userId, tvLibraryId, BaseItemKind.MOVIE, tvIds)

            val loadedMovies = cache.get(userId, movieLibraryId, BaseItemKind.MOVIE)
            val loadedTv = cache.get(userId, tvLibraryId, BaseItemKind.MOVIE)

            assertNotNull(loadedMovies)
            assertNotNull(loadedTv)
            assertEquals(2, loadedMovies!!.ids.size)
            assertEquals(3, loadedTv!!.ids.size)
            assertEquals("movie-1", loadedMovies.ids[0])
            assertEquals("tv-1", loadedTv.ids[0])
        }

    @Test
    fun differentItemKinds_returnIsolatedCacheEntries() =
        runBlocking {
            val cache = testCacheWithTempDir()
            val userId = UUID.randomUUID()
            val libraryId = UUID.randomUUID()

            val movieIds = listOf("movie-a")
            val seriesIds = listOf("series-b", "series-c")

            cache.put(userId, libraryId, BaseItemKind.MOVIE, movieIds)
            cache.put(userId, libraryId, BaseItemKind.SERIES, seriesIds)

            val loadedMovies = cache.get(userId, libraryId, BaseItemKind.MOVIE)
            val loadedSeries = cache.get(userId, libraryId, BaseItemKind.SERIES)

            assertNotNull(loadedMovies)
            assertNotNull(loadedSeries)
            assertEquals(1, loadedMovies!!.ids.size)
            assertEquals(2, loadedSeries!!.ids.size)
            assertEquals("movie-a", loadedMovies.ids[0])
            assertEquals("series-b", loadedSeries.ids[0])
        }

    @Test
    fun rapidLibrarySwitching_maintainsIsolation() =
        runBlocking {
            val cache = testCacheWithTempDir()
            val userId = UUID.randomUUID()
            val lib1 = UUID.randomUUID()
            val lib2 = UUID.randomUUID()
            val lib3 = UUID.randomUUID()

            val ids1 = listOf("lib1-item")
            val ids2 = listOf("lib2-item")
            val ids3 = listOf("lib3-item")

            // Simulate rapid switching: put -> get -> put -> get pattern
            cache.put(userId, lib1, BaseItemKind.MOVIE, ids1)
            assertEquals("lib1-item", cache.get(userId, lib1, BaseItemKind.MOVIE)?.ids?.firstOrNull())

            cache.put(userId, lib2, BaseItemKind.MOVIE, ids2)
            assertEquals("lib2-item", cache.get(userId, lib2, BaseItemKind.MOVIE)?.ids?.firstOrNull())

            // Switch back to lib1 - should still have correct data
            assertEquals("lib1-item", cache.get(userId, lib1, BaseItemKind.MOVIE)?.ids?.firstOrNull())

            cache.put(userId, lib3, BaseItemKind.MOVIE, ids3)
            assertEquals("lib3-item", cache.get(userId, lib3, BaseItemKind.MOVIE)?.ids?.firstOrNull())

            // Verify all libraries still have correct data after rapid switching
            assertEquals("lib1-item", cache.get(userId, lib1, BaseItemKind.MOVIE)?.ids?.firstOrNull())
            assertEquals("lib2-item", cache.get(userId, lib2, BaseItemKind.MOVIE)?.ids?.firstOrNull())
            assertEquals("lib3-item", cache.get(userId, lib3, BaseItemKind.MOVIE)?.ids?.firstOrNull())
        }

    @Test
    fun libraryIsolation_persistsToDisk() =
        runBlocking {
            val userId = UUID.randomUUID()
            val lib1 = UUID.randomUUID()
            val lib2 = UUID.randomUUID()

            val ids1 = listOf("lib1-content")
            val ids2 = listOf("lib2-content")

            // Write with first cache instance
            val cache1 = testCacheWithTempDir()
            cache1.put(userId, lib1, BaseItemKind.MOVIE, ids1)
            cache1.put(userId, lib2, BaseItemKind.SERIES, ids2)

            // Read with fresh cache instance (empty memory cache, reads from disk)
            val cache2 = testCacheWithTempDir()
            assertTrue(memoryCacheOf(cache2).isEmpty())

            val loaded1 = cache2.get(userId, lib1, BaseItemKind.MOVIE)
            val loaded2 = cache2.get(userId, lib2, BaseItemKind.SERIES)

            assertNotNull(loaded1)
            assertNotNull(loaded2)
            assertEquals("lib1-content", loaded1!!.ids[0])
            assertEquals("lib2-content", loaded2!!.ids[0])
        }

    @Test
    fun differentUsers_returnIsolatedCacheEntries() =
        runBlocking {
            val cache = testCacheWithTempDir()
            val user1 = UUID.randomUUID()
            val user2 = UUID.randomUUID()
            val libraryId = UUID.randomUUID()

            val user1Ids = listOf("user1-item")
            val user2Ids = listOf("user2-item-a", "user2-item-b")

            cache.put(user1, libraryId, BaseItemKind.MOVIE, user1Ids)
            cache.put(user2, libraryId, BaseItemKind.MOVIE, user2Ids)

            val loadedUser1 = cache.get(user1, libraryId, BaseItemKind.MOVIE)
            val loadedUser2 = cache.get(user2, libraryId, BaseItemKind.MOVIE)

            assertNotNull(loadedUser1)
            assertNotNull(loadedUser2)
            assertEquals(1, loadedUser1!!.ids.size)
            assertEquals(2, loadedUser2!!.ids.size)
            assertEquals("user1-item", loadedUser1.ids[0])
            assertEquals("user2-item-a", loadedUser2.ids[0])
        }
}
