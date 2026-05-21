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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.dimAndBlur
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.launch

@Composable
fun SwitchUserContent(
    server: JellyfinServer,
    modifier: Modifier = Modifier,
    viewModel: SwitchUserViewModel =
        hiltViewModel<SwitchUserViewModel, SwitchUserViewModel.Factory>(
            creationCallback = { it.create(server) },
        ),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        viewModel.init()
    }

    val state by viewModel.state.collectAsState()

    val currentUser by viewModel.serverRepository.currentUser.observeAsState()
    var showAddUser by remember { mutableStateOf(false) }
    var addUser by remember(server) { mutableStateOf<JellyfinUser?>(null) }
    var username by remember(addUser) { mutableStateOf(addUser?.name ?: "") }

    fun showAddUserDialog(user: JellyfinUser?) {
        addUser = user
        showAddUser = true
    }

    fun hideAddUserDialog() {
        addUser = null
        showAddUser = false
    }

    LaunchedEffect(state.switchUserState) {
        if (!showAddUser) {
            when (val s = state.switchUserState) {
                is LoadingState.Error -> {
                    val msg = s.message ?: s.exception?.localizedMessage
                    Toast.makeText(context, "Error: $msg", Toast.LENGTH_LONG).show()
                }

                else -> {}
            }
        }
    }
    var switchUserWithPin by remember { mutableStateOf<JellyfinUser?>(null) }

    when (val st = state.loading) {
        is LoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
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
                        users = state.users,
                        currentUser = currentUser,
                        onSwitchUser = { user ->
                            if (user.accessToken == null || user.requireLogin) {
                                showAddUserDialog(user)
                            } else if (user.hasPin) {
                                switchUserWithPin = user
                            } else {
                                val result = viewModel.trySwitchUser(user)
                                scope.launch {
                                    result.await()?.let {
                                        Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                                        showAddUserDialog(user)
                                    }
                                }
                            }
                        },
                        onAddUser = {
                            showAddUserDialog(null)
                        },
                        onRemoveUser = { user ->
                            viewModel.removeUser(user)
                        },
                        onSwitchServer = {
                            viewModel.setupNavigationManager.navigateTo(
                                SetupDestination.ServerList,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showAddUser) {
        var useQuickConnect by remember { mutableStateOf(state.quickConnectEnabled) }
        LaunchedEffect(Unit) {
            viewModel.clearSwitchUserState()
            viewModel.resetAttempts()
            if (useQuickConnect) {
                viewModel.initiateQuickConnect(server, addUser)
            }
        }
        BasicDialog(
            onDismissRequest = {
                viewModel.cancelQuickConnect()
                hideAddUserDialog()
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
                    if (state.quickConnectStatus == null && state.switchUserState !is LoadingState.Error) {
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
                    } else if (state.quickConnectStatus != null) {
                        Text(
                            text = "Use Quick Connect on your device to authenticate to ${server.name ?: server.url}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = state.quickConnectStatus?.code ?: "Failed to get code",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                    UserStateError(state.switchUserState)
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
                    var password by remember { mutableStateOf("") }
                    val onSubmit = {
                        viewModel.login(
                            server,
                            addUser,
                            username,
                            password,
                        )
                    }
                    val focusRequester = remember { FocusRequester() }
                    val passwordFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        if (username.isBlank()) {
                            focusRequester.tryRequestFocus()
                        } else {
                            passwordFocusRequester.tryRequestFocus()
                        }
                    }
                    Text(
                        text = "Enter username/password to login to ${server.name ?: server.url}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    UserStateError(state.switchUserState)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            text = stringResource(R.string.username),
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
                            isInputValid = { state.switchUserState !is LoadingState.Error },
                            modifier = Modifier.focusRequester(focusRequester),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            text = stringResource(R.string.password),
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
                            isInputValid = { state.switchUserState !is LoadingState.Error },
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
                if (state.loginAttempts > 2) {
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
                showAddUserDialog(user)
                switchUserWithPin = null
            },
            onTextChange = {
                if (it == user.pin) {
                    val result = viewModel.trySwitchUser(user)
                    scope.launch {
                        result.await()?.let {
                            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                            showAddUserDialog(user)
                            switchUserWithPin = null
                        }
                    }
                }
            },
        )
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
