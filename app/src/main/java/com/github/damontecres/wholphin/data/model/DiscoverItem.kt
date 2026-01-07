@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.api.seerr.model.CreditCast
import com.github.damontecres.wholphin.api.seerr.model.CreditCrew
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.api.seerr.model.MovieMovieIdRatingsGet200Response
import com.github.damontecres.wholphin.api.seerr.model.MovieResult
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.api.seerr.model.TvResult
import com.github.damontecres.wholphin.api.seerr.model.TvTvIdRatingsGet200Response
import com.github.damontecres.wholphin.services.SeerrSearchResult
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.toLocalDate
import com.github.damontecres.wholphin.util.LocalDateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.time.LocalDate
import java.util.UUID

@Serializable
enum class SeerrItemType(
    val baseItemKind: BaseItemKind?,
) {
    @SerialName("movie")
    MOVIE(BaseItemKind.MOVIE),

    @SerialName("tv")
    TV(BaseItemKind.SERIES),

    @SerialName("person")
    PERSON(BaseItemKind.PERSON),

    @SerialName("unknown")
    UNKNOWN(null),
    ;

    companion object {
        fun fromString(
            str: String?,
            fallback: SeerrItemType = UNKNOWN,
        ) = when (str) {
            "movie" -> MOVIE
            "tv" -> TV
            "person" -> PERSON
            else -> fallback
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
    val subtitle: String?,
    val overview: String?,
    val availability: SeerrAvailability,
    @Serializable(LocalDateSerializer::class) val releaseDate: LocalDate?,
    val posterPath: String?,
    val backdropPath: String?,
    val jellyfinItemId: UUID?,
) : CardGridItem {
    override val gridId: String get() = id.toString()
    override val playable: Boolean = false
    override val sortName: String get() = title ?: ""

    val backDropUrl: String? get() = backdropPath?.let { "https://image.tmdb.org/t/p/w1920_and_h800_multi_faces$it" }
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }

    val destination: Destination
        get() {
            val jfType =
                when (type) {
                    SeerrItemType.MOVIE -> BaseItemKind.MOVIE
                    SeerrItemType.TV -> BaseItemKind.SERIES
                    SeerrItemType.PERSON -> BaseItemKind.PERSON
                    SeerrItemType.UNKNOWN -> null
                }
            return if (jellyfinItemId != null && jfType != null) {
                Destination.MediaItem(
                    itemId = jellyfinItemId,
                    type = jfType,
                )
            } else {
                Destination.DiscoveredItem(this)
            }
        }

    constructor(movie: MovieResult) : this(
        id = movie.id,
        type = SeerrItemType.MOVIE,
        title = movie.title,
        subtitle = null,
        overview = movie.overview,
        availability = SeerrAvailability.from(movie.mediaInfo?.status) ?: SeerrAvailability.UNKNOWN,
        releaseDate = toLocalDate(movie.releaseDate),
        posterPath = movie.posterPath,
        backdropPath = movie.backdropPath,
        jellyfinItemId = movie.mediaInfo?.jellyfinMediaId?.toUUIDOrNull(),
    )

    constructor(movie: MovieDetails) : this(
        id = movie.id ?: -1,
        type = SeerrItemType.MOVIE,
        title = movie.title,
        subtitle = null,
        overview = movie.overview,
        availability = SeerrAvailability.from(movie.mediaInfo?.status) ?: SeerrAvailability.UNKNOWN,
        releaseDate = toLocalDate(movie.releaseDate),
        posterPath = movie.posterPath,
        backdropPath = movie.backdropPath,
        jellyfinItemId = movie.mediaInfo?.jellyfinMediaId?.toUUIDOrNull(),
    )

    constructor(tv: TvResult) : this(
        id = tv.id!!,
        type = SeerrItemType.TV,
        title = tv.name,
        subtitle = null,
        overview = tv.overview,
        availability = SeerrAvailability.from(tv.mediaInfo?.status) ?: SeerrAvailability.UNKNOWN,
        releaseDate = toLocalDate(tv.firstAirDate),
        posterPath = tv.posterPath,
        backdropPath = tv.backdropPath,
        jellyfinItemId = tv.mediaInfo?.jellyfinMediaId?.toUUIDOrNull(),
    )

    constructor(tv: TvDetails) : this(
        id = tv.id!!,
        type = SeerrItemType.TV,
        title = tv.name,
        subtitle = null,
        overview = tv.overview,
        availability = SeerrAvailability.from(tv.mediaInfo?.status) ?: SeerrAvailability.UNKNOWN,
        releaseDate = toLocalDate(tv.firstAirDate),
        posterPath = tv.posterPath,
        backdropPath = tv.backdropPath,
        jellyfinItemId = tv.mediaInfo?.jellyfinMediaId?.toUUIDOrNull(),
    )

    constructor(search: SeerrSearchResult) : this(
        id = search.id,
        type = SeerrItemType.fromString(search.mediaType),
        title = search.title ?: search.name,
        subtitle = null,
        overview = search.overview,
        availability =
            SeerrAvailability.from(search.mediaInfo?.status)
                ?: SeerrAvailability.UNKNOWN,
        releaseDate = toLocalDate(search.releaseDate ?: search.firstAirDate),
        posterPath = search.posterPath,
        backdropPath = search.backdropPath,
        jellyfinItemId = search.mediaInfo?.jellyfinMediaId?.toUUIDOrNull(),
    )

    constructor(credit: CreditCast) : this(
        id = credit.id!!,
        type = SeerrItemType.fromString(credit.mediaType, SeerrItemType.PERSON),
        title = credit.name ?: credit.title,
        subtitle = null,
        overview = credit.overview,
        availability =
            SeerrAvailability.from(credit.mediaInfo?.status)
                ?: SeerrAvailability.UNKNOWN,
        releaseDate = toLocalDate(credit.firstAirDate),
        posterPath = credit.posterPath ?: credit.profilePath,
        backdropPath = credit.backdropPath,
        jellyfinItemId = credit.mediaInfo?.jellyfinMediaId?.toUUIDOrNull(),
    )

    constructor(credit: CreditCrew) : this(
        id = credit.id!!,
        type = SeerrItemType.fromString(credit.mediaType, SeerrItemType.PERSON),
        title = credit.name ?: credit.title,
        subtitle = null,
        overview = credit.overview,
        availability =
            SeerrAvailability.from(credit.mediaInfo?.status)
                ?: SeerrAvailability.UNKNOWN,
        releaseDate = toLocalDate(credit.firstAirDate),
        posterPath = credit.posterPath ?: credit.profilePath,
        backdropPath = credit.backdropPath,
        jellyfinItemId = credit.mediaInfo?.jellyfinMediaId?.toUUIDOrNull(),
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
    constructor(rating: TvTvIdRatingsGet200Response) : this(
        criticRating = rating.criticsScore,
        audienceRating = null,
    )
}
