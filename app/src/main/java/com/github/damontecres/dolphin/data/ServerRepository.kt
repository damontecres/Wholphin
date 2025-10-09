package com.github.damontecres.dolphin.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import com.github.damontecres.dolphin.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.UserDto
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepository
    @Inject
    constructor(
        val jellyfin: Jellyfin,
        val serverDao: JellyfinServerDao,
        val apiClient: ApiClient,
        val userPreferencesDataStore: DataStore<AppPreferences>,
    ) {
        private var _currentServer by mutableStateOf<JellyfinServer?>(null)
        val currentServer get() = _currentServer
        private var _currentUser by mutableStateOf<JellyfinUser?>(null)
        val currentUser get() = _currentUser
        private var _currentUserDto by mutableStateOf<UserDto?>(null)
        val currentUserDto get() = _currentUserDto

        suspend fun addAndChangeServer(server: JellyfinServer) {
            withContext(Dispatchers.IO) {
                serverDao.addServer(server)
            }
            changeServer(server)
        }

        fun changeServer(server: JellyfinServer) {
            apiClient.update(baseUrl = server.url, accessToken = null)
            _currentServer = server
        }

        suspend fun changeUser(
            server: JellyfinServer,
            user: JellyfinUser,
        ) = withContext(Dispatchers.IO) {
            try {
                if (server.id != user.serverId) {
                    throw IllegalStateException()
                }
                Timber.v("Changing user to ${user.name} on ${server.url}")
                apiClient.update(baseUrl = server.url, accessToken = user.accessToken)
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
                serverDao.addServer(updatedServer)
                serverDao.addUser(updatedUser)
                userPreferencesDataStore.updateData {
                    it
                        .toBuilder()
                        .apply {
                            currentServerId = updatedServer.id
                            currentUserId = updatedUser.id
                        }.build()
                }
                withContext(Dispatchers.Main) {
                    _currentUserDto = userDto
                    _currentServer = updatedServer
                    _currentUser = updatedUser
                }
            } catch (e: InvalidStatusException) {
                // TODO
                Timber.e(e)
                withContext(Dispatchers.Main) {
                    // Unauthorized
                    _currentServer = null
                    _currentUser = null
                }
                if (e.status == 401) {
                    return@withContext
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
                _currentServer = serverAndUsers.server
                val user = serverAndUsers.users.firstOrNull { it.id == userId }
                if (user != null) {
                    changeUser(serverAndUsers.server, user)
                    return true
                }
            }
            return false
        }

        suspend fun changeUser(
            serverUrl: String,
            authenticationResult: AuthenticationResult,
        ) = withContext(Dispatchers.IO) {
            val accessToken = authenticationResult.accessToken
            if (accessToken != null) {
                val authedUser = authenticationResult.user
                val server =
                    authenticationResult.serverId?.let {
                        JellyfinServer(
                            id = it,
                            name = authedUser?.serverName,
                            url = serverUrl,
                        )
                    }
                if (server != null) {
                    val user =
                        authedUser?.let {
                            JellyfinUser(
                                id = it.id.toString(),
                                name = it.name,
                                serverId = server.id,
                                accessToken = accessToken,
                            )
                        }
                    if (user != null) {
                        changeUser(server, user)
                    }
                }
            }
        }

        suspend fun removeUser(user: JellyfinUser) {
            if (currentUser == user) {
                _currentUser = null
                userPreferencesDataStore.updateData {
                    it
                        .toBuilder()
                        .apply {
                            currentUserId = ""
                        }.build()
                }
                apiClient.update(accessToken = null)
            }
            withContext(Dispatchers.IO) {
                serverDao.deleteUser(user.serverId, user.id)
            }
        }

        suspend fun removeServer(server: JellyfinServer) {
            if (currentServer == server) {
                _currentServer = null
                userPreferencesDataStore.updateData {
                    it
                        .toBuilder()
                        .apply {
                            currentServerId = ""
                        }.build()
                }
            }
            withContext(Dispatchers.IO) {
                serverDao.deleteServer(server.id)
            }
        }
    }
