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

/**
 * The type of a Seerr/Discover object with mapping to the Jellyfin [BaseItemKind]
 */
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

/**
 * How available is a particular discovered item within the Jellyfin server
 */
@Serializable
enum class SeerrAvailability(
    val status: Int,
) {
    // https://github.com/seerr-team/seerr/blob/develop/server/constants/media.ts#L14
    UNKNOWN(1),
    PENDING(2),
    PROCESSING(3),
    PARTIALLY_AVAILABLE(4),
    AVAILABLE(5),
    BLOCKLISTED(6),
    DELETED(7),
    ;

    companion object {
        fun from(status: Int?) = entries.firstOrNull { it.status == status }
    }
}

@Serializable
enum class RequestStatus(
    val status: Int,
) {
    UNKNOWN(0),
    PENDING(1),
    APPROVED(2),
    DECLINED(3),
    FAILURE(4),
    COMPLETED(5),
    ;

    companion object {
        fun from(status: Int?) = RequestStatus.entries.firstOrNull { it.status == status } ?: UNKNOWN
    }
}

private val COMPLETED_DOWNLOAD_STATUSES =
    setOf(
        "completed",
        "seeding",
        "failed",
        "error",
    )

/**
 * Represents an actively downloading item (movie or episode) from Seerr's download tracker
 */
@Stable
@Serializable
data class SeerrDownloadItem(
    val title: String? = null,
    val status: String? = null,
    val size: Long = 0L,
    val sizeLeft: Long = 0L,
    val timeLeft: String? = null,
    val estimatedCompletionTime: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val is4k: Boolean = false,
) {
    val isCompleted: Boolean
        get() =
            (size > 0L && sizeLeft <= 0L) ||
                status?.trim()?.lowercase() in COMPLETED_DOWNLOAD_STATUSES

    val downloadedBytes: Long
        get() = (size - sizeLeft).coerceIn(0L, size.coerceAtLeast(0L))

    val progressPercent: Double?
        get() =
            if (size > 0L) {
                ((downloadedBytes.toDouble() / size.toDouble()) * 100.0).coerceIn(0.0, 100.0)
            } else {
                null
            }
}

fun List<SeerrDownloadItem>.overallProgressPercent(): Double? {
    val active = filterNot { it.isCompleted }
    if (active.isEmpty()) return null
    val totalSize = active.sumOf { it.size.coerceAtLeast(0L) }
    if (totalSize <= 0L) return 0.0
    val totalLeft = active.sumOf { it.sizeLeft.coerceIn(0L, it.size.coerceAtLeast(0L)) }
    if (totalLeft <= 0L) return null
    return (((totalSize - totalLeft).toDouble() / totalSize.toDouble()) * 100.0).coerceIn(0.0, 99.9)
}

/**
 * An item provided by a discovery service (ie Seerr). It may exist on the JF server as well, see [availability].
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
    val logoUrl: String?,
    val jellyfinItemId: UUID?,
    val downloadProgress: Double? = null,
    val downloads: List<SeerrDownloadItem> = emptyList(),
) : CardGridItem {
    override val gridId: String get() = id.toString()
    override val playable: Boolean = false
    override val sortName: String get() = title ?: ""

    val isActivelyDownloading: Boolean
        get() =
            downloads.any { !it.isCompleted } ||
                (downloadProgress != null && downloadProgress >= 0.0 && downloadProgress < 100.0)

    val destination: Destination
        get() {
            val jfType = type.baseItemKind
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

/**
 * A rating for a discovered item which is usually fetched separately from the item
 */
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
