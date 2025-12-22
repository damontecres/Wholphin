package com.github.damontecres.wholphin.ui.setup

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.JellyfinServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.SetupNavigationManager
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.discovery.RecommendedServerIssue
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class SwitchServerViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val jellyfin: Jellyfin,
        val serverRepository: ServerRepository,
        val serverDao: JellyfinServerDao,
        val navigationManager: SetupNavigationManager,
    ) : ViewModel() {
        val servers = MutableLiveData<List<JellyfinServer>>(listOf())
        val serverStatus = MutableLiveData<Map<UUID, ServerConnectionStatus>>(mapOf())
        val serverQuickConnect = MutableLiveData<Map<UUID, Boolean>>(mapOf())

        val discoveredServers = MutableLiveData<List<JellyfinServer>>(listOf())

        val addServerState = MutableLiveData<LoadingState>(LoadingState.Pending)

        fun clearAddServerState() {
            addServerState.value = LoadingState.Pending
        }

        fun init() {
            viewModelScope.launchIO {
                withContext(Dispatchers.Main) {
                    serverStatus.value = mapOf()
                    serverQuickConnect.value = mapOf()
                }

                val allServers =
                    serverDao
                        .getServers()
                        .map { it.server }
                        .sortedWith(compareBy<JellyfinServer> { it.name }.thenBy { it.url })
                withContext(Dispatchers.Main) {
                    servers.value = allServers
                }
                allServers.forEach { server ->
                    internalTestServer(server)
                }
            }
        }

        fun testServer(server: JellyfinServer) {
            serverStatus.value =
                serverStatus.value!!.toMutableMap().apply {
                    put(
                        server.id,
                        ServerConnectionStatus.Pending,
                    )
                }
            viewModelScope.launchIO {
                delay(1000)
                val result = internalTestServer(server)
                if (result is ServerConnectionStatus.Success) {
                    showToast(context, context.getString(R.string.success), Toast.LENGTH_SHORT)
                } else if (result is ServerConnectionStatus.Error) {
                    showToast(context, result.message ?: "Error", Toast.LENGTH_SHORT)
                }
            }
        }

        private suspend fun internalTestServer(server: JellyfinServer): ServerConnectionStatus =
            try {
                val systemInfo =
                    jellyfin
                        .createApi(
                            server.url,
                            httpClientOptions =
                                HttpClientOptions(
                                    requestTimeout = 6.seconds,
                                    connectTimeout = 6.seconds,
                                    socketTimeout = 6.seconds,
                                ),
                        ).systemApi
                        .getPublicSystemInfo()
                        .content
                val result = ServerConnectionStatus.Success(systemInfo)
                withContext(Dispatchers.Main) {
                    serverStatus.value =
                        serverStatus.value!!.toMutableMap().apply {
                            put(
                                server.id,
                                result,
                            )
                        }
                }
                result
            } catch (ex: Exception) {
                val status = ServerConnectionStatus.Error(ex.localizedMessage)
                Timber.w(ex, "Error checking server ${server.url}")
                withContext(Dispatchers.Main) {
                    serverStatus.value =
                        serverStatus.value!!.toMutableMap().apply {
                            put(
                                server.id,
                                status,
                            )
                        }
                }
                status
            }

        fun switchServer(server: JellyfinServer) {
            viewModelScope.launchIO {
                withContext(Dispatchers.Main) {
                    serverStatus.value =
                        serverStatus.value!!.toMutableMap().apply {
                            put(
                                server.id,
                                ServerConnectionStatus.Pending,
                            )
                        }
                }
                val result = internalTestServer(server)
                if (result is ServerConnectionStatus.Success) {
                    val updatedServer =
                        server.copy(
                            name = result.systemInfo.serverName,
                            version = result.systemInfo.version,
                        )
                    serverRepository.addAndChangeServer(updatedServer)
                    withContext(Dispatchers.Main) {
                        navigationManager.navigateTo(SetupDestination.UserList(updatedServer))
                    }
                } else if (result is ServerConnectionStatus.Error) {
                    showToast(context, "Error connecting: $${result.message}")
                }
            }
        }

        fun addServer(inputUrl: String) {
            addServerState.value = LoadingState.Loading
            viewModelScope.launchIO {
                try {
                    val scores =
                        jellyfin.discovery.getRecommendedServers(inputUrl).sortedBy { it.score }
                    val bestServer =
                        scores.firstOrNull { it.score != RecommendedServerInfoScore.BAD }
                    val serverInfo = bestServer?.systemInfo?.getOrNull()
                    if (bestServer != null && serverInfo != null) {
                        val serverUrl = bestServer.address

                        val id = serverInfo.id?.toUUIDOrNull()

                        if (id != null && serverInfo.startupWizardCompleted == true) {
                            val server =
                                JellyfinServer(
                                    id = id,
                                    name = serverInfo.serverName,
                                    url = serverUrl,
                                    version = serverInfo.version,
                                )
                            serverRepository.addAndChangeServer(server)
                            val quickConnect =
                                jellyfin
                                    .createApi(serverUrl)
                                    .quickConnectApi
                                    .getQuickConnectEnabled()
                                    .content
                            withContext(Dispatchers.Main) {
                                serverQuickConnect.value =
                                    serverQuickConnect.value!!.toMutableMap().apply {
                                        put(id, quickConnect)
                                    }
                            }
                            withContext(Dispatchers.Main) {
                                addServerState.value = LoadingState.Success
                                navigationManager.navigateTo(SetupDestination.UserList(server))
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                addServerState.value =
                                    LoadingState.Error("Server returned invalid response")
                            }
                        }
                    } else {
                        Timber.w("Error connecting with %s: %s", inputUrl, scores)
                        // No good server candidate
                        val errors =
                            scores.joinToString("\n") {
                                val issues =
                                    it.issues.firstOrNull()?.let {
                                        when (it) {
                                            is RecommendedServerIssue.InvalidProductName,
                                            is RecommendedServerIssue.MissingSystemInfo,
                                            -> "Invalid server info"

                                            is RecommendedServerIssue.SecureConnectionFailed,
                                            is RecommendedServerIssue.ServerUnreachable,
                                            is RecommendedServerIssue.SlowResponse,
                                            -> "Unable to connect"

                                            RecommendedServerIssue.MissingVersion,
                                            is RecommendedServerIssue.OutdatedServerVersion,
                                            is RecommendedServerIssue.UnsupportedServerVersion,
                                            -> "Unsupported server version"
                                        }
                                    }
                                "${it.address} - $issues"
                            }
                        val message = "Error, tried addresses:\n$errors"
                        addServerState.setValueOnMain(LoadingState.Error(message))
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Error creating API for $inputUrl")
                    withContext(Dispatchers.Main) {
                        addServerState.value =
                            LoadingState.Error(exception = ex)
                    }
                }
            }
        }

        fun removeServer(server: JellyfinServer) {
            viewModelScope.launchIO {
                serverRepository.removeServer(server)
                init()
            }
        }

        fun discoverServers() {
            viewModelScope.launchIO {
                jellyfin.discovery.discoverLocalServers().collect { server ->
                    val newServerList =
                        discoveredServers.value!!
                            .toMutableList()
                            .apply {
                                add(
                                    JellyfinServer(
                                        server.id.toUUID(),
                                        server.name,
                                        server.address,
                                        null,
                                    ),
                                )
                            }
                    withContext(Dispatchers.Main) {
                        discoveredServers.value = newServerList
                    }
                }
            }
        }
    }
