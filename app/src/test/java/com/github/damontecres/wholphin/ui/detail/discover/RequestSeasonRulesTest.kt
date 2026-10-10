package com.github.damontecres.wholphin.ui.detail.discover

import com.github.damontecres.wholphin.api.seerr.model.Season
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestSeasonRulesTest {
    @Test
    fun seerrAvailabilityStatesRemainDistinct() {
        val seasons =
            TvDetails(
                seasons =
                    listOf(
                        Season(seasonNumber = 1, status = SeerrAvailability.UNKNOWN.status),
                        Season(seasonNumber = 2, status = SeerrAvailability.PENDING.status),
                        Season(seasonNumber = 3, status = SeerrAvailability.PROCESSING.status),
                        Season(seasonNumber = 4, status = SeerrAvailability.PARTIALLY_AVAILABLE.status),
                        Season(seasonNumber = 5, status = SeerrAvailability.AVAILABLE.status),
                    ),
            ).toRequestSeasons(currentUserId = null, is4k = false)

        assertEquals(
            listOf(
                SeerrAvailability.UNKNOWN,
                SeerrAvailability.PENDING,
                SeerrAvailability.PROCESSING,
                SeerrAvailability.PARTIALLY_AVAILABLE,
                SeerrAvailability.AVAILABLE,
            ),
            seasons.map { it.availability },
        )
        assertEquals(setOf(1), seasons.requestableSeasonNumbers())
    }

    @Test
    fun onlyGenuinelyMissingEditableSeasonsAdvertiseRequest() {
        assertTrue(season(1, RequestStatus.UNKNOWN, SeerrAvailability.UNKNOWN, true).isRequestable())
        assertTrue(season(2, RequestStatus.DECLINED, SeerrAvailability.UNKNOWN, true).isRequestable())
        assertFalse(season(3, RequestStatus.PENDING, SeerrAvailability.PENDING, true).isRequestable())
        assertFalse(season(4, RequestStatus.APPROVED, SeerrAvailability.PROCESSING, true).isRequestable())
        assertFalse(season(5, RequestStatus.UNKNOWN, SeerrAvailability.PARTIALLY_AVAILABLE, false).isRequestable())
        assertFalse(season(6, RequestStatus.UNKNOWN, SeerrAvailability.AVAILABLE, false).isRequestable())
        assertFalse(season(7, RequestStatus.UNKNOWN, SeerrAvailability.UNKNOWN, false).isRequestable())
    }

    @Test
    fun dialogOffersSelectableSeasonsAndRetainsPendingContext() {
        val seasons =
            listOf(
                season(1, RequestStatus.UNKNOWN, SeerrAvailability.UNKNOWN, true),
                season(2, RequestStatus.PENDING, SeerrAvailability.PENDING, true),
                season(3, RequestStatus.APPROVED, SeerrAvailability.PROCESSING, false),
                season(4, RequestStatus.UNKNOWN, SeerrAvailability.PARTIALLY_AVAILABLE, false),
                season(5, RequestStatus.UNKNOWN, SeerrAvailability.AVAILABLE, false),
            )

        assertEquals(setOf(1), seasons.requestableSeasonNumbers())
        assertEquals(listOf(2, 3), seasons.relevantRequestSeasons(excluding = 1).map { it.season.seasonNumber })
        assertTrue(seasons[1].isRelevantToRequest())
        assertTrue(seasons[2].isRelevantToRequest())
        assertFalse(seasons[3].isRelevantToRequest())
        assertFalse(seasons[4].isRelevantToRequest())
    }

    private fun season(
        number: Int,
        status: RequestStatus,
        availability: SeerrAvailability,
        editable: Boolean,
    ) = RequestSeason(Season(seasonNumber = number), status, availability, editable)
}
