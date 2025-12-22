package com.github.damontecres.wholphin.ui.setup

import androidx.lifecycle.MutableLiveData
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
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
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

        val serverQuickConnect = MutableLiveData<Boolean>(false)

        val users = MutableLiveData<List<JellyfinUserAndImage>>(listOf())
        val quickConnectState = MutableLiveData<QuickConnectResult?>(null)

        private var quickConnectJob: Job? = null

        val switchUserState = MutableLiveData<LoadingState>(LoadingState.Pending)

        val loginAttempts = MutableLiveData(0)

        fun clearSwitchUserState() {
            switchUserState.value = LoadingState.Pending
        }

        fun resetAttempts() {
            loginAttempts.value = 0
        }

        init {
            init()
        }

        fun init() {
            viewModelScope.launch(Dispatchers.Main + ExceptionHandler()) {
                serverRepository.switchServerOrUser()
            }
            quickConnectJob?.cancel()
            viewModelScope.launchIO {
                users.setValueOnMain(listOf())
                val serverUsers = getUsers()
                withContext(Dispatchers.Main) {
                    users.setValueOnMain(serverUsers)
                }
            }

            viewModelScope.launchIO {
                try {
                    jellyfin
                        .createApi(
                            server.url,
                            httpClientOptions =
                                HttpClientOptions(
                                    requestTimeout = 6.seconds,
                                    connectTimeout = 6.seconds,
                                    socketTimeout = 6.seconds,
                                ),
                        ).systemApi
                        .getPublicSystemInfo()
                    val quickConnect by
                        jellyfin
                            .createApi(server.url)
                            .quickConnectApi
                            .getQuickConnectEnabled()
                    withContext(Dispatchers.Main) {
                        serverQuickConnect.value = quickConnect
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Error checking quick connect for server ${server.url}")
                    withContext(Dispatchers.Main) {
                        serverQuickConnect.value = false
                    }
                }
            }
        }

        fun switchUser(user: JellyfinUser) {
            viewModelScope.launchIO {
                try {
                    val current = serverRepository.changeUser(server, user)
                    if (current != null) {
                        withContext(Dispatchers.Main) {
                            setupNavigationManager.navigateTo(SetupDestination.AppContent(current))
                        }
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
                        withContext(Dispatchers.Main) {
                            setupNavigationManager.navigateTo(SetupDestination.AppContent(current))
                        }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error logging in user")
                    if (ex is InvalidStatusException && ex.status == 401) {
                        withContext(Dispatchers.Main) {
                            switchUserState.value =
                                LoadingState.Error("Invalid username or password")
                        }
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
                        var state =
                            api
                                .quickConnectApi
                                .initiateQuickConnect()
                                .content

                        withContext(Dispatchers.Main) {
                            quickConnectState.value = state
                        }

                        while (!state.authenticated) {
                            delay(5_000L)
                            state =
                                api.quickConnectApi
                                    .getQuickConnectState(
                                        secret = state.secret,
                                    ).content
                            withContext(Dispatchers.Main) {
                                quickConnectState.value = state
                            }
                        }
                        val authenticationResult by api.userApi.authenticateWithQuickConnect(
                            QuickConnectDto(secret = state.secret),
                        )
                        val current = serverRepository.changeUser(server.url, authenticationResult)
                        if (current != null) {
                            withContext(Dispatchers.Main) {
                                setupNavigationManager.navigateTo(
                                    SetupDestination.AppContent(current),
                                )
                            }
                        }
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error during quick connect")
                        if (ex is InvalidStatusException && ex.status == 401) {
                            withContext(Dispatchers.Main) {
                                quickConnectState.value = null
                                serverQuickConnect.value = false
                            }
                        }
                        setError("Error with Quick Connect", ex)
                    }
                }
        }

        fun cancelQuickConnect() {
            quickConnectJob?.cancel()
            quickConnectState.value = null
        }

        fun removeUser(user: JellyfinUser) {
            viewModelScope.launchIO {
                serverRepository.removeUser(user)
                val serverUsers = getUsers()
                withContext(Dispatchers.Main) {
                    users.value = serverUsers
                }
            }
        }

        private suspend fun getUsers(): List<JellyfinUserAndImage> {
            val api = jellyfin.createApi(server.url)
            return serverDao
                .getServer(server.id)
                ?.users
                ?.sortedBy { it.name }
                ?.map { JellyfinUserAndImage(it, api.imageApi.getUserImageUrl(it.id)) }
                .orEmpty()
        }

        private suspend fun setError(
            msg: String? = null,
            ex: Exception? = null,
        ) = withContext(Dispatchers.Main) {
            loginAttempts.value = (loginAttempts.value ?: 0) + 1
            switchUserState.value = LoadingState.Error(msg, ex)
        }
    }

data class JellyfinUserAndImage(
    val user: JellyfinUser,
    val imageUrl: String?,
)
