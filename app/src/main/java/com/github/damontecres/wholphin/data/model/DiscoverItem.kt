package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.api.seerr.model.MovieMovieIdRatingsGet200Response
import com.github.damontecres.wholphin.api.seerr.model.MovieResult
import com.github.damontecres.wholphin.api.seerr.model.TvResult
import kotlinx.serialization.Serializable

@Serializable
enum class SeerrItemType {
    MOVIE,
    TV,
    PERSON,
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
