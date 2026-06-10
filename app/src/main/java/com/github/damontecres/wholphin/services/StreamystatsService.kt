package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.StreamystatsRecommendationType
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_STREAMYSTATS_ERROR_BODY_LOG_LENGTH = 500

@Singleton
class StreamystatsService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val settingsRepository: StreamystatsSettingsRepository,
        @param:AuthOkHttpClient private val okHttpClient: OkHttpClient,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Loads recommended Jellyfin item IDs from Streamystats.
         *
         * Endpoint shape mirrors Streamyfin's local client (`utils/streamystats/api.ts`)
         * for Streamystats: https://github.com/fredrikburmester/streamystats
         */
        suspend fun getRecommendationIds(
            type: StreamystatsRecommendationType,
            limit: Int,
        ): List<UUID> {
            val settings =
                (settingsRepository.connection.first() as? StreamystatsConnectionStatus.Success)
                    ?.settings
                    ?: return emptyList()
            val jellyfinServerId = serverRepository.currentServer?.id ?: return emptyList()
            return getRecommendationIds(settings.serverUrl, jellyfinServerId, type, limit)
        }

        suspend fun testConnection(serverUrl: String) {
            val jellyfinServerId =
                serverRepository.currentServer?.id
                    ?: throw IllegalStateException("No Jellyfin server selected")
            getRecommendationIds(serverUrl, jellyfinServerId, StreamystatsRecommendationType.MOVIE, 1)
        }

        private fun getRecommendationIds(
            serverUrl: String,
            jellyfinServerId: UUID,
            type: StreamystatsRecommendationType,
            limit: Int,
        ): List<UUID> {
            val url =
                serverUrl
                    .toHttpUrl()
                    .newBuilder()
                    .addPathSegment("api")
                    .addPathSegment("recommendations")
                    .addQueryParameter("jellyfinServerId", jellyfinServerId.toJellyfinServerId())
                    .addQueryParameter("format", "ids")
                    .addQueryParameter("type", type.queryValue)
                    .addQueryParameter("limit", limit.toString())
                    .addQueryParameter("includeBasedOn", "false")
                    .addQueryParameter("includeReasons", "false")
                    .build()
            Timber.d(
                "Calling Streamystats recommendations endpoint: GET %s; configuredUrl=%s",
                url,
                serverUrl,
            )
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()
            return okHttpClient.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    Timber.w(
                        "Streamystats recommendations request failed: status=%s url=%s body=%s",
                        response.code,
                        url,
                        body.take(MAX_STREAMYSTATS_ERROR_BODY_LOG_LENGTH),
                    )
                    throw StreamystatsApiException(response.code)
                }
                val ids =
                    json
                        .decodeFromString<StreamystatsRecommendationIdsResponse>(body)
                        .data
                        .idsFor(type)
                ids.mapNotNull { it.toUUIDOrNull() }
            }
        }
    }

@Serializable
private data class StreamystatsRecommendationIdsResponse(
    val data: StreamystatsRecommendationIds = StreamystatsRecommendationIds(),
)

@Serializable
private data class StreamystatsRecommendationIds(
    val movies: List<String> = emptyList(),
    val series: List<String> = emptyList(),
) {
    fun idsFor(type: StreamystatsRecommendationType): List<String> =
        when (type) {
            StreamystatsRecommendationType.MOVIE -> movies
            StreamystatsRecommendationType.SERIES -> series
        }
}

// Jellyfin exposes PublicSystemInfo.Id as a 32-character hex string. Wholphin stores
// the same value as UUID, so convert it back before calling Streamystats.
private fun UUID.toJellyfinServerId(): String = toString().replace("-", "")

class StreamystatsApiException(
    val statusCode: Int,
) : RuntimeException("HTTP $statusCode from Streamystats")
