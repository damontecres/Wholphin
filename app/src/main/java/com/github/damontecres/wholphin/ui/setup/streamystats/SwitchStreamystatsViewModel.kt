package com.github.damontecres.wholphin.ui.setup.streamystats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.HomeSettingsService
import com.github.damontecres.wholphin.services.StreamystatsService
import com.github.damontecres.wholphin.services.StreamystatsSettingsRepository
import com.github.damontecres.wholphin.services.normalizeStreamystatsUrl
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class SwitchStreamystatsViewModel
    @Inject
    constructor(
        val streamystatsSettingsRepository: StreamystatsSettingsRepository,
        private val streamystatsService: StreamystatsService,
        private val serverRepository: ServerRepository,
        private val homeSettingsService: HomeSettingsService,
    ) : ViewModel() {
        val connection = streamystatsSettingsRepository.connection
        val status = MutableStateFlow<LoadingState>(LoadingState.Pending)

        fun submitServer(url: String) {
            viewModelScope.launchIO {
                status.update { LoadingState.Loading }
                try {
                    val normalizedUrl = normalizeStreamystatsUrl(url)
                    streamystatsService.testConnection(normalizedUrl)
                    streamystatsSettingsRepository.setServerUrl(normalizedUrl)
                    reloadHomeSettings()
                    status.update { LoadingState.Success }
                } catch (ex: IllegalArgumentException) {
                    status.update { LoadingState.Error("Invalid URL", ex) }
                } catch (ex: Exception) {
                    status.update { LoadingState.Error(ex) }
                }
            }
        }

        fun removeServer() {
            viewModelScope.launchIO {
                streamystatsSettingsRepository.removeForCurrentUser()
                homeSettingsService.removeStreamystatsRowsForCurrentUser()
                reloadHomeSettings()
                status.update { LoadingState.Pending }
            }
        }

        private suspend fun reloadHomeSettings() {
            serverRepository.currentUser?.let { homeSettingsService.loadCurrentSettings(it.id) }
        }

        fun resetStatus() {
            status.update { LoadingState.Pending }
        }
    }
