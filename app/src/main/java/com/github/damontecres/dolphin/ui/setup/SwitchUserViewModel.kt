package com.github.damontecres.dolphin.ui.setup

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.dolphin.data.JellyfinServer
import com.github.damontecres.dolphin.data.JellyfinServerDao
import com.github.damontecres.dolphin.data.JellyfinUser
import com.github.damontecres.dolphin.data.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.api.QuickConnectResult
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SwitchUserViewModel
    @Inject
    constructor(
        val jellyfin: Jellyfin,
        val serverRepository: ServerRepository,
        val serverDao: JellyfinServerDao,
    ) : ViewModel() {
        val servers = MutableLiveData<List<JellyfinServer>>(listOf())
        val serverStatus = MutableLiveData<Map<String, ServerConnectionStatus>>(mapOf())

        val users = MutableLiveData<List<JellyfinUser>>(listOf())
        val quickConnectEnabled = MutableLiveData(false)
        val quickConnectState = MutableLiveData<QuickConnectResult?>(null)

        private var quickConnectJob: Job? = null

        init {
            viewModelScope.launch(Dispatchers.IO) {
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
                            .createApi(server.url)
                            .systemApi
                            .getPublicSystemInfo()
                        withContext(Dispatchers.Main) {
                            serverStatus.value =
                                serverStatus.value!!.toMutableMap().apply {
                                    put(
                                        server.id,
                                        ServerConnectionStatus.Success,
                                    )
                                }
                        }
                    } catch (ex: Exception) {
                        Timber.w(ex, "Error checking quick connect for server ${server.url}")
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
            viewModelScope.launch(Dispatchers.IO) {
                serverRepository.currentServer?.let {
                    val quickConnect =
                        jellyfin
                            .createApi(it.url)
                            .quickConnectApi
                            .getQuickConnectEnabled()
                            .content
                    val serverUsers = serverDao.getServer(it.id)?.users?.sortedBy { it.name } ?: listOf()
                    withContext(Dispatchers.Main) {
                        quickConnectEnabled.value = quickConnect
                        users.value = serverUsers
                    }
                }
            }
        }

        fun switchUser(
            server: JellyfinServer,
            user: JellyfinUser,
            callback: () -> Unit,
        ) {
            viewModelScope.launch {
                serverRepository.changeUser(server, user)
                callback.invoke()
            }
        }

        fun login(
            server: JellyfinServer,
            username: String,
            password: String,
            onAuthenticated: () -> Unit,
        ) {
            quickConnectJob?.cancel()
            viewModelScope.launch {
                try {
                    val api = jellyfin.createApi(baseUrl = server.url)
                    val authenticationResult by api.userApi.authenticateUserByName(
                        username = username,
                        password = password,
                    )
                    serverRepository.changeUser(server.url, authenticationResult)
                    withContext(Dispatchers.Main) {
                        onAuthenticated.invoke()
                    }
                } catch (err: InvalidStatusException) {
                    if (err.status == 401) {
                        // TODO set state to notify user
                    }
                }
            }
        }

        fun initiateQuickConnect(
            server: JellyfinServer,
            onAuthenticated: () -> Unit,
        ) {
            quickConnectJob?.cancel()
            viewModelScope.launch(Dispatchers.IO) {
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
                        viewModelScope.launch {
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
                                onAuthenticated.invoke()
                            }
                        }
                } catch (err: InvalidStatusException) {
                    if (err.status == 401) {
                        quickConnectState.value = null
                        quickConnectEnabled.value = false
                    }
                }
            }
        }

        fun cancelQuickConnect() {
            quickConnectJob?.cancel()
            quickConnectState.value = null
        }

        fun addServer(
            serverUrl: String,
            callback: () -> Unit,
        ) {
            viewModelScope.launch {
                try {
                    val serverInfo by jellyfin
                        .createApi(serverUrl)
                        .systemApi
                        .getPublicSystemInfo()
                    val id = serverInfo.id
                    if (id != null) {
                        serverRepository.addAndChangeServer(
                            JellyfinServer(
                                id = id,
                                name = serverInfo.serverName,
                                url = serverUrl,
                            ),
                        )
                        callback.invoke()
                    } else {
                        // TODO
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Error creating API for $serverUrl")
                    // TODO notify user
                }
            }
        }
    }
