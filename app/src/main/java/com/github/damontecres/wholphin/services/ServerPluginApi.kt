package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.HomePageSettings
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
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

        suspend fun public(): Boolean {
            val url = createUrl("public") ?: return false
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()
            return okHttpClient.newCall(request).execute().isSuccessful
        }

        @OptIn(ExperimentalSerializationApi::class)
        suspend fun fetchHomePageSettings(): HomePageSettings? {
            val url = createUrl(HOME_CONFIG_PATH) ?: return null
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()
            return okHttpClient.newCall(request).execute().use { res ->
                if (res.isSuccessful) {
                    json.decodeFromStream<HomePageSettings>(res.body.byteStream())
                } else if (res.code == 404) {
                    Timber.w("fetchHomePageSettings returned 404")
                    null
                } else {
                    throw ApiClientException(res.code.toString() + " " + res.body.string())
                }
            }
        }
    }
