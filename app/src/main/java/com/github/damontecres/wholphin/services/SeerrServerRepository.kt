package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.AuthJellyfinPostRequest
import com.github.damontecres.wholphin.api.seerr.model.AuthLocalPostRequest
import com.github.damontecres.wholphin.api.seerr.model.User
import com.github.damontecres.wholphin.data.SeerrServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeerrServerRepository
    @Inject
    constructor(
        private val seerrApi: SeerrApi,
        private val seerrServerDao: SeerrServerDao,
        private val serverRepository: ServerRepository,
        @param:StandardOkHttpClient private val okHttpClient: OkHttpClient,
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

        fun clear() {
            _currentServer.update { null }
            _currentUser.update { null }
            seerrApi.update("", null)
        }

        suspend fun set(
            server: SeerrServer,
            user: SeerrUser,
        ) {
            _currentServer.update {
                _currentUser.update {
                    user
                }
                server
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
                            jellyfinUserRowId = jellyfinUser.rowId,
                            serverId = server.id,
                            authMethod = SeerrAuthMethod.API_KEY,
                            username = null,
                            password = null,
                            credential = apiKey,
                        )
                    seerrServerDao.addUser(user)

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
                    // TODO Need to update server early so that cookies are saved
                    seerrApi.update(server.url, null)
                    login(seerrApi.api, authMethod, username, password)

                    val user =
                        SeerrUser(
                            jellyfinUserRowId = jellyfinUser.rowId,
                            serverId = server.id,
                            authMethod = authMethod,
                            username = username,
                            password = password,
                            credential = null,
                        )
                    seerrServerDao.addUser(user)
                    _currentServer.update {
                        _currentUser.update {
                            user
                        }
                        server
                    }
                }
            }
        }

        suspend fun testConnection(
            authMethod: SeerrAuthMethod,
            url: String,
            username: String?,
            passwordOrApiKey: String,
        ): LoadingState {
            val api = SeerrApiClient(url, passwordOrApiKey, okHttpClient)
            try {
                login(api, authMethod, username, passwordOrApiKey)
                return LoadingState.Success
            } catch (ex: Exception) {
                Timber.w(ex, "Error testing seerr connection")
                return LoadingState.Error(ex)
            }
        }
    }

data class CurrentSeerr(
    val server: SeerrServer,
    val user: SeerrUser,
)

private suspend fun login(
    client: SeerrApiClient,
    authMethod: SeerrAuthMethod,
    username: String?,
    password: String?,
): User =
    when (authMethod) {
        SeerrAuthMethod.LOCAL -> {
            client.authApi.authLocalPost(
                AuthLocalPostRequest(
                    email = username ?: "",
                    password = password ?: "",
                ),
            )
        }

        SeerrAuthMethod.JELLYFIN -> {
            client.authApi.authJellyfinPost(
                AuthJellyfinPostRequest(
                    username = username ?: "",
                    password = password ?: "",
                ),
            )
        }

        SeerrAuthMethod.API_KEY -> {
            client.usersApi.authMeGet()
        }
    }

@ActivityScoped
class UserSwitchListener
    @Inject
    constructor(
        @param:ActivityContext private val context: Context,
        private val serverRepository: ServerRepository,
        private val seerrServerRepository: SeerrServerRepository,
        private val seerrServerDao: SeerrServerDao,
        private val seerrApi: SeerrApi,
    ) {
        init {
            Timber.v("Launching")
            context as AppCompatActivity
            context.lifecycleScope.launchIO {
                serverRepository.currentUser.asFlow().collect { user ->
                    Timber.v("New user")
                    seerrServerRepository.clear()
                    if (user != null) {
                        seerrServerDao
                            .getUsersByJellyfinUser(user.rowId)
                            .firstOrNull()
                            ?.let { seerrUser ->
                                val server = seerrServerDao.getServer(seerrUser.serverId)?.server
                                if (server != null) {
                                    Timber.i("Found a seerr user & server")
                                    seerrApi.update(server.url, seerrUser.credential)
                                    if (seerrUser.authMethod != SeerrAuthMethod.API_KEY) {
                                        try {
                                            login(
                                                seerrApi.api,
                                                seerrUser.authMethod,
                                                seerrUser.username,
                                                seerrUser.password,
                                            )
                                        } catch (ex: Exception) {
                                            Timber.w(ex, "Error logging into %s", server.url)
                                            seerrServerRepository.clear()
                                            return@let
                                        }
                                    }
                                    seerrServerRepository.set(server, seerrUser)
                                }
                            }
                    }
                }
            }
        }
    }
