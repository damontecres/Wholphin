package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.model.AuthJellyfinPostRequest
import com.github.damontecres.wholphin.api.seerr.model.AuthLocalPostRequest
import com.github.damontecres.wholphin.data.SeerrServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeerrServerRepository
    @Inject
    constructor(
        private val seerrApi: SeerrApi,
        private val seerrServerDao: SeerrServerDao,
        private val serverRepository: ServerRepository,
    ) {
        private val _currentServer = MutableStateFlow<SeerrServer?>(null)
        val currentServer: StateFlow<SeerrServer?> = _currentServer

        private val _currentUser = MutableStateFlow<SeerrUser?>(null)
        val currentUser: StateFlow<SeerrUser?> = _currentUser

        val current =
            currentServer.combine(currentUser) { server, user ->
                if (server != null && user != null) {
                    CurrentSeerr(server, user)
                } else {
                    null
                }
            }

        suspend fun addAndChangeServer(
            url: String,
            apiKey: String,
        ) {
            var server = seerrServerDao.getServer(url)
            if (server == null) {
                seerrServerDao.addServer(SeerrServer(url = url))
                server = seerrServerDao.getServer(url)
            }
            server?.server?.let { server ->
                serverRepository.currentUser.value?.let { jellyfinUser ->
                    // TODO test api key
                    val user =
                        SeerrUser(
                            jellyfinRowId = jellyfinUser.rowId,
                            serverId = server.id,
                            authMethod = SeerrAuthMethod.API_KEY,
                            username = null,
                            password = null,
                            credential = apiKey,
                        )
                    seerrServerDao.addUser(user)
                    // TODO user info
                    seerrApi.api.usersApi.authMeGet()

                    seerrApi.update(server.url, apiKey)
                    _currentServer.update {
                        _currentUser.update {
                            user
                        }
                        server
                    }
                }
            }
        }

        suspend fun addAndChangeServer(
            url: String,
            authMethod: SeerrAuthMethod,
            username: String,
            password: String,
        ) {
            var server = seerrServerDao.getServer(url)
            if (server == null) {
                seerrServerDao.addServer(SeerrServer(url = url))
                server = seerrServerDao.getServer(url)
            }
            server?.server?.let { server ->
                serverRepository.currentUser.value?.let { jellyfinUser ->
                    val response =
                        when (authMethod) {
                            SeerrAuthMethod.LOCAL -> {
                                seerrApi.api.authApi.authLocalPostWithHttpInfo(
                                    AuthLocalPostRequest(
                                        email = username,
                                        password = password,
                                    ),
                                )
                            }

                            SeerrAuthMethod.JELLYFIN -> {
                                seerrApi.api.authApi.authJellyfinPostWithHttpInfo(
                                    AuthJellyfinPostRequest(
                                        username = username,
                                        password = password,
                                    ),
                                )
                            }

                            SeerrAuthMethod.API_KEY -> {
                                throw IllegalArgumentException("Cannot authenticate with API key using this function")
                            }
                        }
                    response.headers["Cookies"]
                    // TODO get session cookie
                    val user =
                        SeerrUser(
                            jellyfinRowId = jellyfinUser.rowId,
                            serverId = server.id,
                            authMethod = SeerrAuthMethod.API_KEY,
                            username = username,
                            password = password,
                            credential = null,
                        )
                }
            }
        }
    }

data class CurrentSeerr(
    val server: SeerrServer,
    val user: SeerrUser,
)
