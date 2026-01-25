package com.github.damontecres.wholphin.test

import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.NameGuidPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests for the suggestions deduplication and genre collection logic used in
 * RecommendedMovie and RecommendedTvShow ViewModels.
 */
class TestSuggestionsDeduplication {
    @Test
    fun `deduplication by seriesId groups episodes of same series`() {
        val seriesId = UUID.randomUUID()
        val items =
            listOf(
                episode(seriesId = seriesId, name = "Episode 1"),
                episode(seriesId = seriesId, name = "Episode 2"),
                episode(seriesId = seriesId, name = "Episode 3"),
                movie(name = "Some Movie"),
            )

        val deduplicated =
            items
                .distinctBy { it.seriesId ?: it.id }
                .take(3)

        // Should have 2 items: one series entry and one movie
        assertEquals(2, deduplicated.size)
    }

    @Test
    fun `deduplication preserves order - most recent first`() {
        val series1 = UUID.randomUUID()
        val series2 = UUID.randomUUID()
        val items =
            listOf(
                episode(seriesId = series1, name = "S1 Episode 1"), // Most recent
                episode(seriesId = series1, name = "S1 Episode 2"),
                episode(seriesId = series2, name = "S2 Episode 1"),
                movie(name = "Old Movie"),
            )

        val deduplicated =
            items
                .distinctBy { it.seriesId ?: it.id }
                .take(3)

        assertEquals(3, deduplicated.size)
        // First item should be from series1 (most recent)
        assertEquals(series1, deduplicated[0].seriesId)
        assertEquals(series2, deduplicated[1].seriesId)
    }

    @Test
    fun `movies use their own id for deduplication`() {
        val items =
            listOf(
                movie(name = "Movie 1"),
                movie(name = "Movie 2"),
                movie(name = "Movie 3"),
            )

        val deduplicated =
            items
                .distinctBy { it.seriesId ?: it.id }
                .take(3)

        // All movies should be kept since they have unique IDs
        assertEquals(3, deduplicated.size)
    }

    @Test
    fun `take 3 limits seed items`() {
        val items =
            listOf(
                movie(name = "Movie 1"),
                movie(name = "Movie 2"),
                movie(name = "Movie 3"),
                movie(name = "Movie 4"),
                movie(name = "Movie 5"),
            )

        val deduplicated =
            items
                .distinctBy { it.seriesId ?: it.id }
                .take(3)

        assertEquals(3, deduplicated.size)
    }
}

class TestSuggestionsGenreCollection {
    @Test
    fun `collects genres from multiple seed items`() {
        val genre1 = NameGuidPair(id = UUID.randomUUID(), name = "Action")
        val genre2 = NameGuidPair(id = UUID.randomUUID(), name = "Comedy")
        val genre3 = NameGuidPair(id = UUID.randomUUID(), name = "Drama")

        val seedItems =
            listOf(
                movie(name = "Action Movie", genres = listOf(genre1)),
                movie(name = "Comedy Movie", genres = listOf(genre2)),
                movie(name = "Drama Movie", genres = listOf(genre3)),
            )

        val allGenreIds =
            seedItems
                .flatMap { it.genreItems?.mapNotNull { g -> g.id } ?: emptyList() }
                .distinct()

        assertEquals(3, allGenreIds.size)
        assertTrue(allGenreIds.contains(genre1.id))
        assertTrue(allGenreIds.contains(genre2.id))
        assertTrue(allGenreIds.contains(genre3.id))
    }

    @Test
    fun `deduplicates shared genres across seed items`() {
        val sharedGenre = NameGuidPair(id = UUID.randomUUID(), name = "Action")
        val uniqueGenre = NameGuidPair(id = UUID.randomUUID(), name = "Comedy")

        val seedItems =
            listOf(
                movie(name = "Action Movie 1", genres = listOf(sharedGenre)),
                movie(name = "Action Movie 2", genres = listOf(sharedGenre)),
                movie(name = "Action Comedy", genres = listOf(sharedGenre, uniqueGenre)),
            )

        val allGenreIds =
            seedItems
                .flatMap { it.genreItems?.mapNotNull { g -> g.id } ?: emptyList() }
                .distinct()

        // Should only have 2 unique genres
        assertEquals(2, allGenreIds.size)
    }

    @Test
    fun `handles items with no genres`() {
        val genre1 = NameGuidPair(id = UUID.randomUUID(), name = "Action")

        val seedItems =
            listOf(
                movie(name = "Movie with genre", genres = listOf(genre1)),
                movie(name = "Movie without genre", genres = null),
                movie(name = "Movie with empty genres", genres = emptyList()),
            )

        val allGenreIds =
            seedItems
                .flatMap { it.genreItems?.mapNotNull { g -> g.id } ?: emptyList() }
                .distinct()

        assertEquals(1, allGenreIds.size)
        assertEquals(genre1.id, allGenreIds[0])
    }

    @Test
    fun `returns empty list when no seed items have genres`() {
        val seedItems =
            listOf(
                movie(name = "Movie 1", genres = null),
                movie(name = "Movie 2", genres = emptyList()),
            )

        val allGenreIds =
            seedItems
                .flatMap { it.genreItems?.mapNotNull { g -> g.id } ?: emptyList() }
                .distinct()

        assertTrue(allGenreIds.isEmpty())
    }
}

class TestSuggestionsExcludeIds {
    @Test
    fun `excludeIds contains all seed item ids`() {
        val seedItems =
            listOf(
                movie(name = "Movie 1"),
                movie(name = "Movie 2"),
                movie(name = "Movie 3"),
            )

        val excludeIds = seedItems.map { it.id }

        assertEquals(3, excludeIds.size)
        seedItems.forEach { item ->
            assertTrue(excludeIds.contains(item.id))
        }
    }
}

@RunWith(Parameterized::class)
class TestSuggestionsCombineAndDeduplicate(
    private val contextual: List<BaseItemDto>,
    private val random: List<BaseItemDto>,
    private val fresh: List<BaseItemDto>,
    private val expectedUniqueCount: Int,
    private val description: String,
) {
    @Test
    fun `combine and deduplicate works correctly`() {
        val combined =
            (contextual + random + fresh)
                .distinctBy { it.id }

        assertEquals(description, expectedUniqueCount, combined.size)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{4}")
        fun data(): Collection<Array<Any>> {
            val movie1 = movie(name = "Movie 1")
            val movie2 = movie(name = "Movie 2")
            val movie3 = movie(name = "Movie 3")
            val movie4 = movie(name = "Movie 4")

            return listOf(
                arrayOf(
                    listOf(movie1, movie2),
                    listOf(movie3),
                    listOf(movie4),
                    4,
                    "no duplicates - all 4 unique",
                ),
                arrayOf(
                    listOf(movie1, movie2),
                    listOf(movie1, movie3),
                    listOf(movie2, movie4),
                    4,
                    "with duplicates - deduplicates to 4",
                ),
                arrayOf(
                    listOf(movie1),
                    listOf(movie1),
                    listOf(movie1),
                    1,
                    "all same - deduplicates to 1",
                ),
                arrayOf(
                    emptyList<BaseItemDto>(),
                    listOf(movie1, movie2),
                    listOf(movie3),
                    3,
                    "empty contextual - still combines others",
                ),
                arrayOf(
                    emptyList<BaseItemDto>(),
                    emptyList<BaseItemDto>(),
                    emptyList<BaseItemDto>(),
                    0,
                    "all empty - returns empty",
                ),
            )
        }
    }
}

// Helper functions to create test data

private fun movie(
    id: UUID = UUID.randomUUID(),
    name: String = "Test Movie",
    genres: List<NameGuidPair>? = null,
): BaseItemDto =
    BaseItemDto(
        id = id,
        type = BaseItemKind.MOVIE,
        name = name,
        seriesId = null,
        genreItems = genres,
    )

private fun episode(
    id: UUID = UUID.randomUUID(),
    seriesId: UUID,
    name: String = "Test Episode",
    genres: List<NameGuidPair>? = null,
): BaseItemDto =
    BaseItemDto(
        id = id,
        type = BaseItemKind.EPISODE,
        name = name,
        seriesId = seriesId,
        genreItems = genres,
    )
