package com.github.damontecres.dolphin.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.dolphin.ui.components.BasicDialog
import com.github.damontecres.dolphin.ui.components.EditTextBox
import com.github.damontecres.dolphin.ui.isNotNullOrBlank
import com.github.damontecres.dolphin.ui.nav.Destination

@Composable
fun SwitchUserContent(
    modifier: Modifier = Modifier,
    viewModel: SwitchUserViewModel = hiltViewModel(),
) {
    val currentServer = viewModel.serverRepository.currentServer
    val currentUser = viewModel.serverRepository.currentUser
    val users by viewModel.users.observeAsState(listOf())

    val serverQuickConnect by viewModel.serverQuickConnect.observeAsState(mapOf())
    val quickConnectEnabled = currentServer?.let { serverQuickConnect[it.id] ?: false } ?: false
    val quickConnect by viewModel.quickConnectState.observeAsState(null)
    var showAddUser by remember { mutableStateOf(false) }

    currentServer?.let { server ->
        Box(
            modifier = modifier,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .fillMaxWidth(.5f)
                        .align(Alignment.Center)
                        .padding(16.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                            shape = RoundedCornerShape(16.dp),
                        ),
            ) {
                Text(
                    text = "Select User",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = server.name ?: server.url,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                UserList(
                    users = users,
                    currentUser = currentUser,
                    onSwitchUser = { user ->
                        viewModel.switchUser(server, user) {
                            viewModel.navigationManager.goToHome()
                        }
                    },
                    onAddUser = { showAddUser = true },
                    onRemoveUser = { user ->
                        viewModel.removeUser(user)
                    },
                    onSwitchServer = {
                        viewModel.navigationManager.navigateTo(Destination.ServerList)
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                                shape = RoundedCornerShape(16.dp),
                            ),
                )
            }
        }

        if (showAddUser) {
            var useQuickConnect by remember { mutableStateOf(quickConnectEnabled) }
            LaunchedEffect(Unit) {
                if (useQuickConnect) {
                    viewModel.initiateQuickConnect(server) {
                        viewModel.navigationManager.goToHome()
                    }
                }
            }
            BasicDialog(
                onDismissRequest = {
                    viewModel.cancelQuickConnect()
                    showAddUser = false
                },
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                ) {
                    if (useQuickConnect) {
                        quickConnect?.let { qc ->
                            Text(
                                text = "Use Quick Connect on your device to authenticate to ${server.name ?: server.url}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = qc.code,
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                            Button(
                                onClick = {
                                    useQuickConnect = false
                                },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(text = "Use username/password")
                            }
                        }
                    } else {
                        var username by remember { mutableStateOf("") }
                        var password by remember { mutableStateOf("") }
                        val onSubmit = {
                            viewModel.login(server, username, password) {
                                showAddUser = false
                                viewModel.navigationManager.goToHome()
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier,
                        ) {
                            Text(
                                text = "Username",
                                modifier = Modifier,
                            )
                            EditTextBox(
                                value = username,
                                onValueChange = { username = it },
                                keyboardOptions =
                                    KeyboardOptions(
                                        capitalization = KeyboardCapitalization.None,
                                        autoCorrectEnabled = false,
                                        keyboardType = KeyboardType.Text,
                                    ),
                                modifier = Modifier,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier,
                        ) {
                            Text(
                                text = "Password",
                                modifier = Modifier,
                            )
                            EditTextBox(
                                value = password,
                                onValueChange = { password = it },
                                keyboardOptions =
                                    KeyboardOptions(
                                        capitalization = KeyboardCapitalization.None,
                                        autoCorrectEnabled = false,
                                        keyboardType = KeyboardType.Password,
                                    ),
                                keyboardActions =
                                    KeyboardActions(
                                        onDone = { onSubmit.invoke() },
                                    ),
                                modifier = Modifier,
                            )
                        }
                        Button(
                            onClick = { onSubmit.invoke() },
                            enabled = username.isNotNullOrBlank() && password.isNotNullOrBlank(),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text("Login")
                        }
                    }
                }
            }
        }
    }
}
