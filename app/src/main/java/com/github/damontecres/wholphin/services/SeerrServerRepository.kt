package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.AuthJellyfinPostRequest
import com.github.damontecres.wholphin.api.seerr.model.AuthLocalPostRequest
import com.github.damontecres.wholphin.api.seerr.model.PublicSettings
import com.github.damontecres.wholphin.api.seerr.model.User
import com.github.damontecres.wholphin.data.SeerrServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrPermission
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
import com.github.damontecres.wholphin.data.model.hasPermission
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Manages saves/loading Seerr servers from the local DB. Also will update the current [SeerrApi] as needed.
 */
@Singleton
class SeerrServerRepository
    @Inject
    constructor(
        private val seerrApi: SeerrApi,
        private val seerrServerDao: SeerrServerDao,
        private val serverRepository: ServerRepository,
        @param:StandardOkHttpClient private val okHttpClient: OkHttpClient,
    ) {
        private val _current = MutableStateFlow<CurrentSeerr?>(null)
        val current: StateFlow<CurrentSeerr?> = _current
        val currentServer: Flow<SeerrServer?> = current.map { it?.server }
        val currentUser: Flow<SeerrUser?> = current.map { it?.user }

        /**
         * Whether Seerr integration is currently active of not
         */
        val active: Flow<Boolean> = current.map { it != null && seerrApi.active }

        fun clear() {
            _current.update { null }
            seerrApi.update("", null)
        }

        suspend fun set(
            server: SeerrServer,
            user: SeerrUser,
            userConfig: SeerrUserConfig,
        ) {
            val publicSettings = seerrApi.api.settingsApi.settingsPublicGet()
            _current.update {
                CurrentSeerr(server, user, userConfig, publicSettings)
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
                    val userConfig = seerrApi.api.usersApi.authMeGet()
                    set(server, user, userConfig)
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
                    val userConfig = login(seerrApi.api, authMethod, username, password)

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
                    set(server, user, userConfig)
                }
            }
        }

        suspend fun testConnection(
            authMethod: SeerrAuthMethod,
            url: String,
            username: String?,
            passwordOrApiKey: String,
        ): LoadingState {
            val apiKey = passwordOrApiKey.takeIf { authMethod == SeerrAuthMethod.API_KEY }
            val api =
                SeerrApiClient(
                    url,
                    apiKey,
                    okHttpClient
                        .newBuilder()
                        .connectTimeout(5.seconds)
                        .readTimeout(6.seconds)
                        .build(),
                )
            login(api, authMethod, username, passwordOrApiKey)
            return LoadingState.Success
        }

        suspend fun removeServer() {
            val current = _current.value ?: return
            seerrServerDao.deleteUser(current.server.id, current.user.jellyfinUserRowId)
            clear()
        }
    }

/**
 * A [SeerrUser] config
 */
typealias SeerrUserConfig = User

data class CurrentSeerr(
    val server: SeerrServer,
    val user: SeerrUser,
    val config: SeerrUserConfig,
    val serverConfig: PublicSettings,
) {
    val request4kMovieEnabled: Boolean
        get() =
            (serverConfig.movie4kEnabled ?: false) &&
                config.hasPermission(SeerrPermission.REQUEST_4K_MOVIE)

    val request4kTvEnabled: Boolean
        get() =
            (serverConfig.series4kEnabled ?: false) &&
                config.hasPermission(SeerrPermission.REQUEST_4K_TV)
}

private suspend fun login(
    client: SeerrApiClient,
    authMethod: SeerrAuthMethod,
    username: String?,
    password: String?,
): User =
    when (authMethod) {
        SeerrAuthMethod.LOCAL -> {
            // Establish the session cookie, then fetch the fully-populated user config.
            client.authApi.authLocalPost(
                AuthLocalPostRequest(
                    email = username ?: "",
                    password = password ?: "",
                ),
            )
            client.usersApi.authMeGet()
        }

        SeerrAuthMethod.JELLYFIN -> {
            // Establish the session cookie, then fetch the fully-populated user config.
            client.authApi.authJellyfinPost(
                AuthJellyfinPostRequest(
                    username = username ?: "",
                    password = password ?: "",
                ),
            )
            client.usersApi.authMeGet()
        }

        SeerrAuthMethod.API_KEY -> {
            client.usersApi.authMeGet()
        }
    }

/**
 * Listens for JF user switching in the app to also switch the Seerr user/server
 */
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
            context as AppCompatActivity
            context.lifecycleScope.launchIO {
                serverRepository.currentUser.asFlow().collect { user ->
                    Timber.d("New user")
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
                                    val userConfig =
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
                                        } else {
                                            try {
                                                seerrApi.api.usersApi.authMeGet()
                                            } catch (ex: Exception) {
                                                Timber.w(ex, "Error logging into %s", server.url)
                                                seerrServerRepository.clear()
                                                return@let
                                            }
                                        }
                                    seerrServerRepository.set(server, seerrUser, userConfig)
                                }
                            }
                    }
                }
            }
        }
    }
