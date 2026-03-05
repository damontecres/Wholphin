package com.github.damontecres.wholphin.ui.setup.seerr

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.github.damontecres.wholphin.R
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
