package com.github.damontecres.wholphin.ui.detail.series

import com.github.damontecres.wholphin.api.seerr.model.Season
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.ui.detail.discover.RequestSeason
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MissingSeasonProjectionTest {
    @Test
    fun sameNumberCoalescesAndSeerrOnlySeasonsKeepTheirIdentity() {
        val rows = mergedSeasonRowIdentities(listOf(2, 0, 1), listOf(3, 2, 0))

        assertEquals(listOf(0, 1, 2, 3), rows.map { it.seasonNumber })
        assertEquals(4, rows.size)
        assertTrue(rows[0].hasJellyfinSeason && rows[0].hasSeerrSeason)
        assertTrue(rows[2].hasJellyfinSeason && rows[2].hasSeerrSeason)
        assertFalse(rows[3].hasJellyfinSeason)
        assertTrue(rows[3].hasSeerrSeason)
    }

    @Test
    fun inactiveSeerrProjectionContainsOnlyCurrentJellyfinSeasons() {
        val initial = mergedSeasonRowIdentities(listOf(0, 1), listOf(1, 2))
        val inactive = mergedSeasonRowIdentities(listOf(0, 1), emptyList())
        val refreshed = mergedSeasonRowIdentities(listOf(0, 3), listOf(2, 3))

        assertEquals(listOf(0, 1, 2), initial.map { it.seasonNumber })
        assertEquals(listOf(0, 1), inactive.map { it.seasonNumber })
        assertTrue(inactive.none { it.hasSeerrSeason })
        assertEquals(listOf(0, 2, 3), refreshed.map { it.seasonNumber })
        assertFalse(refreshed.any { it.seasonNumber == 1 })
        assertTrue(refreshed.single { it.seasonNumber == 3 }.hasJellyfinSeason)
        assertTrue(refreshed.single { it.seasonNumber == 3 }.hasSeerrSeason)
    }

    @Test
    fun inactiveSeerrDoesNotFetchTvDetails() = runBlocking {
        var calls = 0
        assertEquals(null, loadSeerrWhenActive(false) { calls++; "details" })
        assertEquals(0, calls)
        assertEquals("details", loadSeerrWhenActive(true) { calls++; "details" })
        assertEquals(1, calls)
    }

    @Test
    fun inactiveSeerrClearsPriorRequestStateWithoutDroppingJellyfinSeasons() {
        val requestable = RequestSeason(Season(seasonNumber = 2), RequestStatus.UNKNOWN, SeerrAvailability.UNKNOWN, true)
        val localSeason = BaseItem(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.SEASON, indexNumber = 1))
        val withSeerr =
            SeriesState(
                detailsSeasons = listOf(SeriesDetailsSeason(2, null, requestable, null)),
                seerrTvDetails = TvDetails(id = 42),
                requestSeasons = listOf(requestable),
                requestSeasons4k = listOf(requestable),
                profileLoading = LoadingState.Success,
            )

        val inactive = withSeerr.withJellyfinOnlySeasons(listOf(localSeason))

        assertEquals(listOf(1), inactive.detailsSeasons.map { it.seasonNumber })
        assertEquals(localSeason, inactive.detailsSeasons.single().jellyfinItem)
        assertFalse(inactive.detailsSeasons.single().canRequest())
        assertEquals(null, inactive.seerrTvDetails)
        assertTrue(inactive.requestSeasons.isEmpty())
        assertTrue(inactive.requestSeasons4k.isEmpty())
        assertEquals(LoadingState.Pending, inactive.profileLoading)
    }

    @Test
    fun requestActionIsOnlyForRequestableSeerrOnlySeason() {
        val requestable = RequestSeason(Season(seasonNumber = 2), RequestStatus.UNKNOWN, SeerrAvailability.UNKNOWN, true)
        val localSeason = BaseItem(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.SEASON, indexNumber = 2))

        assertTrue(SeriesDetailsSeason(2, null, requestable, null).canRequest())
        assertFalse(SeriesDetailsSeason(2, localSeason, requestable, null).canRequest())
        assertFalse(SeriesDetailsSeason(2, null, null, null).canRequest())
    }
}
