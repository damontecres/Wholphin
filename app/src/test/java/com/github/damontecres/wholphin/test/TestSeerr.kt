package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.api.seerr.AuthApi
import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.UsersApi
import com.github.damontecres.wholphin.api.seerr.model.AuthJellyfinPostRequest
import com.github.damontecres.wholphin.api.seerr.model.AuthJellyfinQuickconnectAuthenticatePostRequest
import com.github.damontecres.wholphin.api.seerr.model.AuthJellyfinQuickconnectCheckGet200Response
import com.github.damontecres.wholphin.api.seerr.model.AuthJellyfinQuickconnectInitiatePost200Response
import com.github.damontecres.wholphin.api.seerr.model.DownloadingItem
import com.github.damontecres.wholphin.api.seerr.model.DownloadingItemEpisode
import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.api.seerr.model.Season
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.api.seerr.model.User
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrDownloadItem
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.overallProgressPercent
import com.github.damontecres.wholphin.services.createSeerrDiscoveryUrls
import com.github.damontecres.wholphin.services.extractDownloads
import com.github.damontecres.wholphin.services.extractIpv4SubnetPrefix
import com.github.damontecres.wholphin.services.normalizeTimeLeft
import com.github.damontecres.wholphin.services.seerrLogin
import com.github.damontecres.wholphin.services.seerrQuickConnectLogin
import com.github.damontecres.wholphin.ui.discover.RequestGridItem
import com.github.damontecres.wholphin.ui.discover.ScheduledRelease
import com.github.damontecres.wholphin.ui.discover.buildTimelineReleaseEvents
import com.github.damontecres.wholphin.ui.discover.extractUnavailableRequestedSeasons
import com.github.damontecres.wholphin.ui.setup.seerr.createSeerrApiUrl
import com.github.damontecres.wholphin.ui.setup.seerr.createUrls
import com.github.damontecres.wholphin.ui.setup.seerr.migrateSeerrUrl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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
        assertFalse(item1.isCompleted)

        val item2 = SeerrDownloadItem(title = "Ep2", size = 1_000L, sizeLeft = 750L)
        assertEquals(50.0, listOf(item1, item2).overallProgressPercent()!!, 0.01)

        val completedByBytes =
            SeerrDownloadItem(
                title = "VisionQuest S01E08",
                size = 1_653_562_400L,
                sizeLeft = 0L,
                timeLeft = "00:00:00".normalizeTimeLeft(),
            )
        assertTrue(completedByBytes.isCompleted)
        assertNull(completedByBytes.timeLeft)
        assertNull(listOf(completedByBytes).overallProgressPercent())
        assertEquals(25.0, listOf(completedByBytes, item2).overallProgressPercent()!!, 0.01)

        val completedByStatus =
            SeerrDownloadItem(
                title = "Seeding Episode",
                status = "completed",
                size = 1_000L,
                sizeLeft = 100L,
            )
        assertTrue(completedByStatus.isCompleted)
    }

    @Test
    fun `Test extractDownloads filters completed and already available seasons`() {
        val mediaInfo =
            MediaInfo(
                status = SeerrAvailability.PARTIALLY_AVAILABLE.status,
                seasons =
                    listOf(
                        Season(seasonNumber = 1, status = SeerrAvailability.AVAILABLE.status),
                        Season(seasonNumber = 2, status = SeerrAvailability.PROCESSING.status),
                    ),
                downloadStatus =
                    listOf(
                        DownloadingItem(
                            title = "VisionQuest S01E08 1080p",
                            propertySize = 1_500_000.toBigDecimal(),
                            sizeLeft = 0.toBigDecimal(),
                            timeLeft = "00:00:00",
                            episode = DownloadingItemEpisode(seasonNumber = 2, episodeNumber = 8),
                        ),
                        DownloadingItem(
                            title = "Season 1 Seeding Ep",
                            propertySize = 1_000_000.toBigDecimal(),
                            sizeLeft = 500_000.toBigDecimal(),
                            timeLeft = "00:05:00",
                            episode = DownloadingItemEpisode(seasonNumber = 1, episodeNumber = 1),
                        ),
                        DownloadingItem(
                            title = "Season 2 Active Ep",
                            propertySize = 1_000_000.toBigDecimal(),
                            sizeLeft = 620_000.toBigDecimal(),
                            timeLeft = "00:12:30",
                            episode = DownloadingItemEpisode(seasonNumber = 2, episodeNumber = 3),
                        ),
                    ),
            )
        val downloads = mediaInfo.extractDownloads()
        assertEquals(1, downloads.size)
        assertEquals("Season 2 Active Ep", downloads.first().title)
        assertEquals("00:12:30", downloads.first().timeLeft)

        val fullyAvailableMedia =
            mediaInfo.copy(status = SeerrAvailability.AVAILABLE.status)
        assertTrue(fullyAvailableMedia.extractDownloads().isEmpty())
    }

    @Test
    fun `Test extractUnavailableRequestedSeasons uses mediaInfo season availability`() {
        val tv =
            TvDetails(
                id = 500,
                mediaInfo =
                    MediaInfo(
                        status = SeerrAvailability.PARTIALLY_AVAILABLE.status,
                        seasons =
                            listOf(
                                Season(seasonNumber = 1, status = SeerrAvailability.AVAILABLE.status),
                                Season(seasonNumber = 2, status = SeerrAvailability.PROCESSING.status),
                            ),
                    ),
            )
        val reqSeason1Only =
            MediaRequest(
                id = 10,
                status = RequestStatus.APPROVED.status,
                seasons = listOf(Season(seasonNumber = 1, status = RequestStatus.APPROVED.status)),
            )
        assertTrue(extractUnavailableRequestedSeasons(reqSeason1Only, tv).isEmpty())

        val reqBothSeasons =
            MediaRequest(
                id = 11,
                status = RequestStatus.APPROVED.status,
                seasons =
                    listOf(
                        Season(seasonNumber = 1, status = RequestStatus.APPROVED.status),
                        Season(seasonNumber = 2, status = RequestStatus.APPROVED.status),
                    ),
            )
        assertEquals(listOf(2), extractUnavailableRequestedSeasons(reqBothSeasons, tv))
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

    @Test
    fun `Test createSeerrDiscoveryUrls from Jellyfin server and saved servers`() {
        val urls =
            createSeerrDiscoveryUrls(
                jellyfinServerUrl = "http://192.168.1.50:8096",
                savedSeerrUrls = listOf("http://192.168.1.200:5055"),
            ).map { it.toString() }

        val expected =
            listOf(
                "http://192.168.1.200:5055/",
                "http://192.168.1.50:5055/",
                "https://192.168.1.50:5055/",
            )
        assertEquals(expected, urls)
    }

    @Test
    fun `Test createSeerrDiscoveryUrls deduplicates saved and Jellyfin host`() {
        val urls =
            createSeerrDiscoveryUrls(
                jellyfinServerUrl = "https://media.local:8920",
                savedSeerrUrls = listOf("https://media.local:5055/api/v1"),
            ).map { it.toString() }

        val expected =
            listOf(
                "https://media.local:5055/",
                "http://media.local:5055/",
            )
        assertEquals(expected, urls)
    }

    @Test
    fun `Test extractIpv4SubnetPrefix`() {
        assertEquals("192.168.1.", extractIpv4SubnetPrefix("192.168.1.50"))
        assertEquals("10.0.10.", extractIpv4SubnetPrefix("10.0.10.2"))
        assertEquals("172.16.5.", extractIpv4SubnetPrefix("172.16.5.100"))
        assertEquals("172.31.255.", extractIpv4SubnetPrefix("172.31.255.1"))
        assertNull(extractIpv4SubnetPrefix("172.15.0.1"))
        assertNull(extractIpv4SubnetPrefix("172.32.0.1"))
        assertNull(extractIpv4SubnetPrefix("8.8.8.8"))
        assertNull(extractIpv4SubnetPrefix("127.0.0.1"))
        assertNull(extractIpv4SubnetPrefix("jellyfin.example.com"))
    }

    @Test
    fun `Test seerrQuickConnectLogin flow`() =
        runTest {
            val mockClient = mockk<SeerrApiClient>()
            val mockAuthApi = mockk<AuthApi>()
            val mockUsersApi = mockk<UsersApi>()
            every { mockClient.authApi } returns mockAuthApi
            every { mockClient.usersApi } returns mockUsersApi

            val expectedUser = User(id = 42, username = "jellyuser")
            coEvery { mockAuthApi.authJellyfinQuickconnectInitiatePost() } returns
                AuthJellyfinQuickconnectInitiatePost200Response(
                    code = "123456",
                    secret = "sec-xyz",
                )
            coEvery { mockAuthApi.authJellyfinQuickconnectCheckGet("sec-xyz") } returns
                AuthJellyfinQuickconnectCheckGet200Response(authenticated = true)
            coEvery {
                mockAuthApi.authJellyfinQuickconnectAuthenticatePost(
                    AuthJellyfinQuickconnectAuthenticatePostRequest(secret = "sec-xyz"),
                )
            } returns expectedUser
            coEvery { mockUsersApi.authMeGet() } returns expectedUser

            var authorizedCode: String? = null
            val result =
                seerrQuickConnectLogin(mockClient) { code ->
                    authorizedCode = code
                    true
                }

            assertEquals("123456", authorizedCode)
            assertEquals(expectedUser, result)
            coVerify(exactly = 1) {
                mockAuthApi.authJellyfinQuickconnectAuthenticatePost(
                    AuthJellyfinQuickconnectAuthenticatePostRequest(secret = "sec-xyz"),
                )
            }
        }

    @Test
    fun `Test seerrLogin with Jellyfin falls back from Quick Connect to password`() =
        runTest {
            val mockClient = mockk<SeerrApiClient>()
            val mockAuthApi = mockk<AuthApi>()
            val mockUsersApi = mockk<UsersApi>()
            every { mockClient.authApi } returns mockAuthApi
            every { mockClient.usersApi } returns mockUsersApi

            val expectedUser = User(id = 7, username = "jellyuser")
            coEvery { mockAuthApi.authJellyfinQuickconnectInitiatePost() } throws RuntimeException("404 Not Found")
            coEvery {
                mockAuthApi.authJellyfinPost(
                    AuthJellyfinPostRequest(username = "jellyuser", password = ""),
                )
            } returns expectedUser
            coEvery { mockUsersApi.authMeGet() } returns expectedUser

            val result =
                seerrLogin(
                    client = mockClient,
                    authMethod = SeerrAuthMethod.JELLYFIN,
                    username = "jellyuser",
                    password = null,
                    quickConnectAuthorizer = { true },
                )

            assertEquals(expectedUser, result)
            coVerify(exactly = 1) {
                mockAuthApi.authJellyfinPost(
                    AuthJellyfinPostRequest(username = "jellyuser", password = ""),
                )
            }
        }
}
