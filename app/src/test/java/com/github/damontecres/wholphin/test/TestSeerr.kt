package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.ui.setup.seerr.createSeerrApiUrl
import com.github.damontecres.wholphin.ui.setup.seerr.createUrls
import com.github.damontecres.wholphin.ui.setup.seerr.migrateSeerrUrl
import org.junit.Assert.assertEquals
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
}
