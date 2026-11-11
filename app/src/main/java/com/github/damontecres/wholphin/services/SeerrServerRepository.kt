package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.AuthJellyfinPostRequest
import com.github.damontecres.wholphin.api.seerr.model.AuthJellyfinQuickconnectAuthenticatePostRequest
import com.github.damontecres.wholphin.api.seerr.model.AuthLocalPostRequest
import com.github.damontecres.wholphin.api.seerr.model.PublicSettings
import com.github.damontecres.wholphin.api.seerr.model.User
import com.github.damontecres.wholphin.data.SeerrServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrPermission
import com.github.damontecres.wholphin.data.model.SeerrPluginLogin
import com.github.damontecres.wholphin.data.model.SeerrPluginSettings
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
import com.github.damontecres.wholphin.data.model.hasPermission
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.setup.seerr.createSeerrApiUrl
import com.github.damontecres.wholphin.ui.setup.seerr.migrateSeerrUrl
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
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
        private val _connection = MutableStateFlow<SeerrState>(SeerrState())
        val connection: StateFlow<SeerrState> = _connection

        val current: Flow<CurrentSeerr?> =
            _connection.map { (it.connection as? SeerrConnectionStatus.Success)?.current }
        val currentServer: Flow<SeerrServer?> =
            connection.map { (it.connection as? SeerrConnectionStatus.Success)?.current?.server }
        val currentUser: Flow<SeerrUser?> =
            connection.map { (it.connection as? SeerrConnectionStatus.Success)?.current?.user }
        val currentUserId: Flow<Int?> = current.map { it?.config?.id }

        /**
         * Whether Seerr integration is currently active of not
         */
        val active: Flow<Boolean> =
            connection.map { it.connection is SeerrConnectionStatus.Success && seerrApi.active }

        fun clear() {
            _connection.update { SeerrState() }
            seerrApi.update("", null)
        }

        fun error(
            server: SeerrServer,
            user: SeerrUser,
            exception: Exception,
        ) {
            _connection.update { it.copy(connection = SeerrConnectionStatus.Error(server, user, exception)) }
            seerrApi.update("", null)
        }

        suspend fun set(
            server: SeerrServer,
            user: SeerrUser,
            userConfig: SeerrUserConfig,
        ) {
            val publicSettings = seerrApi.api.settingsApi.settingsPublicGet()
            _connection.update {
                it.copy(
                    connection =
                        SeerrConnectionStatus.Success(CurrentSeerr(server, user, userConfig, publicSettings)),
                )
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
                serverRepository.currentUser?.let { jellyfinUser ->
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
                serverRepository.currentUser?.let { jellyfinUser ->
                    // TODO Need to update server early so that cookies are saved
                    seerrApi.update(server.url, null)
                    val userConfig =
                        seerrLogin(
                            client = seerrApi.api,
                            authMethod = authMethod,
                            username = username,
                            password = password,
                            quickConnectAuthorizer =
                                if (authMethod == SeerrAuthMethod.JELLYFIN) {
                                    { code -> serverRepository.authorizeQuickConnect(code) }
                                } else {
                                    null
                                },
                        )

                    val user =
                        SeerrUser(
                            jellyfinUserRowId = jellyfinUser.rowId,
                            serverId = server.id,
                            authMethod = authMethod,
                            username = username,
                            password = password.takeIf { it.isNotNullOrBlank() },
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
                    createSeerrApiUrl(url),
                    apiKey,
                    okHttpClient
                        .newBuilder()
                        .connectTimeout(2.seconds)
                        .readTimeout(6.seconds)
                        .build(),
                )
            seerrLogin(
                client = api,
                authMethod = authMethod,
                username = username,
                password = passwordOrApiKey,
                quickConnectAuthorizer =
                    if (authMethod == SeerrAuthMethod.JELLYFIN) {
                        { code -> serverRepository.authorizeQuickConnect(code) }
                    } else {
                        null
                    },
            )
            return LoadingState.Success
        }

        suspend fun removeServerForCurrentUser(): Boolean {
            val user =
                when (val conn = connection.value.connection) {
                    SeerrConnectionStatus.NotConfigured -> return false
                    is SeerrConnectionStatus.Error -> conn.user
                    is SeerrConnectionStatus.Success -> conn.current.user
                }
            val rows = seerrServerDao.deleteUser(user)
            clear()
            return rows > 0
        }

        /**
         * Given the [SeerrPluginSettings], try to connect to the Seerr server
         *
         * @return true if successfully updated the SeerrApi and logged in
         */
        suspend fun usePluginProvidedServer(settings: SeerrPluginSettings): Boolean {
            val currentUser = serverRepository.currentUser ?: return false
            if (settings.serverUrl.isNotNullOrBlank() && settings.login != null && settings.login != SeerrPluginLogin.None) {
                val server =
                    SeerrServer(
                        id = -1,
                        url = settings.serverUrl,
                    )
                val user =
                    SeerrUser(
                        jellyfinUserRowId = currentUser.rowId,
                        serverId = server.id,
                        authMethod =
                            when (settings.login) {
                                is SeerrPluginLogin.ApiKey -> SeerrAuthMethod.API_KEY
                                is SeerrPluginLogin.Local -> SeerrAuthMethod.LOCAL
                            },
                        username =
                            when (settings.login) {
                                is SeerrPluginLogin.ApiKey -> null
                                is SeerrPluginLogin.Local -> settings.login.local.username
                            },
                        password = null,
                        credential = null,
                    )
                try {
                    val userConfig =
                        when (settings.login) {
                            is SeerrPluginLogin.ApiKey -> {
                                Timber.v("maybeAddPluginServer, using API key")
                                seerrApi.update(settings.serverUrl, settings.login.apiKey)
                                seerrLogin(seerrApi.api, SeerrAuthMethod.API_KEY, null, null)
                            }

                            is SeerrPluginLogin.Local -> {
                                Timber.v("maybeAddPluginServer, using local user")
                                seerrApi.update(settings.serverUrl, null)
                                seerrLogin(
                                    seerrApi.api,
                                    SeerrAuthMethod.LOCAL,
                                    settings.login.local.username,
                                    settings.login.local.password,
                                )
                            }

                            SeerrPluginLogin.None -> {
                                return false
                            }
                        }
                    // Success
                    _connection.update { it.copy(pluginProvided = true) }
                    set(server, user, userConfig)
                    return true
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    clear()
                    Timber.w(ex, "Error adding plugin provided seerr")
                    _connection.update {
                        it.copy(
                            pluginProvided = true,
                            connection = SeerrConnectionStatus.Error(server, user, ex),
                        )
                    }
                    return false
                }
            }
            return false
        }

        /**
         * Connect an already-configured [SeerrUser] to its [SeerrServer].
         */
        suspend fun connectExistingUser(
            server: SeerrServer,
            seerrUser: SeerrUser,
            jellyfinUser: JellyfinUser? = serverRepository.currentUser,
            recordError: Boolean = true,
        ): Boolean =
            try {
                seerrApi.update(server.url, seerrUser.credential)
                val userConfig =
                    if (seerrUser.authMethod != SeerrAuthMethod.API_KEY) {
                        val passwordToUse =
                            seerrUser.password
                                ?: jellyfinUser
                                    ?.id
                                    ?.let { serverRepository.getLastLoginPassword(it) }
                                    .takeIf { seerrUser.authMethod == SeerrAuthMethod.JELLYFIN }
                        seerrLogin(
                            client = seerrApi.api,
                            authMethod = seerrUser.authMethod,
                            username = seerrUser.username ?: jellyfinUser?.name,
                            password = passwordToUse,
                            quickConnectAuthorizer =
                                if (seerrUser.authMethod == SeerrAuthMethod.JELLYFIN) {
                                    { code -> serverRepository.authorizeQuickConnect(code) }
                                } else {
                                    null
                                },
                        )
                    } else {
                        seerrApi.api.usersApi.authMeGet()
                    }
                set(server, seerrUser, userConfig)
                true
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Error logging into %s", server.url)
                if (recordError) {
                    error(server, seerrUser, ex)
                } else {
                    clear()
                }
                false
            }

        /**
         * Probe a single URL to verify if it is an active, initialized Seerr instance.
         */
        suspend fun probeSeerrServer(url: String): DiscoveredSeerrServer? {
            val normalizedUrl =
                try {
                    migrateSeerrUrl(url)
                } catch (_: Exception) {
                    return null
                }
            val probeClient =
                okHttpClient
                    .newBuilder()
                    .connectTimeout(1500.milliseconds)
                    .readTimeout(2500.milliseconds)
                    .build()
            val api = SeerrApiClient(createSeerrApiUrl(normalizedUrl), null, probeClient)
            return try {
                val status = api.publicApi.statusGet()
                if (status.version.isNullOrBlank()) return null
                val publicSettings = api.settingsApi.settingsPublicGet()
                if (publicSettings.initialized == false) return null
                DiscoveredSeerrServer(
                    url = normalizedUrl,
                    version = status.version,
                    name = publicSettings.applicationTitle,
                    publicSettings = publicSettings,
                )
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.v("Seerr probe failed for %s: %s", normalizedUrl, ex.message)
                null
            }
        }

        /**
         * Discover active Seerr servers by checking:
         * 1. Previously saved Seerr servers in the local database
         * 2. The current Jellyfin server's host on default port 5055 (http & https)
         * 3. (Optional fallback) Local /24 subnet scan on port 5055
         */
        suspend fun discoverSeerrServers(
            includeSubnetScan: Boolean = true,
            port: Int = SEERR_DEFAULT_PORT,
        ): List<DiscoveredSeerrServer> =
            withContext(WholphinDispatchers.IO) {
                val savedUrls = seerrServerDao.getServers().map { it.server.url }
                val jellyfinUrl = serverRepository.currentServer?.url
                val candidateUrls = createSeerrDiscoveryUrls(jellyfinUrl, savedUrls, port)
                Timber.d("Probing Seerr candidate URLs: %s", candidateUrls)

                val directMatches = mutableListOf<DiscoveredSeerrServer>()
                for (url in candidateUrls) {
                    val discovered = probeSeerrServer(url.toString())
                    if (discovered != null) {
                        Timber.i("Discovered active Seerr server at %s (v%s)", discovered.url, discovered.version)
                        directMatches.add(discovered)
                    }
                }
                if (directMatches.isNotEmpty() || !includeSubnetScan) {
                    return@withContext directMatches.distinctBy { it.url }
                }

                val subnetPrefixes = getLocalNetworkSubnetPrefixes(jellyfinUrl)
                val checkedHosts = candidateUrls.mapNotNull { it.host }.toSet()
                val subnetMatches = scanSubnetsForSeerr(subnetPrefixes, checkedHosts, port)
                subnetMatches.distinctBy { it.url }
            }

        private suspend fun scanSubnetsForSeerr(
            subnetPrefixes: List<String>,
            skipHosts: Set<String>,
            port: Int = SEERR_DEFAULT_PORT,
        ): List<DiscoveredSeerrServer> =
            coroutineScope {
                if (subnetPrefixes.isEmpty()) return@coroutineScope emptyList()
                Timber.d("Scanning local subnets %s on port %d for Seerr", subnetPrefixes, port)
                val semaphore = Semaphore(32)
                subnetPrefixes
                    .flatMap { prefix ->
                        (1..254).map { hostNum -> "$prefix$hostNum" }
                    }.filter { it !in skipHosts }
                    .map { ip ->
                        async(WholphinDispatchers.IO) {
                            semaphore.withPermit {
                                if (isTcpPortOpen(ip, port, timeoutMs = 350)) {
                                    probeSeerrServer("http://$ip:$port/")
                                        ?: probeSeerrServer("https://$ip:$port/")
                                } else {
                                    null
                                }
                            }
                        }
                    }.awaitAll()
                    .filterNotNull()
            }

        /**
         * Discover an active Seerr server and automatically log in using the currently connected
         * Jellyfin user (via Jellyfin Quick Connect or Jellyfin user credentials).
         *
         * @return true if a Seerr server was found and the user was automatically logged in
         */
        suspend fun autoDiscoverAndLogin(
            jellyfinUser: JellyfinUser? = serverRepository.currentUser,
            includeSubnetScan: Boolean = true,
        ): Boolean =
            withContext(WholphinDispatchers.IO) {
                val user = jellyfinUser ?: return@withContext false
                val discoveredServers = discoverSeerrServers(includeSubnetScan = includeSubnetScan)
                if (discoveredServers.isEmpty()) {
                    Timber.d("No active Seerr server discovered")
                    return@withContext false
                }

                val sessionPassword = serverRepository.getLastLoginPassword(user.id)
                val fallbackPassword =
                    sessionPassword
                        ?: "".takeIf { serverRepository.currentUserDto?.hasPassword == false }

                for (discovered in discoveredServers) {
                    try {
                        Timber.d("Attempting Seerr auto-login for user %s at %s", user.name, discovered.url)
                        seerrApi.update(discovered.url, null)
                        var usedPassword: String? = null
                        val userConfig =
                            try {
                                seerrQuickConnectLogin(seerrApi.api) { code ->
                                    serverRepository.authorizeQuickConnect(code)
                                }
                            } catch (qcEx: CancellationException) {
                                throw qcEx
                            } catch (qcEx: Exception) {
                                Timber.d(qcEx, "Seerr Quick Connect auto-login failed at %s, checking password fallback", discovered.url)
                                if (fallbackPassword != null && user.name.isNotNullOrBlank()) {
                                    usedPassword = fallbackPassword
                                    seerrLogin(
                                        client = seerrApi.api,
                                        authMethod = SeerrAuthMethod.JELLYFIN,
                                        username = user.name,
                                        password = fallbackPassword,
                                        quickConnectAuthorizer = null,
                                    )
                                } else {
                                    throw qcEx
                                }
                            }

                        var serverEntity = seerrServerDao.getServer(discovered.url)?.server
                        if (serverEntity == null) {
                            seerrServerDao.addServer(
                                SeerrServer(
                                    url = discovered.url,
                                    name = discovered.name,
                                    version = discovered.version,
                                ),
                            )
                            serverEntity = seerrServerDao.getServer(discovered.url)?.server
                        } else if (discovered.name != null || discovered.version != null) {
                            val updated =
                                serverEntity.copy(
                                    name = discovered.name ?: serverEntity.name,
                                    version = discovered.version ?: serverEntity.version,
                                )
                            seerrServerDao.updateServer(updated)
                            serverEntity = updated
                        }

                        if (serverEntity != null) {
                            val seerrUser =
                                SeerrUser(
                                    jellyfinUserRowId = user.rowId,
                                    serverId = serverEntity.id,
                                    authMethod = SeerrAuthMethod.JELLYFIN,
                                    username = user.name ?: userConfig.username,
                                    password = usedPassword?.takeIf { it.isNotNullOrBlank() },
                                    credential = null,
                                )
                            seerrServerDao.addUser(seerrUser)
                            set(serverEntity, seerrUser, userConfig)
                            Timber.i("Seerr auto-discovery and login succeeded at %s", discovered.url)
                            return@withContext true
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Seerr auto-login failed at %s", discovered.url)
                    }
                }
                clear()
                false
            }
    }

const val SEERR_DEFAULT_PORT = 5055

data class DiscoveredSeerrServer(
    val url: String,
    val version: String?,
    val name: String?,
    val publicSettings: PublicSettings,
)

/**
 * Build ordered candidate URLs for Seerr discovery from any saved Seerr server URLs and the
 * currently connected Jellyfin server's host on [port] (default 5055).
 */
fun createSeerrDiscoveryUrls(
    jellyfinServerUrl: String?,
    savedSeerrUrls: List<String> = emptyList(),
    port: Int = SEERR_DEFAULT_PORT,
): List<HttpUrl> {
    val urls = mutableListOf<HttpUrl>()
    for (saved in savedSeerrUrls) {
        if (saved.isNotBlank()) {
            try {
                migrateSeerrUrl(saved).toHttpUrlOrNull()?.let { urls.add(it) }
            } catch (_: Exception) {
            }
        }
    }
    if (!jellyfinServerUrl.isNullOrBlank()) {
        val jellyfinHttpUrl =
            jellyfinServerUrl.toHttpUrlOrNull()
                ?: "http://$jellyfinServerUrl".toHttpUrlOrNull()
        if (jellyfinHttpUrl != null) {
            val primaryScheme = jellyfinHttpUrl.scheme
            val alternateScheme = if (primaryScheme == "https") "http" else "https"
            urls.add(
                HttpUrl
                    .Builder()
                    .scheme(primaryScheme)
                    .host(jellyfinHttpUrl.host)
                    .port(port)
                    .addPathSegment("")
                    .build(),
            )
            urls.add(
                HttpUrl
                    .Builder()
                    .scheme(alternateScheme)
                    .host(jellyfinHttpUrl.host)
                    .port(port)
                    .addPathSegment("")
                    .build(),
            )
        }
    }
    return urls.distinct()
}

private val PRIVATE_IPV4_REGEX =
    Regex("""^(10\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}|172\.(1[6-9]|2\d|3[0-1])\.\d{1,3})\.(\d{1,3})$""")

/**
 * If [host] is a private RFC1918 IPv4 address, returns its /24 prefix (e.g. `"192.168.1."`).
 */
fun extractIpv4SubnetPrefix(host: String?): String? {
    if (host.isNullOrBlank()) return null
    val match = PRIVATE_IPV4_REGEX.matchEntire(host.trim()) ?: return null
    return "${match.groupValues[1]}."
}

fun getLocalNetworkSubnetPrefixes(jellyfinServerUrl: String?): List<String> {
    val prefixes = mutableListOf<String>()
    val jellyfinHost =
        jellyfinServerUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { it.toHttpUrlOrNull() ?: "http://$it".toHttpUrlOrNull() }
            ?.host
    extractIpv4SubnetPrefix(jellyfinHost)?.let { prefixes.add(it) }

    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        if (interfaces != null) {
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                for (inetAddress in networkInterface.inetAddresses) {
                    if (inetAddress is Inet4Address && !inetAddress.isLoopbackAddress && inetAddress.isSiteLocalAddress) {
                        extractIpv4SubnetPrefix(inetAddress.hostAddress)?.let { prefixes.add(it) }
                    }
                }
            }
        }
    } catch (ex: Exception) {
        Timber.v(ex, "Could not enumerate local network interfaces")
    }
    return prefixes.distinct()
}

private fun isTcpPortOpen(
    host: String,
    port: Int,
    timeoutMs: Int,
): Boolean =
    try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            true
        }
    } catch (_: Exception) {
        false
    }

/**
 * A [SeerrUser] config
 */
typealias SeerrUserConfig = User

data class SeerrState(
    val connection: SeerrConnectionStatus = SeerrConnectionStatus.NotConfigured,
    val pluginProvided: Boolean = false,
)

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
    val requestMovieEnabled: Boolean
        get() = config.hasPermission(SeerrPermission.REQUEST_MOVIE)
    val request4kMovieEnabled: Boolean
        get() =
            (serverConfig.movie4kEnabled ?: false) &&
                config.hasPermission(SeerrPermission.REQUEST_4K_MOVIE)

    val requestTvEnabled: Boolean
        get() = config.hasPermission(SeerrPermission.REQUEST_TV)
    val request4kTvEnabled: Boolean
        get() =
            (serverConfig.series4kEnabled ?: false) &&
                config.hasPermission(SeerrPermission.REQUEST_4K_TV)
}

suspend fun seerrQuickConnectLogin(
    client: SeerrApiClient,
    quickConnectAuthorizer: suspend (String) -> Boolean,
): User {
    val initiate = client.authApi.authJellyfinQuickconnectInitiatePost()
    val authorized = quickConnectAuthorizer.invoke(initiate.code)
    if (!authorized) {
        throw IllegalStateException("Jellyfin Quick Connect authorization was not approved")
    }
    runCatching {
        client.authApi.authJellyfinQuickconnectCheckGet(initiate.secret)
    }
    client.authApi.authJellyfinQuickconnectAuthenticatePost(
        AuthJellyfinQuickconnectAuthenticatePostRequest(secret = initiate.secret),
    )
    return client.usersApi.authMeGet()
}

suspend fun seerrLogin(
    client: SeerrApiClient,
    authMethod: SeerrAuthMethod,
    username: String?,
    password: String?,
    quickConnectAuthorizer: (suspend (String) -> Boolean)? = null,
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
            if (password.isNotNullOrBlank()) {
                try {
                    client.authApi.authJellyfinPost(
                        AuthJellyfinPostRequest(
                            username = username ?: "",
                            password = password,
                        ),
                    )
                    client.usersApi.authMeGet()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    if (quickConnectAuthorizer != null) {
                        Timber.d(ex, "Jellyfin password login failed, falling back to Quick Connect")
                        seerrQuickConnectLogin(client, quickConnectAuthorizer)
                    } else {
                        throw ex
                    }
                }
            } else if (quickConnectAuthorizer != null) {
                try {
                    seerrQuickConnectLogin(client, quickConnectAuthorizer)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.d(ex, "Jellyfin Quick Connect login failed, falling back to direct Jellyfin auth")
                    client.authApi.authJellyfinPost(
                        AuthJellyfinPostRequest(
                            username = username ?: "",
                            password = password ?: "",
                        ),
                    )
                    client.usersApi.authMeGet()
                }
            } else {
                client.authApi.authJellyfinPost(
                    AuthJellyfinPostRequest(
                        username = username ?: "",
                        password = password ?: "",
                    ),
                )
                client.usersApi.authMeGet()
            }
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
