package com.github.damontecres.wholphin.ui.detail.discover

import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability

fun TvDetails.toRequestSeasons(
    currentUserId: Int?,
    is4k: Boolean,
): List<RequestSeason> {
    val seasonStatus = mutableMapOf<Int, RequestStatus>()
    val seasonAvailability = mutableMapOf<Int, SeerrAvailability>()
    val editable = mutableMapOf<Int, Boolean>()

    seasons?.forEach { season ->
        season.seasonNumber?.let { seasonNumber ->
            seasonStatus[seasonNumber] = RequestStatus.UNKNOWN
            val status = if (is4k) season.status4k else season.status
            seasonAvailability[seasonNumber] =
                SeerrAvailability.from(status) ?: SeerrAvailability.UNKNOWN
        }
    }
    mediaInfo?.seasons?.forEach { season ->
        season.seasonNumber?.let { seasonNumber ->
            val status = if (is4k) season.status4k else season.status
            val availability = SeerrAvailability.from(status) ?: SeerrAvailability.UNKNOWN
            val current = seasonAvailability.getOrDefault(seasonNumber, SeerrAvailability.UNKNOWN)
            if (availability > current) seasonAvailability[seasonNumber] = availability
        }
    }
    mediaInfo
        ?.requests
        ?.filter { it.is4k == is4k }
        ?.forEach { request ->
            request.seasons?.forEach { season ->
                season.seasonNumber?.let { seasonNumber ->
                    val current = seasonStatus[seasonNumber]
                    val new = RequestStatus.from(season.status)
                    if (current == null || new.status > current.status) {
                        seasonStatus[seasonNumber] = new
                    }
                    editable[seasonNumber] =
                        currentUserId == request.requestedBy?.id &&
                            request.status == RequestStatus.PENDING.status
                }
            }
        }

    return seasonStatus
        .mapNotNull { (seasonNumber, status) ->
            seasons?.firstOrNull { it.seasonNumber == seasonNumber }?.let { season ->
                val availability =
                    when (status) {
                        RequestStatus.PENDING -> SeerrAvailability.PENDING
                        RequestStatus.APPROVED -> SeerrAvailability.PROCESSING
                        RequestStatus.DECLINED, RequestStatus.FAILURE -> SeerrAvailability.UNKNOWN
                        RequestStatus.UNKNOWN, RequestStatus.COMPLETED ->
                            seasonAvailability.getOrDefault(seasonNumber, SeerrAvailability.UNKNOWN)
                    }
                val defaultEditable =
                    availability != SeerrAvailability.AVAILABLE &&
                        availability != SeerrAvailability.PARTIALLY_AVAILABLE &&
                        availability != SeerrAvailability.PROCESSING &&
                        availability != SeerrAvailability.BLOCKLISTED
                RequestSeason(
                    season = season,
                    status = status,
                    availability = availability,
                    editable = editable.getOrDefault(seasonNumber, defaultEditable),
                )
            }
        }.sortedBy { it.season.seasonNumber }
}
