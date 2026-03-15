package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.services.getDownloadUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.readText

class TestUpdateChecker {
    lateinit var releaseJson: JsonObject
    val assetsJson: JsonArray by lazy { releaseJson["assets"]!!.jsonArray }

    @Before
    fun setup() {
        val resource = javaClass.classLoader?.getResource("release_develop.json")
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        releaseJson = Json.parseToJsonElement(fileContents).jsonObject
    }

    @Test
    fun `Release chooses release`() {
        val url = getDownloadUrl(assetsJson, false, listOf())
        Assert.assertEquals("https://github.com/damontecres/Wholphin/releases/download/develop/Wholphin-release.apk", url)
    }

    @Test
    fun `Choose abi`() {
        val url = getDownloadUrl(assetsJson, false, listOf("arm64-v8a"))
        Assert.assertEquals("https://github.com/damontecres/Wholphin/releases/download/develop/Wholphin-release-arm64-v8a.apk", url)
    }

    @Test
    fun `Choose unknown abi`() {
        val url = getDownloadUrl(assetsJson, false, listOf("unknown"))
        Assert.assertEquals("https://github.com/damontecres/Wholphin/releases/download/develop/Wholphin-release.apk", url)
    }

    @Test
    fun `Debug chooses debug`() {
        val url = getDownloadUrl(assetsJson, true, listOf())
        Assert.assertEquals("https://github.com/damontecres/Wholphin/releases/download/develop/Wholphin-debug.apk", url)
    }

    @Test
    fun `Choose debug abi`() {
        val url = getDownloadUrl(assetsJson, true, listOf("arm64-v8a"))
        Assert.assertEquals("https://github.com/damontecres/Wholphin/releases/download/develop/Wholphin-debug-arm64-v8a.apk", url)
    }
}
