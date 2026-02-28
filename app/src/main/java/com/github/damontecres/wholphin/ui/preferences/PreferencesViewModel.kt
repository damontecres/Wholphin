package com.github.damontecres.wholphin.ui.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.resetSubtitles
import com.github.damontecres.wholphin.preferences.updateSubtitlePreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.ui.detail.DebugViewModel.Companion.sendAppLogs
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.RememberTabManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
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
    ) : ViewModel(),
        RememberTabManager by rememberTabManager {
        val currentUser get() = serverRepository.currentUser

        val seerrConnection = seerrServerRepository.connection

        private val _quickConnectStatus = MutableStateFlow<LoadingState>(LoadingState.Pending)
        val quickConnectStatus: StateFlow<LoadingState> = _quickConnectStatus

        init {
            viewModelScope.launchIO {
                serverRepository.currentUser.value?.let { user ->
//                    fetchNavDrawerPins(user)
                }
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
