package com.github.damontecres.wholphin.ui.setup.streamystats

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState

@Composable
fun StreamystatsSettingsDialog(
    currentUrl: String?,
    status: LoadingState,
    onSubmit: (url: String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    LaunchedEffect(status) {
        if (status is LoadingState.Success) {
            onDismissRequest.invoke()
        }
    }
    BasicDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        StreamystatsSettingsDialogContent(
            currentUrl = currentUrl.orEmpty(),
            status = status,
            onSubmit = onSubmit,
            modifier = Modifier.widthIn(min = 360.dp),
        )
    }
}

@Composable
private fun StreamystatsSettingsDialogContent(
    currentUrl: String,
    status: LoadingState,
    onSubmit: (url: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var error by remember(status) { mutableStateOf((status as? LoadingState.Error)?.localizedMessage) }
    var url by remember(currentUrl) { mutableStateOf(currentUrl) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .focusGroup()
                .padding(16.dp)
                .wrapContentSize(),
    ) {
        Text(
            text = stringResource(R.string.streamystats_integration),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = stringResource(R.string.url),
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .width(90.dp)
                        .padding(end = 8.dp),
            )
            EditTextBox(
                value = url,
                onValueChange = {
                    error = null
                    url = it
                },
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                keyboardActions = KeyboardActions(onGo = { onSubmit.invoke(url) }),
                isInputValid = { true },
                modifier = Modifier.focusRequester(focusRequester),
            )
        }
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        TextButton(
            stringRes = R.string.submit,
            onClick = { onSubmit.invoke(url) },
            enabled = status !is LoadingState.Loading && url.isNotNullOrBlank(),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
