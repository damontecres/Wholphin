package com.github.damontecres.wholphin.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.JellyfinServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.SetupNavigationManager
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.api.QuickConnectResult
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

@HiltViewModel(assistedFactory = SwitchUserViewModel.Factory::class)
class SwitchUserViewModel
    @AssistedInject
    constructor(
        val jellyfin: Jellyfin,
        val serverRepository: ServerRepository,
        val serverDao: JellyfinServerDao,
        val navigationManager: NavigationManager,
        val setupNavigationManager: SetupNavigationManager,
        val imageUrlService: ImageUrlService,
        @Assisted val server: JellyfinServer,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(server: JellyfinServer): SwitchUserViewModel
        }

        private val _state = MutableStateFlow(SwitchUserState())
        val state: StateFlow<SwitchUserState> = _state

        private var quickConnectJob: Job? = null

        fun clearSwitchUserState() {
            _state.update { it.copy(switchUserState = LoadingState.Pending) }
        }

        fun resetAttempts() {
            _state.update { it.copy(loginAttempts = 0) }
        }

        fun init() {
            viewModelScope.launchDefault {
                serverRepository.switchServerOrUser()
            }
            quickConnectJob?.cancel()
            viewModelScope.launchIO {
                _state.update { SwitchUserState() }
                try {
                    val serverUsers = getUsers()
                    _state.update {
                        it.copy(
                            loading = LoadingState.Success,
                            users = serverUsers,
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching users for server $server")
                    _state.update {
                        it.copy(
                            loading = LoadingState.Error(ex),
                        )
                    }
                }
            }

            viewModelScope.launchIO {
                try {
                    val quickConnect by
                        jellyfin
                            .createApi(
                                server.url,
                                httpClientOptions =
                                    HttpClientOptions(
                                        requestTimeout = 6.seconds,
                                        connectTimeout = 6.seconds,
                                        socketTimeout = 6.seconds,
                                    ),
                            ).quickConnectApi
                            .getQuickConnectEnabled()
                    _state.update { it.copy(quickConnectEnabled = quickConnect) }
                } catch (_: CancellationException) {
                    // no-op, user may have canceled
                } catch (ex: Exception) {
                    Timber.w(ex, "Error checking quick connect for server ${server.url}")
                    _state.update { it.copy(quickConnectEnabled = false) }
                }
            }
        }

        fun switchUser(user: JellyfinUser) {
            viewModelScope.launchIO {
                try {
                    val current = serverRepository.changeUser(server, user)
                    if (current != null) {
                        setupNavigationManager.navigateTo(SetupDestination.AppContent(current))
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error switching user")
                    setError("Error switching user", ex)
                }
            }
        }

        fun login(
            server: JellyfinServer,
            username: String,
            password: String,
        ) {
            quickConnectJob?.cancel()
            viewModelScope.launchIO {
                try {
                    val api = jellyfin.createApi(baseUrl = server.url)
                    val authenticationResult by api.userApi.authenticateUserByName(
                        username = username,
                        password = password,
                    )
                    val current = serverRepository.changeUser(server.url, authenticationResult)
                    if (current != null) {
                        setupNavigationManager.navigateTo(SetupDestination.AppContent(current))
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error logging in user")
                    if (ex is InvalidStatusException && ex.status == 401) {
                        _state.update { it.copy(switchUserState = LoadingState.Error("Invalid username or password")) }
                    } else {
                        setError("Error during login", ex)
                    }
                }
            }
        }

        fun initiateQuickConnect(server: JellyfinServer) {
            quickConnectJob?.cancel()
            quickConnectJob =
                viewModelScope.launchIO {
                    try {
                        val api = jellyfin.createApi(server.url)
                        var quickConnectStatus =
                            api
                                .quickConnectApi
                                .initiateQuickConnect()
                                .content
                        _state.update { it.copy(quickConnectStatus = quickConnectStatus) }

                        while (!quickConnectStatus.authenticated) {
                            delay(5_000L)
                            quickConnectStatus =
                                api.quickConnectApi
                                    .getQuickConnectState(
                                        secret = quickConnectStatus.secret,
                                    ).content
                            _state.update { it.copy(quickConnectStatus = quickConnectStatus) }
                        }
                        val authenticationResult by api.userApi.authenticateWithQuickConnect(
                            QuickConnectDto(secret = quickConnectStatus.secret),
                        )
                        val current = serverRepository.changeUser(server.url, authenticationResult)
                        if (current != null) {
                            setupNavigationManager.navigateTo(SetupDestination.AppContent(current))
                        }
                    } catch (_: CancellationException) {
                        // no-op, user may have canceled
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error during quick connect")
                        if (ex is InvalidStatusException && ex.status == 401) {
                            _state.update {
                                it.copy(
                                    quickConnectEnabled = false,
                                    quickConnectStatus = null,
                                )
                            }
                        }
                        setError("Error with Quick Connect", ex)
                    }
                }
        }

        fun cancelQuickConnect() {
            quickConnectJob?.cancel()
            _state.update {
                it.copy(
                    quickConnectStatus = null,
                )
            }
        }

        fun removeUser(user: JellyfinUser) {
            viewModelScope.launchIO {
                serverRepository.removeUser(user)
                val serverUsers = getUsers()
                _state.update { it.copy(users = serverUsers) }
            }
        }

        private suspend fun getUsers(): List<JellyfinUserAndImage> =
            withContext(Dispatchers.IO) {
                val api = jellyfin.createApi(server.url)
                val knownUsers =
                    serverDao
                        .getServer(server.id)
                        ?.users
                        .orEmpty()
                val knownUserIds = knownUsers.map { it.id }
                val publicUsers =
                    api.userApi
                        .getPublicUsers()
                        .content
                        .map {
                            JellyfinUser(
                                id = it.id,
                                name = it.name,
                                serverId = server.id,
                                accessToken = null,
                            )
                        }.filter { it.id !in knownUserIds }

                return@withContext (knownUsers + publicUsers)
                    .map { JellyfinUserAndImage(it, api.imageApi.getUserImageUrl(it.id)) }
            }

        private fun setError(
            msg: String? = null,
            ex: Exception? = null,
        ) {
            _state.update {
                it.copy(
                    loginAttempts = it.loginAttempts + 1,
                    switchUserState = LoadingState.Error(msg, ex),
                )
            }
        }
    }

data class JellyfinUserAndImage(
    val user: JellyfinUser,
    val imageUrl: String?,
)

data class SwitchUserState(
    // LoadingState for fetching available users
    val loading: LoadingState = LoadingState.Pending,
    val quickConnectEnabled: Boolean = false,
    val quickConnectStatus: QuickConnectResult? = null,
    val users: List<JellyfinUserAndImage> = emptyList(),
    // LoadingState for while adding/switching users
    val switchUserState: LoadingState = LoadingState.Pending,
    val loginAttempts: Int = 0,
)
