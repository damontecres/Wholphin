package com.github.damontecres.wholphin.services

import android.util.LruCache
import com.github.damontecres.wholphin.data.model.ExternalRating
import com.github.damontecres.wholphin.data.model.ExternalRatingIcon
import com.github.damontecres.wholphin.preferences.DisplayToggle
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Fetches ratings from the third party [MDBList Ratings](https://github.com/Druidblack/Jellyfin.Plugin.MDBList_Ratings)
 * server plugin, if it is installed
 *
 * The plugin caches all ratings from MDBList on the server, so this is a single request per item.
 * The server admin's choice of which ratings to show and in what order is respected.
 */
@Singleton
class MdbListRatingsService
    @Inject
    constructor(
        @param:AuthOkHttpClient private val okHttpClient: OkHttpClient,
        private val api: ApiClient,
        private val userPreferencesService: UserPreferencesService,
    ) {
        private val json =
            Json {
                isLenient = true
                ignoreUnknownKeys = true
            }

        /**
         * Settings per server base URL, an absent plugin is stored as [PluginSettings.NOT_INSTALLED]
         */
        private val settingsCache = ConcurrentHashMap<String, PluginSettings>()

        private val ratingsCache = LruCache<UUID, List<ExternalRating>>(100)

        /**
         * Get the ratings for an item
         *
         * @return the ratings to display, or an empty list if disabled, the plugin isn't installed, or there are no ratings
         */
        suspend fun getRatings(itemId: UUID): List<ExternalRating> =
            withContext(WholphinDispatchers.IO) {
                val enabled =
                    DisplayToggle.MDBLIST_RATINGS in
                        userPreferencesService
                            .getCurrent()
                            .appPreferences.interfacePreferences.displayTogglesList
                if (!enabled) return@withContext emptyList()
                ratingsCache.get(itemId)?.let { return@withContext it }
                try {
                    val settings = getSettings() ?: return@withContext emptyList()
                    if (!settings.installed) return@withContext emptyList()
                    val ratings = fetchRatings(itemId, settings) ?: return@withContext emptyList()
                    ratingsCache.put(itemId, ratings)
                    ratings
                } catch (ex: Exception) {
                    Timber.w(ex, "Error fetching MDBList ratings for %s", itemId)
                    emptyList()
                }
            }

        private fun pluginUrl(path: String) =
            api.baseUrl
                ?.toHttpUrlOrNull()
                ?.newBuilder()
                ?.addPathSegments("Plugins/MdbListRatings")
                ?.addPathSegments(path)

        /**
         * URL to an icon served by the plugin
         */
        private fun iconUrl(icon: ExternalRatingIcon): String? = pluginUrl("Assets")?.addPathSegment(icon.fileName)?.build()?.toString()

        @OptIn(ExperimentalSerializationApi::class)
        private fun getSettings(): PluginSettings? {
            val baseUrl = api.baseUrl ?: return null
            settingsCache[baseUrl]?.let { return it }
            val url = pluginUrl("WebClientSettings")?.build() ?: return null
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()
            val settings =
                okHttpClient.newCall(request).execute().use { res ->
                    if (res.isSuccessful) {
                        json.decodeFromStream<PluginSettings>(res.body.byteStream())
                    } else if (res.code == 404) {
                        Timber.i("MDBList Ratings plugin is not installed")
                        PluginSettings.NOT_INSTALLED
                    } else {
                        // Don't cache other errors so it can be retried
                        Timber.w("Error fetching MDBList plugin settings: %s", res.code)
                        return null
                    }
                }
            settingsCache[baseUrl] = settings
            return settings
        }

        @OptIn(ExperimentalSerializationApi::class)
        private fun fetchRatings(
            itemId: UUID,
            settings: PluginSettings,
        ): List<ExternalRating>? {
            val url =
                pluginUrl("CachedByItemId")
                    ?.addQueryParameter("itemId", itemId.toString())
                    ?.build() ?: return null
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()
            val response =
                okHttpClient.newCall(request).execute().use { res ->
                    if (!res.isSuccessful) {
                        Timber.w("Error fetching MDBList ratings for %s: %s", itemId, res.code)
                        return null
                    }
                    json.decodeFromStream<CachedRatingsResponse>(res.body.byteStream())
                }
            val ratings =
                response.ratings
                    .mapNotNull { rating ->
                        val value = rating.value?.takeIf { it > 0 } ?: return@mapNotNull null
                        val external = ExternalRating(source = rating.source, value = value, score = rating.score?.roundToInt())
                        val icon = external.icon ?: return@mapNotNull null
                        external.copy(iconUrl = iconUrl(icon))
                    }
            return settings.order?.let { order ->
                ratings
                    .filter { it.source in order }
                    .sortedBy { order.indexOf(it.source) }
            } ?: ratings
        }

        @Serializable
        private data class PluginSettings(
            val installed: Boolean = true,
            val webAllRatingsMode: String = "all",
            val webAllRatingsOrderCsv: String = "",
        ) {
            /**
             * The sources to show in order, or null to show all of them
             */
            val order: List<String>?
                get() =
                    if (webAllRatingsMode == "custom") {
                        webAllRatingsOrderCsv
                            .split(",")
                            .map { it.trim().lowercase() }
                            .filter { it.isNotBlank() }
                            .takeIf { it.isNotEmpty() }
                    } else {
                        null
                    }

            companion object {
                val NOT_INSTALLED = PluginSettings(installed = false)
            }
        }

        @Serializable
        private data class CachedRatingsResponse(
            val ratings: List<CachedRating> = emptyList(),
        )

        @Serializable
        private data class CachedRating(
            val source: String,
            val value: Double? = null,
            val score: Double? = null,
        )
    }
