package com.github.damontecres.wholphin.ui.preferences

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.resetSubtitles
import com.github.damontecres.wholphin.preferences.updateSubtitlePreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.Release
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.UpdateChecker
import com.github.damontecres.wholphin.ui.detail.DebugViewModel.Companion.sendAppLogs
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.RememberTabManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        val preferenceDataStore: DataStore<AppPreferences>,
        val navigationManager: NavigationManager,
        val backdropService: BackdropService,
        val screensaverService: ScreensaverService,
        private val rememberTabManager: RememberTabManager,
        private val serverRepository: ServerRepository,
        private val seerrServerRepository: SeerrServerRepository,
        private val deviceInfo: DeviceInfo,
        private val clientInfo: ClientInfo,
        private val updateChecker: UpdateChecker,
    ) : ViewModel(),
        RememberTabManager by rememberTabManager {
        val currentUser get() = serverRepository.currentUser

        val seerrConnection = seerrServerRepository.connection

        private val _quickConnectStatus = MutableStateFlow<LoadingState>(LoadingState.Pending)
        val quickConnectStatus: StateFlow<LoadingState> = _quickConnectStatus

        val releaseNotes = MutableStateFlow<DataLoadingState<Release>>(DataLoadingState.Pending)

        val externalPlayers = MutableStateFlow<List<ExternalPlayerApp>>(emptyList())

        init {
            viewModelScope.launchIO {
                val fakeIntent =
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType("https://damontecres.com/video.mp4".toUri(), "video/*")
                    }
                val externalPlayers = getExternalPlayers(context)
                val systemDefault =
                    ExternalPlayerApp(
                        name = context.getString(R.string.system_default),
                        icon = null,
                        identifier = "",
                    )
                this@PreferencesViewModel.externalPlayers.update { listOf(systemDefault) + externalPlayers }
            }
        }

        fun sendAppLogs() {
            sendAppLogs(context, api, clientInfo, deviceInfo)
        }

        fun resetSubtitleSettings() {
            viewModelScope.launchIO {
                resetSubtitleSettings(preferenceDataStore)
            }
        }

        fun setPin(
            user: JellyfinUser,
            pin: String?,
        ) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                serverRepository.setUserPin(user, pin)
            }
        }

        fun resetQuickConnectStatus() {
            _quickConnectStatus.value = LoadingState.Pending
        }

        fun authorizeQuickConnect(code: String) {
            viewModelScope.launchIO {
                _quickConnectStatus.value = LoadingState.Loading
                try {
                    val success = serverRepository.authorizeQuickConnect(code)
                    _quickConnectStatus.value =
                        if (success) {
                            LoadingState.Success
                        } else {
                            LoadingState.Error("Authorization failed")
                        }
                } catch (e: Exception) {
                    _quickConnectStatus.value = LoadingState.Error(e)
                }
            }
        }

        fun fetchReleaseNotes() {
            viewModelScope.launchIO {
                releaseNotes.update { DataLoadingState.Loading }
                try {
                    val release = updateChecker.getRelease(updateChecker.getInstalledVersion())
                    if (release != null) {
                        releaseNotes.update { DataLoadingState.Success(release) }
                    } else {
                        releaseNotes.update { DataLoadingState.Error("Release not found") }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching release")
                    releaseNotes.update { DataLoadingState.Error(ex) }
                }
            }
        }

        companion object {
            suspend fun resetSubtitleSettings(appPreferences: DataStore<AppPreferences>) {
                appPreferences.updateData {
                    it.updateSubtitlePreferences {
                        resetSubtitles()
                    }
                }
            }
        }
    }

data class ExternalPlayerApp(
    val name: String,
    val icon: ImageBitmap?,
    val identifier: String,
)

fun getExternalPlayers(context: Context): List<ExternalPlayerApp> {
    val fakeIntent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType("https://damontecres.com/video.mp4".toUri(), "video/*")
        }
    val externalPlayers =
        context.packageManager
            .queryIntentActivities(fakeIntent, PackageManager.MATCH_ALL)
            .filter { it.priority >= 0 }
            .map {
                val component =
                    ComponentName(
                        it.activityInfo.packageName,
                        it.activityInfo.name,
                    )
                ExternalPlayerApp(
                    name = it.loadLabel(context.packageManager).toString(),
                    icon =
                        it
                            .loadIcon(context.packageManager)
                            .toBitmap()
                            .asImageBitmap(),
                    identifier = component.flattenToString(),
                )
            }
    return externalPlayers
}
