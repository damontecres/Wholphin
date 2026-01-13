package com.github.damontecres.wholphin.ui.setup.seerr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.infrastructure.ClientException
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

@HiltViewModel
class SwitchSeerrViewModel
    @Inject
    constructor(
        private val seerrServerRepository: SeerrServerRepository,
        private val seerrService: SeerrService,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        val currentUser = serverRepository.currentUser

        val serverConnectionStatus = MutableStateFlow<LoadingState>(LoadingState.Pending)

        private fun cleanUrl(url: String) =
            if (!url.endsWith("/api/v1")) {
                url
                    .toHttpUrlOrNull()
                    ?.newBuilder()
                    ?.apply {
                        addPathSegment("api")
                        addPathSegment("v1")
                    }?.build()
                    .toString()
            } else {
                url
            }

        fun submitServer(
            url: String,
            apiKey: String,
        ) {
            viewModelScope.launchIO {
                val url = cleanUrl(url)
                val result =
                    try {
                        seerrServerRepository.testConnection(
                            authMethod = SeerrAuthMethod.API_KEY,
                            url = url,
                            username = null,
                            passwordOrApiKey = apiKey,
                        )
                    } catch (ex: ClientException) {
                        Timber.w(ex, "Error logging in via API Key")
                        if (ex.statusCode == 401 || ex.statusCode == 403) {
                            LoadingState.Error("Invalid credentials", ex)
                        } else {
                            LoadingState.Error(ex)
                        }
                    }
                if (result is LoadingState.Success) {
                    seerrServerRepository.addAndChangeServer(url, apiKey)
                }
                serverConnectionStatus.update { result }
            }
        }

        fun submitServer(
            url: String,
            username: String,
            password: String,
            authMethod: SeerrAuthMethod,
        ) {
            viewModelScope.launchIO {
                val url = cleanUrl(url)
                val result =
                    try {
                        seerrServerRepository.testConnection(
                            authMethod = authMethod,
                            url = url,
                            username = username,
                            passwordOrApiKey = password,
                        )
                    } catch (ex: ClientException) {
                        Timber.w(ex, "Error logging in via %s", authMethod)
                        if (ex.statusCode == 401 || ex.statusCode == 403) {
                            LoadingState.Error("Invalid credentials", ex)
                        } else {
                            LoadingState.Error(ex)
                        }
                    }
                if (result is LoadingState.Success) {
                    seerrServerRepository.addAndChangeServer(url, authMethod, username, password)
                }
                serverConnectionStatus.update { result }
            }
        }

        fun removeServer() {
            viewModelScope.launchIO {
                seerrServerRepository.removeServer()
            }
        }

        fun resetStatus() {
            serverConnectionStatus.update { LoadingState.Pending }
        }
    }
