package com.github.damontecres.dolphin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.data.JellyfinServer
import com.github.damontecres.dolphin.data.JellyfinUser
import com.github.damontecres.dolphin.data.ServerRepository
import com.github.damontecres.dolphin.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.userApi
import javax.inject.Inject

@HiltViewModel
class ServerManagementViewModel
    @Inject
    constructor(
        val serverRepository: ServerRepository,
        val userPreferencesDataStore: DataStore<UserPreferences>,
        val jellyfin: Jellyfin,
    ) : ViewModel() {
        fun addServer() {
        }

        suspend fun loginWithPassword(
            serverUrl: String,
            username: String,
            password: String,
        ) {
            try {
                val api = jellyfin.createApi(baseUrl = serverUrl)
                val authenticationResult by api.userApi.authenticateUserByName(
                    username = username,
                    password = password,
                )

                val accessToken = authenticationResult.accessToken
                if (accessToken != null) {
                    val server =
                        authenticationResult.serverId?.let {
                            JellyfinServer(
                                id = it,
                                name = serverUrl,
                                url = serverUrl,
                            )
                        }
                    if (server != null) {
                        val user =
                            authenticationResult.user?.let {
                                JellyfinUser(
                                    id = it.id.toString(),
                                    name = it.name,
                                    serverId = server.id,
                                    accessToken = accessToken,
                                )
                            }
                        if (user != null) {
                            userPreferencesDataStore.updateData {
                                it
                                    .toBuilder()
                                    .apply {
                                        currentServerId = server.id
                                        currentUserId = user.id
                                    }.build()
                            }
                            serverRepository.addAndChangeUser(server, user)
                        }
                    }
                }

                // Print session information
                println(authenticationResult.sessionInfo)
            } catch (err: InvalidStatusException) {
                if (err.status == 401) {
                    // Username or password is incorrect
                    println("Invalid user")
                }
            }
        }
    }

@Composable
fun ServerLoginPage(
    modifier: Modifier = Modifier,
    viewModel: ServerManagementViewModel = viewModel(),
) {
    val scope = rememberCoroutineScope()
    ServerLoginForm(
        modifier = modifier,
        onSubmit = { serverUrl, username, password ->
            scope.launch {
                // Handle form submission
                viewModel.loginWithPassword(serverUrl, username, password)
            }
        },
    )
}

@Composable
fun ServerLoginForm(
    modifier: Modifier = Modifier,
    onSubmit: (serverUrl: String, username: String, password: String) -> Unit,
) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(modifier = modifier.padding(16.dp)) {
        TextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onSubmit(serverUrl, username, password) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Submit")
        }
    }
}
