package com.github.damontecres.wholphin.ui.setup

import android.widget.Toast
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.dimAndBlur
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState

@Composable
fun SwitchUserContent(
    currentServer: JellyfinServer,
    modifier: Modifier = Modifier,
    viewModel: SwitchUserViewModel =
        hiltViewModel<SwitchUserViewModel, SwitchUserViewModel.Factory>(
            creationCallback = { it.create(currentServer) },
        ),
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.init()
    }

//    val currentServer by viewModel.serverRepository.currentServer.observeAsState()
    val currentUser by viewModel.serverRepository.currentUser.observeAsState()
    val users by viewModel.users.observeAsState(listOf())

    val quickConnectEnabled by viewModel.serverQuickConnect.observeAsState(false)
    val quickConnect by viewModel.quickConnectState.observeAsState(null)
    var showAddUser by remember { mutableStateOf(false) }

    val userState by viewModel.switchUserState.observeAsState(LoadingState.Pending)
    val loginAttempts by viewModel.loginAttempts.observeAsState(0)
    LaunchedEffect(userState) {
        if (!showAddUser) {
            when (val s = userState) {
                is LoadingState.Error -> {
                    val msg = s.message ?: s.exception?.localizedMessage
                    Toast.makeText(context, "Error: $msg", Toast.LENGTH_LONG).show()
                }

                else -> {}
            }
        }
    }
    var switchUserWithPin by remember { mutableStateOf<JellyfinUser?>(null) }

    currentServer?.let { server ->
        Box(
            modifier = modifier.dimAndBlur(showAddUser || switchUserWithPin != null),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(16.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.select_user),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = server.name ?: server.url,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                UserList(
                    users = users,
                    currentUser = currentUser,
                    onSwitchUser = { user ->
                        // TODO PIN-related
//                        if (user.pin.isNotNullOrBlank()) {
//                            switchUserWithPin = user
//                        } else {
//                            viewModel.switchUser(user)
//                        }
                        viewModel.switchUser(user)
                    },
                    onAddUser = { showAddUser = true },
                    onRemoveUser = { user ->
                        viewModel.removeUser(user)
                    },
                    onSwitchServer = {
                        viewModel.navigationManager.navigateTo(Destination.ServerList)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (showAddUser) {
            var useQuickConnect by remember { mutableStateOf(quickConnectEnabled) }
            LaunchedEffect(Unit) {
                viewModel.clearSwitchUserState()
                viewModel.resetAttempts()
                if (useQuickConnect) {
                    viewModel.initiateQuickConnect(server)
                }
            }
            BasicDialog(
                onDismissRequest = {
                    viewModel.cancelQuickConnect()
                    showAddUser = false
                },
                properties =
                    DialogProperties(
                        usePlatformDefaultWidth = false,
                    ),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .focusGroup()
                            .padding(16.dp)
                            .fillMaxWidth(.4f),
                ) {
                    if (useQuickConnect) {
                        if (quickConnect == null && userState !is LoadingState.Error) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier =
                                    Modifier
                                        .height(32.dp)
                                        .align(Alignment.CenterHorizontally),
                            ) {
                                CircularProgress(Modifier.size(20.dp))
                                Text(
                                    text = "Waiting for Quick Connect code...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier,
                                )
                            }
                        } else if (quickConnect != null) {
                            Text(
                                text = "Use Quick Connect on your device to authenticate to ${server.name ?: server.url}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = quickConnect?.code ?: "Failed to get code",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                        UserStateError(userState)
                        TextButton(
                            stringRes = R.string.username_or_password,
                            onClick = {
                                viewModel.cancelQuickConnect()
                                viewModel.clearSwitchUserState()
                                useQuickConnect = false
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    } else {
//                        val username = rememberTextFieldState()
//                        val password = rememberTextFieldState()
                        var username by remember { mutableStateOf("") }
                        var password by remember { mutableStateOf("") }
                        val onSubmit = {
                            viewModel.login(
                                server,
                                username,
                                password,
                            )
                        }
                        val focusRequester = remember { FocusRequester() }
                        val passwordFocusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                        Text(
                            text = "Enter username/password to login to ${server.name ?: server.url}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        UserStateError(userState)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(
                                text = "Username",
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            EditTextBox(
                                value = username,
                                onValueChange = { username = it },
                                keyboardOptions =
                                    KeyboardOptions(
                                        capitalization = KeyboardCapitalization.None,
                                        autoCorrectEnabled = false,
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Next,
                                    ),
                                keyboardActions =
                                    KeyboardActions(
                                        onNext = {
                                            passwordFocusRequester.tryRequestFocus()
                                        },
                                    ),
                                //                                onKeyboardAction = {
//                                    passwordFocusRequester.tryRequestFocus()
//                                },
                                isInputValid = { userState !is LoadingState.Error },
                                modifier = Modifier.focusRequester(focusRequester),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(
                                text = "Password",
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            LaunchedEffect(password) {
                                viewModel.clearSwitchUserState()
                            }
                            EditTextBox(
                                value = password,
                                onValueChange = { password = it },
                                keyboardOptions =
                                    KeyboardOptions(
                                        capitalization = KeyboardCapitalization.None,
                                        autoCorrectEnabled = false,
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Go,
                                    ),
                                keyboardActions =
                                    KeyboardActions(
                                        onGo = { onSubmit.invoke() },
                                    ),
                                isInputValid = { userState !is LoadingState.Error },
                                modifier = Modifier.focusRequester(passwordFocusRequester),
                            )
                        }
                        TextButton(
                            stringRes = R.string.login,
                            onClick = { onSubmit.invoke() },
                            enabled = username.isNotNullOrBlank(),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                    if (loginAttempts > 2) {
                        Text(
                            text = "Trouble logging in?",
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        TextButton(
                            stringRes = R.string.show_debug_info,
                            onClick = {
                                viewModel.navigationManager.navigateTo(Destination.Debug)
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
        }
        switchUserWithPin?.let { user ->
            PinEntryDialog(
                onDismissRequest = { switchUserWithPin = null },
                onClickServerAuth = {
                    showAddUser = true
                    switchUserWithPin = null
                },
                onTextChange = {
                    if (it == user.pin) viewModel.switchUser(user)
                },
            )
        }
    }
}

@Composable
private fun UserStateError(
    userState: LoadingState,
    modifier: Modifier = Modifier,
) {
    when (val s = userState) {
        is LoadingState.Error -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = modifier,
            ) {
                s.message?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (s.exception != null) {
                    s.exception.localizedMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    s.exception.cause?.localizedMessage?.let {
                        Text(
                            text = "Cause: $it",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        else -> {}
    }
}
