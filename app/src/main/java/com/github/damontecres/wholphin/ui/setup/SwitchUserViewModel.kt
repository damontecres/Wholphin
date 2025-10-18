package com.github.damontecres.wholphin.ui.setup

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.JellyfinServer
import com.github.damontecres.wholphin.data.JellyfinServerDao
import com.github.damontecres.wholphin.data.JellyfinUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.NavigationManager
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.api.QuickConnectResult
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class SwitchUserViewModel
    @Inject
    constructor(
        val jellyfin: Jellyfin,
        val serverRepository: ServerRepository,
        val serverDao: JellyfinServerDao,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        val servers = MutableLiveData<List<JellyfinServer>>(listOf())
        val serverStatus = MutableLiveData<Map<UUID, ServerConnectionStatus>>(mapOf())
        val serverQuickConnect = MutableLiveData<Map<UUID, Boolean>>(mapOf())

        val users = MutableLiveData<List<JellyfinUser>>(listOf())
        val quickConnectState = MutableLiveData<QuickConnectResult?>(null)

        private var quickConnectJob: Job? = null

        val discoveredServers = MutableLiveData<List<JellyfinServer>>(listOf())

        val addServerState = MutableLiveData<LoadingState>(LoadingState.Pending)
        val switchUserState = MutableLiveData<LoadingState>(LoadingState.Pending)

        fun clearAddServerState() {
            addServerState.value = LoadingState.Pending
        }

        fun clearSwitchUserState() {
            switchUserState.value = LoadingState.Pending
        }

        init {
            init()
        }

        fun init() {
            viewModelScope.launchIO {
                val allServers =
                    serverDao
                        .getServers()
                        .map { it.server }
                        .sortedWith(compareBy<JellyfinServer> { it.name }.thenBy { it.url })
                withContext(Dispatchers.Main) {
                    servers.value = allServers
                }
                allServers.forEach { server ->
                    try {
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
                        val quickConnect by
                            jellyfin
                                .createApi(server.url)
                                .quickConnectApi
                                .getQuickConnectEnabled()
                        withContext(Dispatchers.Main) {
                            serverStatus.value =
                                serverStatus.value!!.toMutableMap().apply {
                                    put(
                                        server.id,
                                        ServerConnectionStatus.Success,
                                    )
                                }
                            serverQuickConnect.value =
                                serverQuickConnect.value!!.toMutableMap().apply {
                                    put(server.id, quickConnect)
                                }
                        }
                    } catch (ex: Exception) {
                        Timber.w(ex, "Error checking quick connect for server ${server.url}")
                        withContext(Dispatchers.Main) {
                            serverStatus.value =
                                serverStatus.value!!.toMutableMap().apply {
                                    put(
                                        server.id,
                                        ServerConnectionStatus.Error(ex.localizedMessage),
                                    )
                                }
                        }
                    }
                }
            }
            viewModelScope.launchIO {
                serverRepository.currentServer?.let {
                    val quickConnect =
                        jellyfin
                            .createApi(it.url)
                            .quickConnectApi
                            .getQuickConnectEnabled()
                            .content
                    val serverUsers = serverDao.getServer(it.id)?.users?.sortedBy { it.name } ?: listOf()
                    withContext(Dispatchers.Main) {
                        serverQuickConnect.value =
                            serverQuickConnect.value!!.toMutableMap().apply {
                                put(it.id, quickConnect)
                            }
                        users.value = serverUsers
                    }
                }
            }
        }

        fun switchUser(
            server: JellyfinServer,
            user: JellyfinUser,
        ) {
            viewModelScope.launchIO {
                try {
                    serverRepository.changeUser(server, user)
                    withContext(Dispatchers.Main) {
                        navigationManager.goToHome()
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error switching user")
                    setError("Error switching user", ex)
                }
            }
        }

        fun login(
            server: JellyfinServer,
            username: String,
            password: String,
        ) {
            quickConnectJob?.cancel()
            viewModelScope.launchIO {
                try {
                    val api = jellyfin.createApi(baseUrl = server.url)
                    val authenticationResult by api.userApi.authenticateUserByName(
                        username = username,
                        password = password,
                    )
                    serverRepository.changeUser(server.url, authenticationResult)
                    withContext(Dispatchers.Main) {
                        navigationManager.goToHome()
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error logging in user")
                    if (ex is InvalidStatusException && ex.status == 401) {
                        withContext(Dispatchers.Main) {
                            switchUserState.value =
                                LoadingState.Error("Invalid username or password")
                        }
                    } else {
                        setError("Error during login", ex)
                    }
                }
            }
        }

        fun initiateQuickConnect(server: JellyfinServer) {
            quickConnectJob?.cancel()
            viewModelScope.launchIO {
                try {
                    val api = jellyfin.createApi(server.url)
                    var state =
                        api
                            .quickConnectApi
                            .initiateQuickConnect()
                            .content
                    withContext(Dispatchers.Main) {
                        quickConnectState.value = state
                    }

                    quickConnectJob =
                        viewModelScope.launchIO {
                            while (!state.authenticated) {
                                delay(5_000L)
                                state =
                                    api.quickConnectApi
                                        .getQuickConnectState(
                                            secret = state.secret,
                                        ).content
                                withContext(Dispatchers.Main) {
                                    quickConnectState.value = state
                                }
                            }
                            val authenticationResult by api.userApi.authenticateWithQuickConnect(
                                QuickConnectDto(secret = state.secret),
                            )
                            serverRepository.changeUser(server.url, authenticationResult)
                            withContext(Dispatchers.Main) {
                                navigationManager.goToHome()
                            }
                        }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error during quick connect")
                    if (ex is InvalidStatusException && ex.status == 401) {
                        withContext(Dispatchers.Main) {
                            quickConnectState.value = null
                            serverQuickConnect.value =
                                serverQuickConnect.value!!.toMutableMap().apply {
                                    put(server.id, false)
                                }
                        }
                    }
                    setError("Error with Quick Connect", ex)
                }
            }
        }

        fun cancelQuickConnect() {
            quickConnectJob?.cancel()
            quickConnectState.value = null
        }

        fun addServer(serverUrl: String) {
            addServerState.value = LoadingState.Loading
            viewModelScope.launchIO {
                try {
                    val serverInfo by jellyfin
                        .createApi(serverUrl)
                        .systemApi
                        .getPublicSystemInfo()
                    val id = serverInfo.id?.toUUIDOrNull()
                    if (id != null && serverInfo.startupWizardCompleted == true) {
                        serverRepository.addAndChangeServer(
                            JellyfinServer(
                                id = id,
                                name = serverInfo.serverName,
                                url = serverUrl,
                            ),
                        )
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
                            navigationManager.navigateTo(Destination.UserList)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            addServerState.value =
                                LoadingState.Error("Server returned invalid response")
                        }
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Error creating API for $serverUrl")
                    withContext(Dispatchers.Main) {
                        addServerState.value =
                            LoadingState.Error(exception = ex)
                    }
                }
            }
        }

        fun removeUser(user: JellyfinUser) {
            viewModelScope.launchIO {
                serverRepository.removeUser(user)
                val serverUsers =
                    serverDao.getServer(user.serverId)?.users?.sortedBy { it.name } ?: listOf()
                withContext(Dispatchers.Main) {
                    users.value = serverUsers
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
                                    ),
                                )
                            }
                    withContext(Dispatchers.Main) {
                        discoveredServers.value = newServerList
                    }
                }
            }
        }

        private suspend fun setError(
            msg: String? = null,
            ex: Exception? = null,
        ) = withContext(Dispatchers.Main) {
            switchUserState.value = LoadingState.Error(msg, ex)
        }
    }
