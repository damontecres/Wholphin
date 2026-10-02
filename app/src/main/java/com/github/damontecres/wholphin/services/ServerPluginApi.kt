package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.HomePageSettings
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.OkHttpClient
import okhttp3.Request
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

        private val json =
            Json {
                ignoreUnknownKeys = false
            }

        companion object {
            private const val HOME_CONFIG_PATH = "homesettings"
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
                return@withContext okHttpClient.newCall(request).execute().isSuccessful
            }

        /**
         * Get the default home page settings from the plugin
         *
         * @return the home page settings or null if no rows are configured or the server isn't installed
         */
        @OptIn(ExperimentalSerializationApi::class)
        suspend fun fetchHomePageSettings(): HomePageSettings? =
            withContext(WholphinDispatchers.IO) {
                val url = createUrl(HOME_CONFIG_PATH) ?: return@withContext null
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .get()
                        .build()
                return@withContext okHttpClient.newCall(request).execute().use { res ->
                    if (res.isSuccessful) {
                        json
                            .decodeFromStream<HomePageSettings>(res.body.byteStream())
                            .takeIf { it.rows.isNotEmpty() }
                    } else if (res.code == 404) {
                        Timber.w("fetchHomePageSettings returned 404")
                        null
                    } else {
                        throw ApiClientException(res.code.toString() + " " + res.body.string())
                    }
                }
            }
    }
