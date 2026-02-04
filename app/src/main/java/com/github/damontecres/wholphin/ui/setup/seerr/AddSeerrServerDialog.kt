package com.github.damontecres.wholphin.ui.setup.seerr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.util.LoadingState

@Composable
fun AddSeerServerDialog(
    currentUsername: String?,
    status: LoadingState,
    onSubmit: (url: String, username: String, passwordOrApiKey: String, method: SeerrAuthMethod) -> Unit,
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
            ) {
                AddSeerrServerUsername(
                    onSubmit = { url, username, password ->
                        onSubmit.invoke(url, username, password, auth)
                    },
                    username = currentUsername ?: "",
                    status = status,
                )
            }
        }

        SeerrAuthMethod.API_KEY -> {
            BasicDialog(
                onDismissRequest = { authMethod = null },
            ) {
                AddSeerrServerApiKey(
                    onSubmit = { url, apiKey ->
                        onSubmit.invoke(url, "", apiKey, SeerrAuthMethod.API_KEY)
                    },
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
