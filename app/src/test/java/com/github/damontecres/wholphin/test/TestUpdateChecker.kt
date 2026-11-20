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

    lateinit var releaseV108Json: JsonObject
    val assetsV108Json: JsonArray by lazy { releaseV108Json["assets"]!!.jsonArray }

    private fun read(filename: String): JsonObject {
        val resource = javaClass.classLoader?.getResource(filename)
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        return Json.parseToJsonElement(fileContents).jsonObject
    }

    @Before
    fun setup() {
        releaseJson = read("release_develop.json")
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

    @Test
    fun `Test regular release`() {
        releaseV108Json = read("release_v108.json")
        getDownloadUrl(assetsV108Json, false, listOf("arm64-v8a")).let { url ->
            Assert.assertEquals(
                "https://github.com/damontecres/Wholphin/releases/download/v1.0.8/Wholphin-arm64-v8a.apk",
                url,
            )
        }
        getDownloadUrl(assetsV108Json, false, listOf("armeabi-v7a")).let { url ->
            Assert.assertEquals(
                "https://github.com/damontecres/Wholphin/releases/download/v1.0.8/Wholphin-armeabi-v7a.apk",
                url,
            )
        }
        getDownloadUrl(assetsV108Json, false, emptyList()).let { url ->
            Assert.assertEquals(
                "https://github.com/damontecres/Wholphin/releases/download/v1.0.8/Wholphin.apk",
                url,
            )
        }
        getDownloadUrl(assetsV108Json, true, listOf("arm64-v8a")).let { url ->
            Assert.assertNull(url)
        }
    }
}
