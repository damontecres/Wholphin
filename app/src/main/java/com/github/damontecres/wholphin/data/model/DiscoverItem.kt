package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.api.seerr.model.MovieMovieIdRatingsGet200Response
import com.github.damontecres.wholphin.api.seerr.model.MovieResult
import com.github.damontecres.wholphin.api.seerr.model.TvResult
import com.github.damontecres.wholphin.services.SeerrSearchResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SeerrItemType {
    @SerialName("movie")
    MOVIE,

    @SerialName("tv")
    TV,

    @SerialName("person")
    PERSON,

    @SerialName("unknown")
    UNKNOWN,
    ;

    companion object {
        fun fromString(str: String?) =
            when (str) {
                "movie" -> MOVIE
                "tv" -> TV
                "person" -> PERSON
                else -> UNKNOWN
            }
    }
}

@Serializable
enum class SeerrAvailability(
    val status: Int,
) {
    UNKNOWN(1),
    PENDING(2),
    PROCESSING(3),
    PARTIALLY_AVAILABLE(4),
    AVAILABLE(5),
    DELETED(6),
    ;

    companion object {
        fun from(status: Int?) = entries.firstOrNull { it.status == status }
    }
}

@Serializable
data class DiscoverItem(
    val id: Int,
    val type: SeerrItemType,
    val title: String?,
    val availability: SeerrAvailability,
    val releaseDate: String?,
    val posterPath: String?,
    val backdropPath: String?,
) {
    constructor(movie: MovieResult) : this(
        id = movie.id,
        type = SeerrItemType.MOVIE,
        title = movie.title,
        availability = SeerrAvailability.from(movie.mediaInfo?.status) ?: SeerrAvailability.UNKNOWN,
        releaseDate = movie.releaseDate,
        posterPath = movie.posterPath,
        backdropPath = movie.backdropPath,
    )

    constructor(tv: TvResult) : this(
        id = tv.id!!,
        type = SeerrItemType.MOVIE,
        title = tv.name,
        availability = SeerrAvailability.from(tv.mediaInfo?.status) ?: SeerrAvailability.UNKNOWN,
        releaseDate = tv.firstAirDate,
        posterPath = tv.posterPath,
        backdropPath = tv.backdropPath,
    )

    constructor(search: SeerrSearchResult) : this(
        id = search.id,
        type = SeerrItemType.fromString(search.mediaType),
        title = search.title ?: search.name,
        availability =
            SeerrAvailability.from(search.mediaInfo?.status)
                ?: SeerrAvailability.UNKNOWN,
        releaseDate = search.releaseDate ?: search.firstAirDate,
        posterPath = search.posterPath,
        backdropPath = search.backdropPath,
    )
}

data class DiscoverRating(
    val criticRating: Int?,
    val audienceRating: Float?,
) {
    constructor(rating: MovieMovieIdRatingsGet200Response) : this(
        criticRating = rating.criticsScore,
        audienceRating = rating.audienceScore?.div(10f),
    )
}
