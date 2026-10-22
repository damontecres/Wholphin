package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.api.seerr.model.WatchProviderDetails
import com.github.damontecres.wholphin.api.seerr.model.WatchProviders
import com.github.damontecres.wholphin.services.mapWatchProviderDisplays
import com.github.damontecres.wholphin.services.selectWatchProviderRegion
import com.github.damontecres.wholphin.ui.setup.seerr.createSeerrApiUrl
import com.github.damontecres.wholphin.ui.setup.seerr.createUrls
import com.github.damontecres.wholphin.ui.setup.seerr.migrateSeerrUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

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
    fun `Select watch provider region prefers the requested locale`() {
        val selected =
            listOf(
                WatchProviders(iso31661 = "US"),
                WatchProviders(iso31661 = "CA"),
            ).selectWatchProviderRegion(Locale.CANADA)

        assertEquals("CA", selected?.iso31661)
    }

    @Test
    fun `Select watch provider region falls back to US`() {
        val selected =
            listOf(
                WatchProviders(iso31661 = "US"),
                WatchProviders(iso31661 = "CA"),
            ).selectWatchProviderRegion(Locale.UK)

        assertEquals("US", selected?.iso31661)
    }

    @Test
    fun `Select watch provider region falls back to first available region`() {
        val selected =
            listOf(
                WatchProviders(iso31661 = "CA"),
                WatchProviders(iso31661 = "GB"),
            ).selectWatchProviderRegion(Locale.JAPAN)

        assertEquals("CA", selected?.iso31661)
    }

    @Test
    fun `Select watch provider region returns null when no regions exist`() {
        assertNull(emptyList<WatchProviders>().selectWatchProviderRegion(Locale.US))
        assertNull(null.selectWatchProviderRegion(Locale.US))
    }

    @Test
    fun `Watch provider details expose streaming provider fields`() {
        val provider =
            WatchProviderDetails(
                id = 8,
                name = "Netflix",
                logoPath = "/netflix.png",
            )
        val region =
            WatchProviders(
                iso31661 = "US",
                flatrate = listOf(provider),
            )

        assertEquals("Netflix", region.flatrate?.single()?.name)
        assertEquals("/netflix.png", region.flatrate?.single()?.logoPath)
    }

    @Test
    fun `Watch provider displays deduplicate shared logo URLs`() {
        val displays =
            mapWatchProviderDisplays(
                providers =
                    listOf(
                        WatchProviders(
                            iso31661 = "US",
                            flatrate =
                                listOf(
                                    WatchProviderDetails(
                                        id = 8,
                                        name = "Netflix",
                                        logoPath = "/shared.png",
                                    ),
                                    WatchProviderDetails(
                                        id = 9,
                                        name = "Other",
                                        logoPath = "/shared.png",
                                    ),
                                ),
                        ),
                    ),
                imageBaseUrl = "https://image.tmdb.org",
            )

        assertEquals(1, displays.size)
        assertEquals("https://image.tmdb.org/t/p/w92/shared.png", displays.single().logoUrl)
    }
}
