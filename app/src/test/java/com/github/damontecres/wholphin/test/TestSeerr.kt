package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.ui.setup.seerr.createUrls
import org.junit.Assert
import org.junit.Test

class TestSeerr {
    @Test
    fun testCreateUrls() {
        val urls =
            createUrls("jellyseerr.com")
                .map { it.toString() }

        val expected =
            listOf(
                "http://jellyseerr.com/api/v1",
                "https://jellyseerr.com/api/v1",
                "http://jellyseerr.com:5055/api/v1",
                "https://jellyseerr.com:5055/api/v1",
            )
        Assert.assertEquals(expected, urls)
    }

    @Test
    fun testCreateUrls2() {
        val urls =
            createUrls("https://jellyseerr.com")
                .map { it.toString() }

        val expected =
            listOf(
                "https://jellyseerr.com/api/v1",
                "https://jellyseerr.com:5055/api/v1",
            )
        Assert.assertEquals(expected, urls)
    }

    @Test
    fun testCreateUrls3() {
        val urls =
            createUrls("http://jellyseerr.com")
                .map { it.toString() }

        val expected =
            listOf(
                "http://jellyseerr.com/api/v1",
                "http://jellyseerr.com:5055/api/v1",
            )
        Assert.assertEquals(expected, urls)
    }

    @Test
    fun testCreateUrls4() {
        val urls =
            createUrls("jellyseerr.com:5055")
                .map { it.toString() }

        val expected =
            listOf(
                "http://jellyseerr.com:5055/api/v1",
                "https://jellyseerr.com:5055/api/v1",
            )
        Assert.assertEquals(expected, urls)
    }

    @Test
    fun testCreateUrls5() {
        val urls =
            createUrls("10.0.0.2:443")
                .map { it.toString() }

        val expected =
            listOf(
                "http://10.0.0.2:443/api/v1",
                "https://10.0.0.2/api/v1",
            )
        Assert.assertEquals(expected, urls)
    }

    @Test
    fun testCreateUrls6() {
        val urls =
            createUrls("10.0.0.2:8080")
                .map { it.toString() }

        val expected =
            listOf(
                "http://10.0.0.2:8080/api/v1",
                "https://10.0.0.2:8080/api/v1",
            )
        Assert.assertEquals(expected, urls)
    }
}
