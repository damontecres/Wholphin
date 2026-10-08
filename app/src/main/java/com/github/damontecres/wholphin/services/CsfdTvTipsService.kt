package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gets the ČSFD "TV tipy dňa" from the Jellyfin ČSFD plugin (`/Csfd/TvTips`).
 *
 * The plugin already filters the tips to what is in the user's library, so the result is just item IDs, best rated first.
 */
@Singleton
class CsfdTvTipsService
    @Inject
    constructor(
        private val api: ApiClient,
        @param:AuthOkHttpClient private val okHttpClient: OkHttpClient,
    ) {
        /**
         * Returns the library item IDs of today's tips, or an empty list if the plugin is missing/unreachable
         */
        suspend fun getItemIds(limit: Int): List<UUID> =
            withContext(Dispatchers.IO) {
                val baseUrl = api.baseUrl?.trimEnd('/') ?: return@withContext emptyList()
                try {
                    val request = Request.Builder().url("$baseUrl/Csfd/TvTips?limit=$limit").build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            // 404 = plugin without TV tips support
                            Timber.w("ČSFD TV tips: HTTP %s", response.code)
                            return@withContext emptyList()
                        }
                        parseItemIds(response.body.string())
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "ČSFD TV tips failed")
                    emptyList()
                }
            }

        companion object {
            fun parseItemIds(json: String): List<UUID> =
                (Json.parseToJsonElement(json) as? JsonArray)
                    .orEmpty()
                    .mapNotNull { tip ->
                        // The server may emit PascalCase or camelCase
                        runCatching {
                            val obj = tip.jsonObject
                            UUID.fromString((obj["ItemId"] ?: obj["itemId"])!!.jsonPrimitive.content)
                        }.getOrNull()
                    }
        }
    }
