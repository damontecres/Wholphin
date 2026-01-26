package com.github.damontecres.wholphin.ui.setup.seerr

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.infrastructure.ClientException
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

@HiltViewModel
class SwitchSeerrViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
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
            username: String,
            passwordOrApiKey: String,
            authMethod: SeerrAuthMethod,
        ) {
            viewModelScope.launchIO {
                val url = cleanUrl(url)
                val result =
                    try {
                        seerrServerRepository.testConnection(
                            authMethod = authMethod,
                            url = url,
                            username = username.takeIf { authMethod != SeerrAuthMethod.API_KEY },
                            passwordOrApiKey = passwordOrApiKey,
                        )
                    } catch (ex: ClientException) {
                        Timber.w(ex, "Error logging in via API Key")
                        if (ex.statusCode == 401 || ex.statusCode == 403) {
                            showToast(context, "Invalid credentials")
                            LoadingState.Error("Invalid credentials", ex)
                        } else {
                            showToast(context, "Error: ${ex.localizedMessage}")
                            LoadingState.Error(ex)
                        }
                    }
                if (result is LoadingState.Success) {
                    when (authMethod) {
                        SeerrAuthMethod.LOCAL,
                        SeerrAuthMethod.JELLYFIN,
                        -> {
                            seerrServerRepository.addAndChangeServer(
                                url,
                                authMethod,
                                username,
                                passwordOrApiKey,
                            )
                        }

                        SeerrAuthMethod.API_KEY -> {
                            seerrServerRepository.addAndChangeServer(
                                url,
                                passwordOrApiKey,
                            )
                        }
                    }
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
