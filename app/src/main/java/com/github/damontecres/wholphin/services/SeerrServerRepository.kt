package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.infrastructure.ClientException
import com.github.damontecres.wholphin.api.seerr.model.AuthJellyfinPostRequest
import com.github.damontecres.wholphin.api.seerr.model.AuthLocalPostRequest
import com.github.damontecres.wholphin.api.seerr.model.PublicSettings
import com.github.damontecres.wholphin.api.seerr.model.User
import com.github.damontecres.wholphin.data.SeerrServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrPermission
import com.github.damontecres.wholphin.data.model.SeerrPluginLogin
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
import com.github.damontecres.wholphin.data.model.hasPermission
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.setup.seerr.createSeerrApiUrl
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import org.jellyfin.sdk.model.api.ImageType
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
        private val serverPluginApi: ServerPluginApi,
        @param:StandardOkHttpClient private val okHttpClient: OkHttpClient,
    ) {
        private val _connection =
            MutableStateFlow<SeerrConnectionStatus>(SeerrConnectionStatus.NotConfigured)
        val connection: StateFlow<SeerrConnectionStatus> = _connection

        val current: Flow<CurrentSeerr?> =
            _connection.map { (it as? SeerrConnectionStatus.Success)?.current }
        val currentServer: Flow<SeerrServer?> =
            connection.map { (it as? SeerrConnectionStatus.Success)?.current?.server }
        val currentUser: Flow<SeerrUser?> =
            connection.map { (it as? SeerrConnectionStatus.Success)?.current?.user }
        val currentUserId: Flow<Int?> = current.map { it?.config?.id }

        private data class SetupTarget(
            val server: SeerrServer,
            val jellyfinUser: JellyfinUser,
        ) {
            fun seerrUser(
                authMethod: SeerrAuthMethod,
                username: String? = null,
                password: String? = null,
                credential: String? = null,
            ) = SeerrUser(
                jellyfinUserRowId = jellyfinUser.rowId,
                serverId = server.id,
                authMethod = authMethod,
                username = username,
                password = password,
                credential = credential,
            )
        }

        /**
         * Whether Seerr integration is currently active of not
         */
        val active: Flow<Boolean> =
            connection.map { it is SeerrConnectionStatus.Success && seerrApi.active }

        fun clear() {
            _connection.update { SeerrConnectionStatus.NotConfigured }
            seerrApi.update("", null)
        }

        fun error(
            server: SeerrServer,
            user: SeerrUser,
            exception: Exception,
        ) {
            _connection.update { SeerrConnectionStatus.Error(server, user, exception) }
            seerrApi.update("", null)
        }

        suspend fun set(
            server: SeerrServer,
            user: SeerrUser,
            userConfig: SeerrUserConfig,
        ) {
            val publicSettings = seerrApi.api.settingsApi.settingsPublicGet()
            _connection.update {
                SeerrConnectionStatus.Success(
                    CurrentSeerr(server, user, userConfig, publicSettings),
                )
            }
        }

        suspend fun addAndChangeServer(
            url: String,
            apiKey: String,
        ) {
            val target = ensureServerAndCurrentUser(url) ?: return
            seerrApi.update(target.server.url, apiKey)
            val userConfig = seerrApi.api.usersApi.authMeGet()
            val user = target.seerrUser(SeerrAuthMethod.API_KEY, credential = apiKey)
            seerrServerDao.addUser(user)
            set(target.server, user, userConfig)
        }

        private suspend fun ensureServer(url: String): SeerrServer? =
            seerrServerDao.getServer(url)?.server
                ?: run {
                    seerrServerDao.addServer(SeerrServer(url = url))
                    seerrServerDao.getServer(url)?.server
                }

        private suspend fun ensureServerAndCurrentUser(url: String): SetupTarget? {
            val server =
                ensureServer(url)
                    ?: return null
            val jellyfinUser = serverRepository.currentUser ?: return null
            return SetupTarget(server, jellyfinUser)
        }

        private suspend fun findExistingForCurrentJellyfinUser(): SeerrServer? {
            val jellyfinUser = serverRepository.currentUser ?: return null
            val seerrUser =
                seerrServerDao
                    .getUsersByJellyfinUser(jellyfinUser.rowId)
                    .lastOrNull() ?: return null
            return seerrServerDao.getServer(seerrUser.serverId)?.server
        }

        private suspend fun prefillFromPlugin(url: String) {
            ensureServer(url)
        }

        suspend fun findPrefillServerUrlForCurrentJellyfinUser(): String =
            findExistingForCurrentJellyfinUser()?.url
                ?: fetchPluginSeerrSettings()?.serverUrl?.takeIf { it.isNotNullOrBlank() }?.also { prefillFromPlugin(it) }
                ?: seerrServerDao
                    .getServers()
                    .lastOrNull()
                    ?.server
                    ?.url
                ?: ""

        suspend fun restoreOrAutoSetupForCurrentUser(user: JellyfinUser) {
            val existing =
                seerrServerDao
                    .getUsersByJellyfinUser(user.rowId)
                    .lastOrNull()
                    ?: return tryAutoSetupFromPlugin()
            val server = seerrServerDao.getServer(existing.serverId)?.server ?: return
            try {
                seerrApi.update(server.url, existing.credential)
                val userConfig =
                    if (existing.authMethod == SeerrAuthMethod.API_KEY) {
                        seerrApi.api.usersApi.authMeGet()
                    } else {
                        seerrLogin(
                            seerrApi.api,
                            existing.authMethod,
                            existing.username,
                            existing.password,
                        )
                    }
                set(server, existing, userConfig)
            } catch (ex: Exception) {
                Timber.w(ex, "Seerr login to %s failed - credentials kept, will retry on next start", server.url)
                error(server, existing, ex)
            }
        }

        private suspend fun fetchPluginSeerrSettings() =
            try {
                serverPluginApi.fetchSeerrSettings()
            } catch (ex: Exception) {
                Timber.w(ex, "Failed to fetch seerr settings from server plugin")
                null
            }

        private suspend fun tryAutoSetupFromPlugin() {
            val settings = fetchPluginSeerrSettings() ?: return
            val url = settings.serverUrl
            if (!url.isNotNullOrBlank()) return
            try {
                when (val login = settings.login ?: SeerrPluginLogin.None) {
                    is SeerrPluginLogin.ApiKey -> {
                        Timber.i("Auto-setup Seerr via API key from plugin")
                        addAndChangeServer(url, login.apiKey)
                    }

                    is SeerrPluginLogin.Local -> {
                        Timber.i("Auto-setup Seerr via local login from plugin")
                        addAndChangeServer(
                            url,
                            SeerrAuthMethod.LOCAL,
                            login.local.username,
                            login.local.password,
                        )
                    }

                    SeerrPluginLogin.None -> {
                        Timber.i("Pre-filling Seerr URL from plugin")
                        prefillFromPlugin(url)
                    }
                }
            } catch (ex: ClientException) {
                prefillFromPlugin(url)
                Timber.w(
                    ex,
                    "Seerr auto-setup from plugin failed for %s with HTTP %s. Check plugin Seerr credentials and auth type.",
                    url,
                    ex.statusCode,
                )
            } catch (ex: Exception) {
                prefillFromPlugin(url)
                Timber.w(ex, "Seerr auto-setup from plugin failed for %s", url)
            }
        }

        suspend fun addAndChangeServer(
            url: String,
            authMethod: SeerrAuthMethod,
            username: String,
            password: String,
        ) {
            val target = ensureServerAndCurrentUser(url) ?: return

            // TODO Need to update server early so that cookies are saved
            seerrApi.update(target.server.url, null)
            val userConfig = seerrLogin(seerrApi.api, authMethod, username, password)
            val user = target.seerrUser(authMethod, username, password)
            seerrServerDao.addUser(user)
            set(target.server, user, userConfig)
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
                    createSeerrApiUrl(url),
                    apiKey,
                    okHttpClient
                        .newBuilder()
                        .connectTimeout(2.seconds)
                        .readTimeout(6.seconds)
                        .build(),
                )
            seerrLogin(api, authMethod, username, passwordOrApiKey)
            return LoadingState.Success
        }

        suspend fun removeServerForCurrentUser(): Boolean {
            val user =
                when (val conn = connection.first()) {
                    SeerrConnectionStatus.NotConfigured -> return false
                    is SeerrConnectionStatus.Error -> conn.user
                    is SeerrConnectionStatus.Success -> conn.current.user
                }
            val rows = seerrServerDao.deleteUser(user)
            clear()
            return rows > 0
        }
    }

/**
 * A [SeerrUser] config
 */
typealias SeerrUserConfig = User

sealed interface SeerrConnectionStatus {
    data object NotConfigured : SeerrConnectionStatus

    data class Error(
        val server: SeerrServer,
        val user: SeerrUser,
        val ex: Exception,
    ) : SeerrConnectionStatus

    data class Success(
        val current: CurrentSeerr,
    ) : SeerrConnectionStatus
}

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

suspend fun seerrLogin(
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
            client.usersApi.authMeGet()
        }

        SeerrAuthMethod.JELLYFIN -> {
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

fun CurrentSeerr?.imageUrlBuilder(
    imageType: ImageType,
    path: String?,
): String? {
    if (this == null) return null
    val cacheImages = serverConfig.cacheImages == true
    val base =
        if (cacheImages) {
            server.url.removeSuffix("/") + "/imageproxy/tmdb"
        } else {
            "https://image.tmdb.org"
        }
    val prefix =
        when (imageType) {
            ImageType.PRIMARY -> "/t/p/w500"
            ImageType.BACKDROP -> "/t/p/w1920_and_h1080_multi_faces"
            else -> throw IllegalArgumentException("Image type not supported: $imageType")
        }
    return "${base}${prefix}$path"
}
