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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
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
        val currentSeerrServer = seerrServerRepository.currentServer

        val serverConnectionStatus = MutableStateFlow<LoadingState>(LoadingState.Pending)

        fun submitServer(
            url: String,
            username: String,
            passwordOrApiKey: String,
            authMethod: SeerrAuthMethod,
        ) {
            viewModelScope.launchIO {
                serverConnectionStatus.update { LoadingState.Loading }
                val urls =
                    try {
                        createUrls(url)
                    } catch (ex: IllegalArgumentException) {
                        showToast(context, "Invalid URL")
                        serverConnectionStatus.update { LoadingState.Error("Invalid URL", ex) }
                        return@launchIO
                    }
                var result: LoadingState = LoadingState.Error("No url")
                for (url in urls) {
                    Timber.d("Trying %s", url)
                    result =
                        try {
                            seerrServerRepository.testConnection(
                                authMethod = authMethod,
                                url = url.toString(),
                                username = username.takeIf { authMethod != SeerrAuthMethod.API_KEY },
                                passwordOrApiKey = passwordOrApiKey,
                            )
                        } catch (ex: ClientException) {
                            Timber.w(ex, "ClientException logging in")
                            if (ex.statusCode == 401 || ex.statusCode == 403) {
                                showToast(context, "Invalid credentials")
                                result = LoadingState.Error("Invalid credentials", ex)
                                break
                            } else {
                                LoadingState.Error("Could not connect with URL")
                            }
                        } catch (ex: Exception) {
                            Timber.w(ex, "Exception logging in")
                            LoadingState.Error(ex)
                        }
                    if (result is LoadingState.Success) {
                        when (authMethod) {
                            SeerrAuthMethod.LOCAL,
                            SeerrAuthMethod.JELLYFIN,
                            -> {
                                seerrServerRepository.addAndChangeServer(
                                    url.toString(),
                                    authMethod,
                                    username,
                                    passwordOrApiKey,
                                )
                            }

                            SeerrAuthMethod.API_KEY -> {
                                seerrServerRepository.addAndChangeServer(
                                    url.toString(),
                                    passwordOrApiKey,
                                )
                            }
                        }
                        break
                    }
                }
                if (result is LoadingState.Error) {
                    showToast(context, "Error: ${result.message}")
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

fun createUrls(url: String): List<HttpUrl> {
    val urls = mutableListOf<String>()
    if (url.startsWith("http://") || url.startsWith("https://")) {
        urls.add(url)
        val httpUrl = url.toHttpUrl()
        if (HttpUrl.defaultPort(httpUrl.scheme) == httpUrl.port) {
            urls.add("$url:5055")
        }
    } else {
        urls.add("http://$url")
        val httpUrl = "http://$url".toHttpUrl()
        if (httpUrl.port == 80) {
            urls.add("https://$url")
            urls.add("http://$url:5055")
            urls.add("https://$url:5055")
        } else {
            urls.add("https://$url")
        }
    }
    return urls.map { cleanUrl(it).toHttpUrl() }
}

private fun cleanUrl(url: String) =
    if (!url.endsWith("/api/v1")) {
        url
            .toHttpUrl()
            .newBuilder()
            .apply {
                addPathSegment("api")
                addPathSegment("v1")
            }.build()
            .toString()
    } else {
        url
    }
