package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.HomePageSettings
import com.github.damontecres.wholphin.ui.toServerString
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.displayPreferencesApi
import org.jellyfin.sdk.model.UUID
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeSettingsService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) {
        val jsonParser =
            Json {
                isLenient = true
                ignoreUnknownKeys = true
            }

        suspend fun saveToServer(
            settings: HomePageSettings,
            displayPreferencesId: String = DISPLAY_PREF_ID,
        ) {
            serverRepository.currentUser.value?.let { user ->
                val current = getDisplayPreferences(user.id, DISPLAY_PREF_ID)
                val customPrefs =
                    current.customPrefs.toMutableMap().apply {
                        put(CUSTOM_PREF_ID, jsonParser.encodeToString(settings))
                    }
                api.displayPreferencesApi.updateDisplayPreferences(
                    displayPreferencesId = displayPreferencesId,
                    userId = user.id,
                    client = context.getString(R.string.app_name),
                    data = current.copy(customPrefs = customPrefs),
                )
            }
        }

        suspend fun loadFromServer(displayPreferencesId: String = DISPLAY_PREF_ID): HomePageSettings? =
            serverRepository.currentUser.value?.let { user ->
                val current = getDisplayPreferences(user.id, displayPreferencesId)
                current.customPrefs[DISPLAY_PREF_ID]?.let {
                    Json.decodeFromString<HomePageSettings>(it)
                }
            }

        private suspend fun getDisplayPreferences(
            userId: UUID,
            displayPreferencesId: String,
        ) = api.displayPreferencesApi
            .getDisplayPreferences(
                userId = userId,
                displayPreferencesId = displayPreferencesId,
                client = context.getString(R.string.app_name),
            ).content

        private fun filename(userId: UUID) = "${CUSTOM_PREF_ID}_${userId.toServerString()}.json"

        @OptIn(ExperimentalSerializationApi::class)
        suspend fun saveToLocal(settings: HomePageSettings) {
            serverRepository.currentUser.value?.let { user ->
                val dir = File(context.filesDir, CUSTOM_PREF_ID)
                dir.mkdirs()
                File(dir, filename(user.id)).outputStream().use {
                    jsonParser.encodeToStream(settings, it)
                }
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        suspend fun loadFromLocal(): HomePageSettings? =
            serverRepository.currentUser.value?.let { user ->
                val dir = File(context.filesDir, CUSTOM_PREF_ID)
                val file = File(dir, filename(user.id))
                if (file.exists()) {
                    file.inputStream().use {
                        jsonParser.decodeFromStream<HomePageSettings>(it)
                    }
                } else {
                    null
                }
            }

        companion object {
            const val DISPLAY_PREF_ID = "default"
            const val CUSTOM_PREF_ID = "home_settings"
        }
    }
