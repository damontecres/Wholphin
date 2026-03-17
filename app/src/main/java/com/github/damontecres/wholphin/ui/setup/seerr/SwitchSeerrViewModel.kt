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
                Timber.v("Urls to try: %s", urls)
                val results = mutableMapOf<HttpUrl, LoadingState>()
                for (url in urls) {
                    Timber.d("Trying %s", url)
                    try {
                        seerrServerRepository.testConnection(
                            authMethod = authMethod,
                            url = url.toString(),
                            username = username.takeIf { authMethod != SeerrAuthMethod.API_KEY },
                            passwordOrApiKey = passwordOrApiKey,
                        )
                        results[url] = LoadingState.Success
                        break
                    } catch (ex: ClientException) {
                        Timber.w(ex, "ClientException logging in %s", url)
                        if (ex.statusCode == 401 || ex.statusCode == 403) {
                            showToast(context, "Invalid credentials")
                            results[url] = LoadingState.Error("Invalid credentials", ex)
                        } else {
                            results[url] = LoadingState.Error("Could not connect with URL")
                        }
                    } catch (ex: Exception) {
                        Timber.w(ex, "ClientException logging in %s", url)
                        results[url] = LoadingState.Error(ex)
                    }
                }
                val result = results.filter { (url, state) -> state is LoadingState.Success }
                if (result.isNotEmpty()) {
                    val url = result.keys.first()
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
                    serverConnectionStatus.update { LoadingState.Success }
                } else {
                    val message =
                        results
                            .map { (url, state) ->
                                val s = state as? LoadingState.Error
                                "$url - ${s?.localizedMessage}"
                            }.joinToString("\n")
                    showToast(context, "Could not connect")
                    serverConnectionStatus.update { LoadingState.Error(message) }
                }
            }
        }

        fun removeServer() {
            viewModelScope.launchIO {
                val result = seerrServerRepository.removeServerForCurrentUser()
                if (!result) {
                    showToast(context, "Could not remove server")
                }
            }
        }

        fun resetStatus() {
            serverConnectionStatus.update { LoadingState.Pending }
        }
    }

fun createUrls(url: String): List<HttpUrl> {
    val urls = mutableListOf<HttpUrl>()
    if (url.startsWith("http://") || url.startsWith("https://")) {
        val httpUrl = url.toHttpUrl()
        urls.add(httpUrl)
        if (HttpUrl.defaultPort(httpUrl.scheme) == httpUrl.port) {
            urls.add(httpUrl.newBuilder().port(5055).build())
        }
    } else {
        val httpUrl = "http://$url".toHttpUrl()
        urls.add(httpUrl)
        if (httpUrl.port == 80) {
            urls.add(httpUrl.newBuilder().scheme("https").build())
            urls.add(httpUrl.newBuilder().port(5055).build())
            urls.add(
                httpUrl
                    .newBuilder()
                    .scheme("https")
                    .port(5055)
                    .build(),
            )
        } else {
            urls.add(httpUrl.newBuilder().scheme("https").build())
        }
    }
    return urls
}

fun createSeerrApiUrl(url: String): String =
    if (url.isBlank()) {
        url
    } else if (url.endsWith("/api/v1") || url.endsWith("/api/v1/")) {
        url
    } else {
        url
            .toHttpUrl()
            .newBuilder()
            .addPathSegment("api")
            .addPathSegment("v1")
            .build()
            .toString()
    }

fun migrateSeerrUrl(url: String): String {
    var url = url.removeSuffix("/api/v1/").removeSuffix("/api/v1")
    if (!url.endsWith("/")) url += "/"
    return url
}
