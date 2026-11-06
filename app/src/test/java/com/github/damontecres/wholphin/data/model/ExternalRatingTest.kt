package com.github.damontecres.wholphin.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalRatingTest {
    private fun rating(
        source: String,
        value: Double,
        score: Int? = null,
    ) = ExternalRating(source = source, value = value, score = score)

    @Test
    fun formatsEachSourceInItsOwnScale() {
        assertEquals("7.3", rating("imdb", 7.3).displayText)
        assertEquals("95%", rating("tomatoes", 95.0).displayText)
        assertEquals("77%", rating("popcorn", 77.0).displayText)
        assertEquals("3.7", rating("letterboxd", 3.7).displayText)
        assertEquals("78", rating("metacritic", 78.0).displayText)
    }

    @Test
    fun tmdbIsPercentForMoviesButOutOfTenForEpisodes() {
        assertEquals("71%", rating("tmdb", 71.0).displayText)
        assertEquals("8.3", rating("tmdb", 8.3).displayText)
        assertEquals("75%", rating("trakt", 75.0).displayText)
        assertEquals("8.0", rating("trakt", 8.0).displayText)
    }

    @Test
    fun tomatoesAreAlwaysPercent() {
        assertEquals("5%", rating("tomatoes", 5.0).displayText)
    }

    @Test
    fun freshOrRottenIcons() {
        assertEquals(ExternalRatingIcon.TOMATOES_FRESH, rating("tomatoes", 95.0, 95).icon)
        assertEquals(ExternalRatingIcon.TOMATOES_FRESH, rating("tomatoes", 60.0, 60).icon)
        assertEquals(ExternalRatingIcon.TOMATOES_ROTTEN, rating("tomatoes", 59.0, 59).icon)
        assertEquals(ExternalRatingIcon.POPCORN_FRESH, rating("popcorn", 77.0, 77).icon)
        assertEquals(ExternalRatingIcon.POPCORN_ROTTEN, rating("popcorn", 40.0, 40).icon)
    }

    @Test
    fun unknownSourceHasNoIcon() {
        assertNull(rating("whatson_imdb", 7.7).icon)
        assertNull(rating("mdblist", 83.0).icon)
    }
}
