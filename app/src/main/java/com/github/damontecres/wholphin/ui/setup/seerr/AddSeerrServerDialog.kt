package com.github.damontecres.wholphin.ui.setup.seerr

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
import com.github.damontecres.wholphin.services.CurrentSeerr
import com.github.damontecres.wholphin.services.SeerrState
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.ScrollableDialog
import com.github.damontecres.wholphin.ui.preferences.rememberSeerrDialogBody
import com.github.damontecres.wholphin.util.LoadingState

@Composable
fun SeerrDialog(
    seerrDialogMode: SeerrDialogMode,
    seerrConnection: SeerrState,
    onDismissRequest: () -> Unit,
    seerrVm: SwitchSeerrViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    when (val mode = seerrDialogMode) {
        is SeerrDialogMode.Remove -> {
            val body = rememberSeerrDialogBody(mode.current.user, mode.current.server, seerrConnection.pluginProvided)
            if (seerrConnection.pluginProvided) {
                ScrollableDialog(
                    onDismissRequest = onDismissRequest,
                ) {
                    item {
                        Text(body)
                    }
                }
            } else {
                ConfirmDialog(
                    title = stringResource(R.string.remove_seerr_server),
                    body = body,
                    onCancel = onDismissRequest,
                    onConfirm = {
                        seerrVm.removeServer()
                        onDismissRequest.invoke()
                    },
                    elevation = 3.dp,
                )
            }
        }

        SeerrDialogMode.Add -> {
            val currentUser by seerrVm.currentUser.collectAsState(null)
            val state by seerrVm.state.collectAsState()
            val serverAddedMessage = stringResource(R.string.seerr_server_added)
            LaunchedEffect(state.serverConnectionStatus) {
                if (state.serverConnectionStatus == LoadingState.Success) {
                    Toast.makeText(context, serverAddedMessage, Toast.LENGTH_SHORT).show()
                    onDismissRequest.invoke()
                }
            }
            when (state.loading) {
                is LoadingState.Error,
                LoadingState.Loading,
                LoadingState.Pending,
                -> {
                    BasicDialog(
                        onDismissRequest = onDismissRequest,
                    ) {
                        LoadingPage(Modifier.padding(16.dp))
                    }
                }

                LoadingState.Success -> {
                    AddSeerServerDialog(
                        initialUrl = state.initialUrl,
                        currentUsername = currentUser?.name,
                        status = state.serverConnectionStatus,
                        onSubmit = seerrVm::submitServer,
                        onResetStatus = seerrVm::resetStatus,
                        onDismissRequest = onDismissRequest,
                    )
                }
            }
        }

        is SeerrDialogMode.Error -> {
            val body = rememberSeerrDialogBody(mode.user, mode.server, seerrConnection.pluginProvided, mode.ex)
            if (seerrConnection.pluginProvided) {
                ConfirmDialog(
                    title = stringResource(R.string.error),
                    body = body,
                    onCancel = onDismissRequest,
                    onConfirm = {
                        seerrVm.retryServerFromPlugin()
                        onDismissRequest.invoke()
                    },
                    bodyColor = MaterialTheme.colorScheme.error,
                    elevation = 3.dp,
                    confirmStringRes = R.string.retry,
                )
            } else {
                ConfirmDialog(
                    title = stringResource(R.string.remove_seerr_server),
                    body = body,
                    onCancel = onDismissRequest,
                    onConfirm = {
                        seerrVm.removeServer()
                        onDismissRequest.invoke()
                    },
                    bodyColor = MaterialTheme.colorScheme.error,
                    elevation = 3.dp,
                )
            }
        }

        SeerrDialogMode.None -> {}
    }
}

sealed interface SeerrDialogMode {
    data object None : SeerrDialogMode

    data object Add : SeerrDialogMode

    data class Remove(
        val current: CurrentSeerr,
    ) : SeerrDialogMode

    data class Error(
        val server: SeerrServer,
        val user: SeerrUser,
        val ex: Exception,
    ) : SeerrDialogMode
}

@Composable
fun AddSeerServerDialog(
    currentUsername: String?,
    initialUrl: String?,
    status: LoadingState,
    onSubmit: (url: String, username: String, passwordOrApiKey: String, method: SeerrAuthMethod) -> Unit,
    onResetStatus: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    var authMethod by remember { mutableStateOf<SeerrAuthMethod?>(null) }
    LaunchedEffect(status) {
        if (status is LoadingState.Success) {
            onDismissRequest.invoke()
        }
    }
    when (val auth = authMethod) {
        SeerrAuthMethod.LOCAL,
        SeerrAuthMethod.JELLYFIN,
        -> {
            BasicDialog(
                onDismissRequest = { authMethod = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                AddSeerrServerUsername(
                    initialUrl = initialUrl ?: "",
                    onSubmit = { url, username, password ->
                        onSubmit.invoke(url, username, password, auth)
                    },
                    username = currentUsername ?: "",
                    status = status,
                    modifier = Modifier.widthIn(min = 320.dp),
                )
            }
        }

        SeerrAuthMethod.API_KEY -> {
            BasicDialog(
                onDismissRequest = { authMethod = null },
            ) {
                AddSeerrServerApiKey(
                    initialUrl = initialUrl ?: "",
                    onSubmit = { url, apiKey ->
                        onSubmit.invoke(url, "", apiKey, SeerrAuthMethod.API_KEY)
                    },
                    status = status,
                    modifier = Modifier.widthIn(min = 320.dp),
                )
            }
        }

        null -> {
            ChooseSeerrLoginType(
                onDismissRequest = onDismissRequest,
                onChoose = {
                    onResetStatus.invoke()
                    authMethod = it
                },
            )
        }
    }
}

@Composable
fun ChooseSeerrLoginType(
    onDismissRequest: () -> Unit,
    onChoose: (SeerrAuthMethod) -> Unit,
) {
    val params =
        DialogParams(
            fromLongClick = false,
            title = stringResource(R.string.seerr_login),
            items =
                listOf(
                    DialogItem(
                        text = stringResource(R.string.api_key),
                        onClick = { onChoose.invoke(SeerrAuthMethod.API_KEY) },
                    ),
                    DialogItem(
                        text = stringResource(R.string.seerr_jellyfin_user),
                        onClick = { onChoose.invoke(SeerrAuthMethod.JELLYFIN) },
                    ),
                    DialogItem(
                        text = stringResource(R.string.seerr_local_user),
                        onClick = { onChoose.invoke(SeerrAuthMethod.LOCAL) },
                    ),
                ),
        )

    DialogPopup(
        params = params,
        onDismissRequest = onDismissRequest,
        dismissOnClick = false,
    )
}
