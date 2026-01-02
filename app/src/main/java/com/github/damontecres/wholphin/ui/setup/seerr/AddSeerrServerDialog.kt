package com.github.damontecres.wholphin.ui.setup.seerr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.util.LoadingState

@Composable
fun AddSeerServerDialog(
    onDismissRequest: () -> Unit,
    viewModel: SwitchSeerrViewModel = hiltViewModel(),
) {
    val currentUser by viewModel.currentUser.observeAsState()
    var authMethod by remember { mutableStateOf<SeerrAuthMethod?>(null) }
    val status by viewModel.serverConnectionStatus.collectAsState(LoadingState.Pending)
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
            ) {
                AddSeerrServerUsername(
                    onSubmit = { url, username, password ->
                        viewModel.submitServer(
                            url,
                            username,
                            password,
                            auth,
                        )
                    },
                    username = currentUser?.name ?: "",
                    status = status,
                )
            }
        }

        SeerrAuthMethod.API_KEY -> {
            BasicDialog(
                onDismissRequest = { authMethod = null },
            ) {
                AddSeerrServerApiKey(
                    onSubmit = { url, apiKey -> viewModel.submitServer(url, apiKey) },
                    status = status,
                )
            }
        }

        null -> {
            ChooseSeerrLoginType(
                onDismissRequest = onDismissRequest,
                onChoose = { authMethod = it },
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
        remember {
            DialogParams(
                fromLongClick = false,
                title = "Login to Seerr server",
                items =
                    listOf(
                        DialogItem(
                            text = "API Key",
                            onClick = { onChoose.invoke(SeerrAuthMethod.API_KEY) },
                        ),
                        DialogItem(
                            text = "Jellyfin user",
                            onClick = { onChoose.invoke(SeerrAuthMethod.JELLYFIN) },
                        ),
                        DialogItem(
                            text = "Local user",
                            onClick = { onChoose.invoke(SeerrAuthMethod.LOCAL) },
                        ),
                    ),
            )
        }
    DialogPopup(
        params = params,
        onDismissRequest = onDismissRequest,
        dismissOnClick = false,
    )
}
