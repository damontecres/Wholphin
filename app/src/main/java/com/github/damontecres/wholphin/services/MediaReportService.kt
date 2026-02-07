package com.github.damontecres.wholphin.services

import android.content.Context
import android.os.Build
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ExceptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.clientLogApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaReportService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val userPreferencesService: UserPreferencesService,
        private val clientInfo: ClientInfo,
        private val deviceInfo: DeviceInfo,
        private val deviceProfileService: DeviceProfileService,
        @param:IoCoroutineScope private val ioScope: CoroutineScope,
    ) {
        val json =
            Json {
                encodeDefaults = false
            }

        fun sendReportFor(itemId: UUID) {
            ioScope.launchIO(ExceptionHandler(autoToast = true)) {
                val item = api.userLibraryApi.getItem(itemId = itemId).content
                sendReportFor(item)
            }
        }

        suspend fun sendReportFor(item: BaseItemDto) {
            val sources =
                item.mediaSources ?: api.userLibraryApi
                    .getItem(itemId = item.id)
                    .content.mediaSources
            val sourcesJson = json.encodeToString(sources)
            val playbackPrefs = userPreferencesService.getCurrent().appPreferences.playbackPreferences
            val serverVersion = serverRepository.currentServer.value?.serverVersion
            val deviceProfile =
                deviceProfileService.getOrCreateDeviceProfile(playbackPrefs, serverVersion)
            val deviceProfileJson = json.encodeToString(deviceProfile)
            val body =
                """
                Send media info
                serverVersion=$serverVersion
                clientInfo=$clientInfo
                deviceInfo=$deviceInfo
                manufacturer=${Build.MANUFACTURER}
                model=${Build.MODEL}
                apiLevel=${Build.VERSION.SDK_INT}

                playbackPrefs=${playbackPrefs.toString().replace("\n", ", ").replace("\t", " ")}

                deviceProfile=$deviceProfileJson

                mediaSources=$sourcesJson
                """.trimIndent()
            Timber.w(body)
            val response by api.clientLogApi.logFile(body)
            showToast(context, "Sent! Filename=${response.fileName}")
        }
    }
