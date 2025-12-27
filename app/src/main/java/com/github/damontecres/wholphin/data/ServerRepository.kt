package com.github.damontecres.wholphin.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.util.EqualityMutableLiveData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles managing the current server & user as well as adding & removing new ones
 */
@Singleton
class ServerRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val jellyfin: Jellyfin,
        val serverDao: JellyfinServerDao,
        val apiClient: ApiClient,
        val userPreferencesDataStore: DataStore<AppPreferences>,
    ) {
        private val sharedPreferences = getServerSharedPreferences(context)

        private var _current = EqualityMutableLiveData<CurrentUser?>(null)
        val current: LiveData<CurrentUser?> = _current

        private var _currentUserDto = EqualityMutableLiveData<UserDto?>(null)
        val currentUserDto: LiveData<UserDto?> = _currentUserDto

        val currentServer: LiveData<JellyfinServer?> get() = _current.map { it?.server }
        val currentUser: LiveData<JellyfinUser?> get() = _current.map { it?.user }

        /**
         * Adds a server to the app database and updated the [ApiClient] to the server's URL
         *
         * The current user is removed
         */
        suspend fun addAndChangeServer(server: JellyfinServer) {
            withContext(Dispatchers.IO) {
                serverDao.addOrUpdateServer(server)
            }
            apiClient.update(baseUrl = server.url, accessToken = null)
            _current.setValueOnMain(null)
        }

        /**
         * Saves the server & User to the app database and updates the [ApiClient] to use this server & user
         */
        suspend fun changeUser(
            server: JellyfinServer,
            user: JellyfinUser,
        ): CurrentUser? =
            withContext(Dispatchers.IO) {
                if (server.id != user.serverId) {
                    throw IllegalStateException("User is not part of the server")
                }
                Timber.v("Changing user to ${user.name} on ${server.url}")
                apiClient.update(baseUrl = server.url, accessToken = user.accessToken)
                val userDto by apiClient.userApi.getCurrentUser()
                val updatedServer =
                    try {
                        val sysInfo by apiClient.systemApi.getPublicSystemInfo()
                        server.copy(name = sysInfo.serverName, version = sysInfo.version)
                    } catch (ex: Exception) {
                        Timber.w(ex, "Exception fetching public system info")
                        server
                    }
                var updatedUser =
                    user.copy(
                        id = userDto.id,
                        name = userDto.name,
                    )
                serverDao.addOrUpdateServer(updatedServer)
                updatedUser = serverDao.addOrUpdateUser(updatedUser)
                userPreferencesDataStore.updateData {
                    it
                        .toBuilder()
                        .apply {
                            currentServerId = updatedServer.id.toServerString()
                            currentUserId = updatedUser.id.toServerString()
                        }.build()
                }
                withContext(Dispatchers.Main) {
                    _current.value = CurrentUser(updatedServer, updatedUser)
                    _currentUserDto.value = userDto
                }
                sharedPreferences.edit(true) {
                    putString(SERVER_URL_KEY, updatedServer.url)
                    putString(ACCESS_TOKEN_KEY, updatedUser.accessToken)
                }
                return@withContext _current.value
            }

        /**
         * Restores a session for the given server & user such as when the app reopens
         *
         * If user has a PIN, this returns false
         */
        suspend fun restoreSession(
            serverId: UUID?,
            userId: UUID?,
        ): CurrentUser? {
            if (serverId == null || userId == null) {
                _current.setValueOnMain(null)
                return null
            }
            val serverAndUsers =
                withContext(Dispatchers.IO) {
                    serverDao.getServer(serverId)
                }
            if (serverAndUsers != null) {
                val current = _current.value
                if (current != null && current.server.id == serverId && current.user.id == userId) {
                    Timber.v("Restoring session for current user, so shortcut")
                    apiClient.update(
                        baseUrl = current.server.url,
                        accessToken = current.user.accessToken,
                    )
                    return current
                } else {
                    val user = serverAndUsers.users.firstOrNull { it.id == userId }
                    if (user != null) {
                        // TODO pin-related
//                if (user != null && !user.hasPin) {
                        return changeUser(serverAndUsers.server, user)
                    }
                }
            }
            return null
        }

        suspend fun fetchLastUsedServer(serverId: UUID?): JellyfinServer? =
            withContext(Dispatchers.IO) {
                serverId?.let { serverDao.getServer(serverId)?.server }
            }

        fun closeSession() {
            _current.value = null
        }

        /**
         * Given a successful [AuthenticationResult], switch to the user that just authenticated
         */
        suspend fun changeUser(
            serverUrl: String,
            authenticationResult: AuthenticationResult,
        ) = withContext(Dispatchers.IO) {
            val accessToken = authenticationResult.accessToken
            if (accessToken != null) {
                val authedUser = authenticationResult.user
                val server =
                    authenticationResult.serverId?.toUUIDOrNull()?.let {
                        JellyfinServer(
                            id = it,
                            name = authedUser?.serverName,
                            url = serverUrl,
                            null,
                        )
                    }
                if (server != null) {
                    val user =
                        authedUser?.let {
                            JellyfinUser(
                                id = it.id,
                                name = it.name,
                                serverId = server.id,
                                accessToken = accessToken,
                            )
                        }
                    if (user != null) {
                        return@withContext changeUser(server, user)
                    } else {
                        throw IllegalArgumentException("Authentication result's user was null")
                    }
                } else {
                    throw IllegalArgumentException("Authentication result's serverId not valid: ${authenticationResult.serverId}")
                }
            } else {
                throw IllegalArgumentException("Authentication result's access token was null")
            }
        }

        suspend fun removeUser(user: JellyfinUser) {
            if (currentUser.value?.id == user.id) {
                withContext(Dispatchers.Main) {
                    _current.value = null
                }
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
            if (currentServer.value?.id == server.id) {
                withContext(Dispatchers.Main) {
                    _current.value = null
                }
                userPreferencesDataStore.updateData {
                    it
                        .toBuilder()
                        .apply {
                            currentServerId = ""
                            currentUserId = ""
                        }.build()
                }
                apiClient.update(baseUrl = null, accessToken = null)
            }
            withContext(Dispatchers.IO) {
                serverDao.deleteServer(server.id)
            }
        }

        suspend fun switchServerOrUser() {
            apiClient.update(baseUrl = null, accessToken = null)
            userPreferencesDataStore.updateData {
                it
                    .toBuilder()
                    .apply {
                        currentServerId = ""
                        currentUserId = ""
                    }.build()
            }
        }

        suspend fun setUserPin(
            user: JellyfinUser,
            pin: String?,
        ) = withContext(Dispatchers.IO) {
            val newUser = user.copy(pin = pin)
            val updatedUser = serverDao.addOrUpdateUser(newUser)
            if (currentUser.value?.id == updatedUser.id && currentServer.value?.id == user.serverId) {
                // Updating current user, so push out the change
                current.value?.let {
                    val newCurrent = it.copy(user = updatedUser)
                    _current.setValueOnMain(newCurrent)
                }
            }
        }

        companion object {
            fun getServerSharedPreferences(context: Context): SharedPreferences =
                context.getSharedPreferences(
                    "${context.packageName}_server",
                    Context.MODE_PRIVATE,
                )

            const val SERVER_URL_KEY = "current.server"
            const val ACCESS_TOKEN_KEY = "current.accessToken"
        }
    }

@Serializable
data class CurrentUser(
    val server: JellyfinServer,
    val user: JellyfinUser,
)
