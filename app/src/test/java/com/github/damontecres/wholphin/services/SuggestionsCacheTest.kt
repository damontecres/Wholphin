package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.data.model.BaseItem
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.*
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

    // Library isolation tests - verify different parentIds/itemKinds don't mix

    @Test
    fun differentParentIds_returnIsolatedCacheEntries() =
        runBlocking {
            val cache = testCacheWithTempDir()
            val movieLibraryId = UUID.randomUUID()
            val tvLibraryId = UUID.randomUUID()

            val movieItems = listOf(testItem("Movie 1"), testItem("Movie 2"))
            val tvItems = listOf(testItem("TV Show 1"), testItem("TV Show 2"), testItem("TV Show 3"))

            cache.put(movieLibraryId, BaseItemKind.MOVIE, movieItems)
            cache.put(tvLibraryId, BaseItemKind.MOVIE, tvItems)

            val loadedMovies = cache.get(movieLibraryId, BaseItemKind.MOVIE)
            val loadedTv = cache.get(tvLibraryId, BaseItemKind.MOVIE)

            assertNotNull(loadedMovies)
            assertNotNull(loadedTv)
            assertEquals(2, loadedMovies!!.items.size)
            assertEquals(3, loadedTv!!.items.size)
            assertEquals("Movie 1", loadedMovies.items[0].name)
            assertEquals("TV Show 1", loadedTv.items[0].name)
        }

    @Test
    fun differentItemKinds_returnIsolatedCacheEntries() =
        runBlocking {
            val cache = testCacheWithTempDir()
            val libraryId = UUID.randomUUID()

            val movies = listOf(testItem("Movie A"))
            val series = listOf(testItem("Series B"), testItem("Series C"))

            cache.put(libraryId, BaseItemKind.MOVIE, movies)
            cache.put(libraryId, BaseItemKind.SERIES, series)

            val loadedMovies = cache.get(libraryId, BaseItemKind.MOVIE)
            val loadedSeries = cache.get(libraryId, BaseItemKind.SERIES)

            assertNotNull(loadedMovies)
            assertNotNull(loadedSeries)
            assertEquals(1, loadedMovies!!.items.size)
            assertEquals(2, loadedSeries!!.items.size)
            assertEquals("Movie A", loadedMovies.items[0].name)
            assertEquals("Series B", loadedSeries.items[0].name)
        }

    @Test
    fun rapidLibrarySwitching_maintainsIsolation() =
        runBlocking {
            val cache = testCacheWithTempDir()
            val lib1 = UUID.randomUUID()
            val lib2 = UUID.randomUUID()
            val lib3 = UUID.randomUUID()

            val items1 = listOf(testItem("Lib1 Item"))
            val items2 = listOf(testItem("Lib2 Item"))
            val items3 = listOf(testItem("Lib3 Item"))

            // Simulate rapid switching: put -> get -> put -> get pattern
            cache.put(lib1, BaseItemKind.MOVIE, items1)
            assertEquals("Lib1 Item", cache.get(lib1, BaseItemKind.MOVIE)?.items?.firstOrNull()?.name)

            cache.put(lib2, BaseItemKind.MOVIE, items2)
            assertEquals("Lib2 Item", cache.get(lib2, BaseItemKind.MOVIE)?.items?.firstOrNull()?.name)

            // Switch back to lib1 - should still have correct data
            assertEquals("Lib1 Item", cache.get(lib1, BaseItemKind.MOVIE)?.items?.firstOrNull()?.name)

            cache.put(lib3, BaseItemKind.MOVIE, items3)
            assertEquals("Lib3 Item", cache.get(lib3, BaseItemKind.MOVIE)?.items?.firstOrNull()?.name)

            // Verify all libraries still have correct data after rapid switching
            assertEquals("Lib1 Item", cache.get(lib1, BaseItemKind.MOVIE)?.items?.firstOrNull()?.name)
            assertEquals("Lib2 Item", cache.get(lib2, BaseItemKind.MOVIE)?.items?.firstOrNull()?.name)
            assertEquals("Lib3 Item", cache.get(lib3, BaseItemKind.MOVIE)?.items?.firstOrNull()?.name)
        }

    @Test
    fun libraryIsolation_persistsToDisk() =
        runBlocking {
            val lib1 = UUID.randomUUID()
            val lib2 = UUID.randomUUID()

            val items1 = listOf(testItem("Library 1 Content"))
            val items2 = listOf(testItem("Library 2 Content"))

            // Write with first cache instance
            val cache1 = testCacheWithTempDir()
            cache1.put(lib1, BaseItemKind.MOVIE, items1)
            cache1.put(lib2, BaseItemKind.SERIES, items2)

            // Read with fresh cache instance (empty memory cache, reads from disk)
            val cache2 = testCacheWithTempDir()
            assertTrue(memoryCacheOf(cache2).isEmpty())

            val loaded1 = cache2.get(lib1, BaseItemKind.MOVIE)
            val loaded2 = cache2.get(lib2, BaseItemKind.SERIES)

            assertNotNull(loaded1)
            assertNotNull(loaded2)
            assertEquals("Library 1 Content", loaded1!!.items[0].name)
            assertEquals("Library 2 Content", loaded2!!.items[0].name)
        }

    private fun testItem(name: String): BaseItem =
        BaseItem(
            data = BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.MOVIE,
                name = name,
            ),
            useSeriesForPrimary = false,
        )
}
