package com.github.damontecres.wholphin.services

import android.content.Context
import android.os.Build
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import com.github.damontecres.wholphin.ui.detail.DebugViewModel.Companion.getLogCatLines
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.google.protobuf.MessageLiteOrBuilder
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

/**
 * Send reports to the server such as media info or app logs
 */
@Singleton
class ServerReportService
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

        /**
         * Fetch the media info and send it to the server
         */
        fun sendMediaReportFor(itemId: UUID) {
            ioScope.launchIO(ExceptionHandler(autoToast = true)) {
                val item = api.userLibraryApi.getItem(itemId = itemId).content
                sendMediaReportFor(item)
            }
        }

        /**
         * Send the media report for the given item
         */
        suspend fun sendMediaReportFor(item: BaseItemDto) {
            val sources =
                item.mediaSources ?: api.userLibraryApi
                    .getItem(itemId = item.id)
                    .content.mediaSources
            val sourcesJson = json.encodeToString(sources)
            val appPreferences = userPreferencesService.getCurrent().appPreferences
            val serverVersion = serverRepository.currentServer?.serverVersion
            val deviceProfile =
                deviceProfileService.getOrCreateDeviceProfile(appPreferences, serverVersion)
            val deviceProfileJson = json.encodeToString(deviceProfile)
            val body =
                buildLogHeader("Send media info") + "\n\n" +
                    """
                    playbackPrefs=${appPreferences.playbackPreferences.toStringOneLine()}

                    experimental=${appPreferences.experimentalPreferences.toStringOneLine()}

                    mediaSources=$sourcesJson

                    deviceProfile=$deviceProfileJson
                    """.trimIndent()
            body.chunked(2048).forEach { Timber.w(it) }
            Timber.w("End send media info")
            val response by api.clientLogApi.logFile(body)
            showToast(context, "Sent! Filename=${response.fileName}")
        }

        fun buildLogHeader(title: String): String {
            val serverVersion = serverRepository.currentServer?.serverVersion
            return """
                $title
                serverVersion=$serverVersion
                clientInfo=$clientInfo
                flavor=${BuildConfig.FLAVOR}
                deviceInfo=$deviceInfo
                manufacturer=${Build.MANUFACTURER}
                model=${Build.MODEL}
                apiLevel=${Build.VERSION.SDK_INT}
                """.trimIndent()
        }

        suspend fun sendAppLogs() {
            val logcat = getLogCatLines().joinToString("\n") { it.text }
            val header = buildLogHeader("Send App Logs")
            Timber.w(header)
            val response by api.clientLogApi.logFile(header + "\n\n" + logcat)
            showToast(context, "Sent! Filename=${response.fileName}")
        }
    }

private fun MessageLiteOrBuilder.toStringOneLine(): String = toString().replace("\n", ", ").replace("\t", " ")
