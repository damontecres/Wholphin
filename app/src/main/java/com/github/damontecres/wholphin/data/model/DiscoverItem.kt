@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import androidx.compose.runtime.Stable
import com.github.damontecres.wholphin.api.seerr.model.MovieMovieIdRatingsGet200Response
import com.github.damontecres.wholphin.api.seerr.model.TvTvIdRatingsGet200Response
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.LocalDateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.serializer.UUIDSerializer
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

/**
 * An item provided by a discovery service (ie Seerr). It may exist on the JF server as well.
 */
@Stable
@Serializable
data class DiscoverItem(
    val id: Int,
    val type: SeerrItemType,
    val title: String?,
    val subtitle: String?,
    val overview: String?,
    val availability: SeerrAvailability,
    @Serializable(LocalDateSerializer::class) val releaseDate: LocalDate?,
    val posterUrl: String?,
    val backDropUrl: String?,
    val jellyfinItemId: UUID?,
) : CardGridItem {
    override val gridId: String get() = id.toString()
    override val playable: Boolean = false
    override val sortName: String get() = title ?: ""

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
