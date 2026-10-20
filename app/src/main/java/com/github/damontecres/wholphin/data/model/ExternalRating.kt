package com.github.damontecres.wholphin.data.model

import androidx.compose.runtime.Immutable
import java.util.Locale
import java.util.UUID

/**
 * A single rating from a third party source, such as from the MDBList Ratings server plugin
 *
 * @param source the source key used by the plugin, eg "imdb", "tomatoes", "popcorn"
 * @param value the rating in the source's own scale, eg 7.3 for imdb (out of 10) or 95 for tomatoes (percent)
 * @param score the rating normalized to 0-100 if available
 * @param iconUrl URL to the source's icon on the server
 */
@Immutable
data class ExternalRating(
    val source: String,
    val value: Double,
    val score: Int?,
    val iconUrl: String? = null,
) {
    /**
     * The icon for this rating, or null if the source isn't known
     */
    val icon: ExternalRatingIcon? get() = ExternalRatingIcon.forRating(this)

    /**
     * Text to display for the rating, formatted for the source's scale
     *
     * Some sources, such as TMDB, use a percentage for movies & series, but out of 10 for episodes
     */
    val displayText: String
        get() =
            if (source in ALWAYS_PERCENT_SOURCES || (source in PERCENT_SOURCES && value > 10.0)) {
                "${value.toInt()}%"
            } else if (value <= 10.0) {
                String.format(Locale.getDefault(), "%.1f", value)
            } else {
                value.toInt().toString()
            }

    companion object {
        private val ALWAYS_PERCENT_SOURCES = setOf("tomatoes", "popcorn")
        private val PERCENT_SOURCES = setOf("tmdb", "trakt")
    }
}

/**
 * The [ExternalRating]s for a specific item
 */
@Immutable
data class ExternalRatings(
    val itemId: UUID? = null,
    val ratings: List<ExternalRating> = emptyList(),
)

/**
 * Icons served by the MDBList Ratings server plugin at `/Plugins/MdbListRatings/Assets/{fileName}`
 *
 * @param fileName the name of the asset file on the server
 * @param aspectRatio width divided by height of the image
 */
enum class ExternalRatingIcon(
    val fileName: String,
    val aspectRatio: Float,
) {
    IMDB("IMDb.png", 1.5f),
    TMDB("TMDB.png", 1.38f),
    TOMATOES_FRESH("Rotten_Tomatoes.png", 1f),
    TOMATOES_ROTTEN("Rotten_Tomatoes_rotten.png", 1f),
    POPCORN_FRESH("Rotten_Tomatoes_positive_audience.png", .76f),
    POPCORN_ROTTEN("Rotten_Tomatoes_negative_audience.png", 1.32f),
    METACRITIC("Metacritic.png", 1f),
    METACRITIC_USER("mus2.png", 1f),
    ROGER_EBERT("Roger_Ebert.png", 1f),
    TRAKT("Trakt.png", 1f),
    LETTERBOXD("letterboxd.png", 1f),
    MYANIMELIST("mal.png", 1f),
    ANILIST("anilist.png", 1f),
    KINOPOISK("kinopoisk.png", 1f),
    SIMKL("simkl.png", 1f),
    TVMAZE("tvmaze.png", 3.16f),
    ;

    companion object {
        /**
         * The fresh/rotten threshold matches the plugin's web UI
         */
        private const val FRESH_THRESHOLD = 60

        fun forRating(rating: ExternalRating): ExternalRatingIcon? {
            val score = rating.score ?: rating.value.toInt()
            return when (rating.source) {
                "imdb" -> IMDB
                "tmdb" -> TMDB
                "tomatoes" -> if (score in 1..<FRESH_THRESHOLD) TOMATOES_ROTTEN else TOMATOES_FRESH
                "popcorn" -> if (score in 1..<FRESH_THRESHOLD) POPCORN_ROTTEN else POPCORN_FRESH
                "metacritic" -> METACRITIC
                "metacriticuser" -> METACRITIC_USER
                "rogerebert" -> ROGER_EBERT
                "trakt" -> TRAKT
                "letterboxd" -> LETTERBOXD
                "myanimelist" -> MYANIMELIST
                "anilist" -> ANILIST
                "kinopoisk" -> KINOPOISK
                "simkl" -> SIMKL
                "tvmaze" -> TVMAZE
                else -> null
            }
        }
    }
}
