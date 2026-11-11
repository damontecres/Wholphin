package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrDownloadItem
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.overallProgressPercent
import com.github.damontecres.wholphin.ui.discover.RequestGridItem
import com.github.damontecres.wholphin.ui.discover.ScheduledRelease
import com.github.damontecres.wholphin.ui.discover.buildTimelineReleaseEvents
import java.time.LocalDate
import com.github.damontecres.wholphin.ui.setup.seerr.createSeerrApiUrl
import com.github.damontecres.wholphin.ui.setup.seerr.createUrls
import com.github.damontecres.wholphin.ui.setup.seerr.migrateSeerrUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestSeerr {
    @Test
    fun testCreateUrls() {
        val urls =
            createUrls("jellyseerr.com")
                .map { it.toString() }

        val expected =
            listOf(
                "http://jellyseerr.com/",
                "https://jellyseerr.com/",
                "http://jellyseerr.com:5055/",
                "https://jellyseerr.com:5055/",
            )
        assertEquals(expected, urls)
    }

    @Test
    fun testCreateUrls2() {
        val urls =
            createUrls("https://jellyseerr.com")
                .map { it.toString() }

        val expected =
            listOf(
                "https://jellyseerr.com/",
                "https://jellyseerr.com:5055/",
            )
        assertEquals(expected, urls)
    }

    @Test
    fun testCreateUrls3() {
        val urls =
            createUrls("http://jellyseerr.com")
                .map { it.toString() }

        val expected =
            listOf(
                "http://jellyseerr.com/",
                "http://jellyseerr.com:5055/",
            )
        assertEquals(expected, urls)
    }

    @Test
    fun testCreateUrls4() {
        val urls =
            createUrls("jellyseerr.com:5055")
                .map { it.toString() }

        val expected =
            listOf(
                "http://jellyseerr.com:5055/",
                "https://jellyseerr.com:5055/",
            )
        assertEquals(expected, urls)
    }

    @Test
    fun testCreateUrls5() {
        val urls =
            createUrls("10.0.0.2:443")
                .map { it.toString() }

        val expected =
            listOf(
                "http://10.0.0.2:443/",
                "https://10.0.0.2/",
            )
        assertEquals(expected, urls)
    }

    @Test
    fun testCreateUrls6() {
        val urls =
            createUrls("10.0.0.2:8080")
                .map { it.toString() }

        val expected =
            listOf(
                "http://10.0.0.2:8080/",
                "https://10.0.0.2:8080/",
            )
        assertEquals(expected, urls)
    }

    @Test
    fun testCreateUrls7() {
        val urls =
            createUrls("http://10.0.0.2:80")
                .map { it.toString() }

        val expected =
            listOf(
                "http://10.0.0.2/",
                "http://10.0.0.2:5055/",
            )
        assertEquals(expected, urls)
    }

    @Test
    fun testCreateUrls8() {
        val urls =
            createUrls("https://10.0.0.2:443")
                .map { it.toString() }

        val expected =
            listOf(
                "https://10.0.0.2/",
                "https://10.0.0.2:5055/",
            )
        assertEquals(expected, urls)
    }

    @Test
    fun `Test createUrls for path`() {
        val urls =
            createUrls("https://jellyseerr.com/seerr/")
                .map { it.toString() }

        val expected =
            listOf(
                "https://jellyseerr.com/seerr/",
                "https://jellyseerr.com:5055/seerr/",
            )
        assertEquals(expected, urls)
    }

    @Test
    fun `Test build api url`() {
        var url = "https://jellyseerr.com/"
        assertEquals("https://jellyseerr.com/api/v1", createSeerrApiUrl(url))

        url = "https://jellyseerr.com/path"
        assertEquals("https://jellyseerr.com/path/api/v1", createSeerrApiUrl(url))

        url = "http://jellyseerr.com:5055/"
        assertEquals("http://jellyseerr.com:5055/api/v1", createSeerrApiUrl(url))

        url = "http://jellyseerr.com:7878/path/"
        assertEquals("http://jellyseerr.com:7878/path/api/v1", createSeerrApiUrl(url))

        url = "http://jellyseerr.com/api/v1"
        assertEquals("http://jellyseerr.com/api/v1", createSeerrApiUrl(url))

        url = "http://jellyseerr.com/api/v1/"
        assertEquals("http://jellyseerr.com/api/v1/", createSeerrApiUrl(url))

        url = "http://jellyseerr.com/path/api/v1"
        assertEquals("http://jellyseerr.com/path/api/v1", createSeerrApiUrl(url))
    }

    @Test
    fun `Test migration`() {
        assertEquals("http://10.0.0.2/", migrateSeerrUrl("http://10.0.0.2/api/v1"))
        assertEquals("https://10.0.0.2/", migrateSeerrUrl("https://10.0.0.2/api/v1"))
        assertEquals("http://10.0.0.2:5055/", migrateSeerrUrl("http://10.0.0.2:5055/api/v1"))
        assertEquals("http://10.0.0.2/", migrateSeerrUrl("http://10.0.0.2/api/v1/"))
        assertEquals("http://10.0.0.2/path/", migrateSeerrUrl("http://10.0.0.2/path/api/v1"))
        assertEquals("http://10.0.0.2/api/v1/", migrateSeerrUrl("http://10.0.0.2/api/v1/api/v1"))
        assertEquals("http://10.0.0.2/", migrateSeerrUrl("http://10.0.0.2/"))
        assertEquals("http://10.0.0.2/", migrateSeerrUrl("http://10.0.0.2"))
    }

    @Test
    fun `Test download progress calculation`() {
        assertNull(emptyList<SeerrDownloadItem>().overallProgressPercent())

        val item1 = SeerrDownloadItem(title = "Movie", size = 1_000L, sizeLeft = 250L)
        assertEquals(75.0, item1.progressPercent!!, 0.01)

        val item2 = SeerrDownloadItem(title = "Ep2", size = 1_000L, sizeLeft = 750L)
        assertEquals(50.0, listOf(item1, item2).overallProgressPercent()!!, 0.01)
    }

    @Test
    fun `Test active request detection`() {
        fun makeDiscoverItem(
            availability: SeerrAvailability,
            downloads: List<SeerrDownloadItem> = emptyList(),
        ) = DiscoverItem(
            id = 100,
            type = SeerrItemType.MOVIE,
            title = "Test Movie",
            subtitle = null,
            overview = null,
            availability = availability,
            releaseDate = null,
            posterUrl = null,
            backDropUrl = null,
            logoUrl = null,
            jellyfinItemId = null,
            downloadProgress = downloads.overallProgressPercent(),
            downloads = downloads,
        )

        val pendingReq =
            RequestGridItem(
                request = MediaRequest(id = 1, status = RequestStatus.PENDING.status),
                item = makeDiscoverItem(SeerrAvailability.PENDING),
            )
        assertTrue(pendingReq.isActiveRequest)

        val processingReq =
            RequestGridItem(
                request = MediaRequest(id = 2, status = RequestStatus.APPROVED.status),
                item = makeDiscoverItem(SeerrAvailability.PROCESSING),
            )
        assertTrue(processingReq.isActiveRequest)

        val downloadingReq =
            RequestGridItem(
                request = MediaRequest(id = 3, status = RequestStatus.APPROVED.status),
                item =
                    makeDiscoverItem(
                        SeerrAvailability.PARTIALLY_AVAILABLE,
                        listOf(SeerrDownloadItem(size = 2000L, sizeLeft = 1000L)),
                    ),
            )
        assertTrue(downloadingReq.isActiveRequest)

        val availableReq =
            RequestGridItem(
                request = MediaRequest(id = 4, status = RequestStatus.COMPLETED.status),
                item = makeDiscoverItem(SeerrAvailability.AVAILABLE),
            )
        assertFalse(availableReq.isActiveRequest)

        val declinedReq =
            RequestGridItem(
                request = MediaRequest(id = 5, status = RequestStatus.DECLINED.status),
                item = makeDiscoverItem(SeerrAvailability.UNKNOWN),
            )
        assertFalse(declinedReq.isActiveRequest)

        val futureDate = LocalDate.now().plusDays(14)
        val futureMovieReq =
            RequestGridItem(
                request = MediaRequest(id = 6, status = RequestStatus.APPROVED.status),
                item = makeDiscoverItem(SeerrAvailability.PROCESSING).copy(releaseDate = futureDate),
                scheduledReleases = listOf(ScheduledRelease(date = futureDate)),
                hasReleasedMissingContent = false,
            )
        assertTrue(futureMovieReq.isFutureRelease)

        val tvFutureReq =
            RequestGridItem(
                request = MediaRequest(id = 7, status = RequestStatus.APPROVED.status),
                item =
                    makeDiscoverItem(SeerrAvailability.PROCESSING).copy(
                        id = 200,
                        type = SeerrItemType.TV,
                        title = "Upcoming Show",
                    ),
                scheduledReleases =
                    listOf(
                        ScheduledRelease(
                            date = LocalDate.now().plusDays(7),
                            seasonNumber = 2,
                            episodeNumber = 1,
                            episodeTitle = "Premiere",
                        ),
                        ScheduledRelease(
                            date = LocalDate.now().plusDays(21),
                            seasonNumber = 2,
                            episodeNumber = 2,
                            episodeTitle = "Episode Two",
                        ),
                    ),
                hasReleasedMissingContent = false,
            )
        val timeline = buildTimelineReleaseEvents(listOf(futureMovieReq, tvFutureReq))
        assertEquals(3, timeline.size)
        assertEquals(LocalDate.now().plusDays(7), timeline[0].release.date)
        assertEquals(futureDate, timeline[1].release.date)
        assertEquals(LocalDate.now().plusDays(21), timeline[2].release.date)
    }
}
