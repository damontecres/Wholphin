package com.github.damontecres.wholphin.data

import android.content.Context
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.hilt.IoDispatcher
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.time.ZonedDateTime
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
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val mostRecentServerProvider: MostRecentServerProvider,
    ) {
        private var _current = MutableStateFlow<CurrentUser?>(null)
        val current: StateFlow<CurrentUser?> = _current

        private var _currentUserDto = MutableStateFlow<UserDto?>(null)
        val currentUserDto: UserDto? get() = _currentUserDto.value
        val currentUserDtoFlow: StateFlow<UserDto?> get() = _currentUserDto

        val currentServer: JellyfinServer? get() = _current.value?.server
        val currentServerFlow: Flow<JellyfinServer?> get() = _current.map { it?.server }
        val currentUser: JellyfinUser? get() = _current.value?.user

        @OptIn(ExperimentalCoroutinesApi::class)
        val currentUserFlow: Flow<JellyfinUser?>
            get() =
                _current
                    .flatMapLatest { user ->
                        if (user?.user != null) {
                            serverDao.getUserFlow(user.user.serverId, user.user.id)
                        } else {
                            flow { emit(null) }
                        }
                    }

        /**
         * Adds a server to the app database and updated the [ApiClient] to the server's URL
         *
         * The current user is removed
         */
        suspend fun addAndChangeServer(server: JellyfinServer) {
            withContext(ioDispatcher) {
                serverDao.addOrUpdateServer(server)
            }
            apiClient.update(baseUrl = server.url, accessToken = null)
            _current.value = null
            mostRecentServerProvider.clearUser()
        }

        /**
         * Saves the server & user to the app database and updates the [ApiClient] to use this server & user
         */
        suspend fun changeUser(
            server: JellyfinServer,
            user: JellyfinUser,
        ): CurrentUser =
            withContext(ioDispatcher) {
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

                val currentUser = CurrentUser(updatedServer, updatedUser)
                mostRecentServerProvider.save(currentUser)
                _current.value = currentUser
                _currentUserDto.value = userDto
                return@withContext currentUser
            }

        /**
         * Try to restore the most recent session
         *
         * Does not check if auto sign-in is enabled, but does check if the user profile is protected
         */
        suspend fun restoreLastSession(): RestoredSession {
            val recentServer = mostRecentServerProvider.get()
            Timber.d("restoreLastSession: recentServer=%s", recentServer)
            val result =
                when (recentServer) {
                    MostRecentServer.None -> {
                        RestoredSession.None
                    }

                    is MostRecentServer.Server -> {
                        val server = serverDao.getServer(recentServer.serverId)
                        if (server != null) {
                            RestoredSession.ServerOnly(server.server)
                        } else {
                            RestoredSession.None
                        }
                    }

                    is MostRecentServer.ServerAndUser -> {
                        val currentUser = tryChangeUser(recentServer.serverId, recentServer.userId)
                        if (currentUser != null) {
                            if (currentUser.user.isProtected) {
                                RestoredSession.ServerOnly(currentUser.server)
                            } else {
                                RestoredSession.Success(currentUser)
                            }
                        } else {
                            getMostRecentServerInternal(recentServer)
                        }
                    }
                }
            return result
        }

        /**
         * Get the most recently used server, if any
         */
        suspend fun getMostRecentServer(): RestoredSession = getMostRecentServerInternal(mostRecentServerProvider.get())

        suspend fun getMostRecentServerInternal(recentServer: MostRecentServer): RestoredSession {
            val recentServer = recentServer ?: mostRecentServerProvider.get()
            val serverId =
                when (recentServer) {
                    MostRecentServer.None -> null
                    is MostRecentServer.Server -> recentServer.serverId
                    is MostRecentServer.ServerAndUser -> recentServer.serverId
                }
            val server = serverId?.let { serverDao.getServer(serverId)?.server }
            return if (server != null) {
                RestoredSession.ServerOnly(server)
            } else {
                RestoredSession.None
            }
        }

        /**
         * Try to change to the given server & user. If the user's profile is protected, returns null
         *
         * @return the server/user or null if the session could not be restored
         */
        suspend fun tryChangeUser(
            serverId: UUID?,
            userId: UUID?,
        ): CurrentUser? =
            withContext(ioDispatcher) {
                return@withContext if (serverId == null || userId == null) {
                    _current.value = null
                    null
                } else {
                    val serverAndUsers = serverDao.getServer(serverId)
                    if (serverAndUsers != null) {
                        val user = serverAndUsers.users.firstOrNull { it.id == userId }
                        if (user != null && !user.isProtected) {
                            changeUser(serverAndUsers.server, user)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            }

        /**
         * Given a successful [AuthenticationResult], switch to the user that just authenticated
         */
        suspend fun changeUser(
            serverUrl: String,
            authenticationResult: AuthenticationResult,
            existingUser: JellyfinUser?,
        ) = withContext(ioDispatcher) {
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
                            if (existingUser != null) {
                                Timber.d("Re-using existing user")
                                existingUser.copy(
                                    // If the server authenticated via the server, always remove the PIN
                                    pin = null,
                                    accessToken = accessToken,
                                    lastUsed = ZonedDateTime.now(),
                                )
                            } else {
                                Timber.d("Creating new user")
                                JellyfinUser(
                                    id = it.id,
                                    name = it.name,
                                    serverId = server.id,
                                    accessToken = accessToken,
                                    lastUsed = ZonedDateTime.now(),
                                )
                            }
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
            if (current.value?.user?.id == user.id) {
                withContext(WholphinDispatchers.Main) {
                    _current.value = null
                }
                mostRecentServerProvider.clearUser()
                apiClient.update(accessToken = null)
            }
            withContext(ioDispatcher) {
                serverDao.deleteUser(user.serverId, user.id)
            }
        }

        suspend fun removeServer(server: JellyfinServer) {
            if (current.value?.server?.id == server.id) {
                withContext(WholphinDispatchers.Main) {
                    _current.value = null
                }
                mostRecentServerProvider.clear()
                apiClient.update(baseUrl = null, accessToken = null)
            }
            withContext(ioDispatcher) {
                serverDao.deleteServer(server.id)
            }
        }

        suspend fun switchServerOrUser() {
            mostRecentServerProvider.clear()
        }

        suspend fun updateUserAuth(
            user: JellyfinUser,
            pin: String?,
            requireLogin: Boolean,
        ) {
            val newUser = user.copy(pin = pin, requireLogin = requireLogin)
            val updatedUser = serverDao.addOrUpdateUser(newUser)
            val cur = current.value
            if (cur?.user?.id == updatedUser.id && cur.server?.id == user.serverId) {
                // Updating current user, so push out the change
                current.value?.let {
                    val newCurrent = it.copy(user = updatedUser)
                    _current.value = newCurrent
                }
            }
        }

        suspend fun authorizeQuickConnect(code: String): Boolean =
            withContext(ioDispatcher) {
                val userId = current.value?.user?.id
                if (userId == null) {
                    Timber.e("No user logged in for Quick Connect authorization")
                    throw IllegalStateException("Must be logged in to authorize Quick Connect")
                }
                val response = apiClient.quickConnectApi.authorizeQuickConnect(code, userId)
                response.content
            }

        /**
         * Update [currentUserDto] by querying the server
         */
        suspend fun updateUserDto() {
            val userDto by apiClient.userApi.getCurrentUser()
            _currentUserDto.update {
                if (it?.id == userDto.id && currentUser?.id == userDto.id) userDto else it
            }
        }
    }

@Serializable
data class CurrentUser(
    val server: JellyfinServer,
    val user: JellyfinUser,
)
