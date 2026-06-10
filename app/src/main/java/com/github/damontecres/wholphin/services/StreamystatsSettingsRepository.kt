package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.StreamystatsSettingsDao
import com.github.damontecres.wholphin.data.model.StreamystatsSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads Streamystats settings for the currently signed-in Jellyfin user.
 *
 * The source boundary is intentionally small so a future Jellyfin-plugin source can be checked
 * before the local Room source without changing Streamystats API calls or home row rendering.
 */
interface StreamystatsSettingsSource {
    suspend fun getCurrentSettings(): StreamystatsSettings?
}

@Singleton
class LocalStreamystatsSettingsSource
    @Inject
    constructor(
        private val streamystatsSettingsDao: StreamystatsSettingsDao,
        private val serverRepository: ServerRepository,
    ) : StreamystatsSettingsSource {
        override suspend fun getCurrentSettings(): StreamystatsSettings? {
            val current = serverRepository.current.value ?: return null
            return streamystatsSettingsDao.get(
                jellyfinUserRowId = current.user.rowId,
                jellyfinServerId = current.server.id,
            )
        }
    }

@Singleton
class StreamystatsSettingsRepository
    @Inject
    constructor(
        private val streamystatsSettingsDao: StreamystatsSettingsDao,
        private val serverRepository: ServerRepository,
        private val localSource: LocalStreamystatsSettingsSource,
    ) {
        private val _connection =
            MutableStateFlow<StreamystatsConnectionStatus>(StreamystatsConnectionStatus.NotConfigured)
        val connection: StateFlow<StreamystatsConnectionStatus> = _connection

        val active = connection.map { it is StreamystatsConnectionStatus.Success }

        suspend fun loadForCurrentUser() {
            val settings = localSource.getCurrentSettings()
            _connection.update {
                settings?.let { StreamystatsConnectionStatus.Success(it) }
                    ?: StreamystatsConnectionStatus.NotConfigured
            }
        }

        fun clear() {
            _connection.update { StreamystatsConnectionStatus.NotConfigured }
        }

        suspend fun setServerUrl(url: String) {
            val current = serverRepository.current.value ?: return
            val settings =
                StreamystatsSettings(
                    jellyfinUserRowId = current.user.rowId,
                    jellyfinServerId = current.server.id,
                    serverUrl = normalizeStreamystatsUrl(url),
                )
            streamystatsSettingsDao.set(settings)
            _connection.update { StreamystatsConnectionStatus.Success(settings) }
        }

        suspend fun removeForCurrentUser(): Boolean {
            val current = serverRepository.current.value ?: return false
            val rows =
                streamystatsSettingsDao.delete(
                    jellyfinUserRowId = current.user.rowId,
                    jellyfinServerId = current.server.id,
                )
            clear()
            return rows > 0
        }
    }

sealed interface StreamystatsConnectionStatus {
    data object NotConfigured : StreamystatsConnectionStatus

    data class Success(
        val settings: StreamystatsSettings,
    ) : StreamystatsConnectionStatus
}

fun normalizeStreamystatsUrl(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isBlank()) {
        throw IllegalArgumentException("URL is blank")
    }
    val withScheme =
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    return withScheme
        .toHttpUrl()
        .newBuilder()
        .build()
        .toString()
        .removeSuffix("/")
}
