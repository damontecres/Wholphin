package com.github.damontecres.dolphin.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.userApi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepository
    @Inject
    constructor(
        val serverDao: JellyfinServerDao,
        val apiClient: ApiClient,
    ) {
        private var _currentServer by mutableStateOf<JellyfinServer?>(null)
        val currentServer get() = _currentServer
        private var _currentUser by mutableStateOf<JellyfinUser?>(null)
        val currentUser get() = _currentUser

        suspend fun addAndChangeServer(server: JellyfinServer) {
            withContext(Dispatchers.IO) {
                serverDao.addServer(server)
            }
            changeServer(server)
        }

        suspend fun addAndChangeUser(
            server: JellyfinServer,
            user: JellyfinUser,
        ) {
            if (server.id != user.serverId) {
                throw IllegalStateException()
            }
            withContext(Dispatchers.IO) {
                serverDao.addServer(server)
                serverDao.addUser(user)
            }
            changeUser(server, user)
        }

        fun changeServer(server: JellyfinServer) {
            apiClient.update(baseUrl = server.url, accessToken = null)
            _currentServer = server
        }

        suspend fun changeUser(
            server: JellyfinServer,
            user: JellyfinUser,
        ) {
            Timber.v("Changing user to ${user.name} on ${server.url}")
            apiClient.update(baseUrl = server.url, accessToken = user.accessToken)
            try {
                val userDto =
                    apiClient.userApi
                        .getCurrentUser()
                        .content
                val updatedServer = server.copy(name = userDto.serverName)
                val updatedUser =
                    user.copy(
                        id = userDto.id.toString(),
                        name = userDto.name,
                    )
                withContext(Dispatchers.IO) {
                    serverDao.addServer(updatedServer)
                    serverDao.addUser(updatedUser)
                }
                _currentServer = updatedServer
                _currentUser = updatedUser
            } catch (e: InvalidStatusException) {
                // TODO
                Timber.e(e)
                if (e.status == 401) {
                    // Unauthorized
                    _currentServer = null
                    _currentUser = null
                    return
                }
            }
        }

        suspend fun restoreSession(
            serverId: String,
            userId: String,
        ): Boolean {
            val serverAndUsers =
                withContext(Dispatchers.IO) {
                    serverDao.getServer(serverId)
                }
            if (serverAndUsers != null) {
                val user = serverAndUsers.users.firstOrNull { it.id == userId }
                if (user != null) {
                    changeUser(serverAndUsers.server, user)
                    return true
                }
            }
            return false
        }
    }
