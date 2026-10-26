package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.HomePageSettings
import com.github.damontecres.wholphin.data.model.SeerrPluginSettings
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for interacting with the companion plugin
 */
@Singleton
class ServerPluginApi
    @Inject
    constructor(
        @param:AuthOkHttpClient private val okHttpClient: OkHttpClient,
        private val api: ApiClient,
    ) {
        private fun createUrl(path: String): String? =
            api.baseUrl?.let { if (it.endsWith("/")) "${it}wholphin/$path" else "$it/wholphin/$path" }

        private val json get() = HomeSettingsService.jsonParser

        companion object {
            private const val HOME_CONFIG_PATH = "homesettings"
            private const val SEERR_CONFIG_PATH = "seerrsettings"
        }

        /**
         * Check if the plugin is installed on the server
         */
        suspend fun checkInstalled(): Boolean =
            withContext(WholphinDispatchers.IO) {
                val url = createUrl("public") ?: return@withContext false
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .get()
                        .build()
                try {
                    return@withContext okHttpClient
                        .newCall(request)
                        .execute()
                        .use { it.isSuccessful }
                } catch (ex: IOException) {
                    Timber.w("Could not connect to server plugin: %s", ex.message)
                    return@withContext false
                }
            }

        private suspend inline fun <reified T> executeRequest(path: String): T? {
            val url = createUrl(path) ?: return null
            val request = Request(url.toHttpUrl())
            return executeRequest<T>(request)
        }

        @OptIn(ExperimentalSerializationApi::class)
        private suspend inline fun <reified T> executeRequest(request: Request): T? =
            withContext(WholphinDispatchers.IO) {
                okHttpClient.newCall(request).execute().use { res ->
                    if (res.isSuccessful) {
                        json.decodeFromStream<T>(res.body.byteStream())
                    } else if (res.code == 404) {
                        Timber.w("Plugin 404: %s", request.url.encodedPath)
                        null
                    } else {
                        throw ApiClientException(res.code.toString() + " " + res.body.string())
                    }
                }
            }

        /**
         * Get the default home page settings from the plugin
         *
         * @return the home page settings or null if no rows are configured or the server isn't installed
         */
        suspend fun fetchHomePageSettings(): HomePageSettings? =
            executeRequest<HomePageSettings>(HOME_CONFIG_PATH)?.takeIf { it.rows.isNotEmpty() }

        /**
         * Get the [SeerrPluginSettings] as configured by teh server plugin, or null if none are configured
         */
        suspend fun fetchSeerrSettings(): SeerrPluginSettings? = executeRequest(SEERR_CONFIG_PATH)
    }
