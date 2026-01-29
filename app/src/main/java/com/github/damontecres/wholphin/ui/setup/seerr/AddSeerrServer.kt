package com.github.damontecres.wholphin.ui.setup.seerr

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState

@Composable
fun AddSeerrServerApiKey(
    onSubmit: (url: String, apiKey: String) -> Unit,
    status: LoadingState,
    modifier: Modifier = Modifier,
) {
    var error by remember(status) { mutableStateOf((status as? LoadingState.Error)?.localizedMessage) }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .focusGroup()
                .padding(16.dp)
                .wrapContentSize(),
    ) {
        var url by remember { mutableStateOf("") }
        var apiKey by remember { mutableStateOf("") }

        val focusRequester = remember { FocusRequester() }
        val passwordFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
        Text(
            text = "Enter URL & API Key",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = "URL",
                modifier = Modifier.padding(end = 8.dp),
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
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onNext = {
                            passwordFocusRequester.tryRequestFocus()
                        },
                    ),
                isInputValid = { true },
                modifier = Modifier.focusRequester(focusRequester),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = "API Key",
                modifier = Modifier.padding(end = 8.dp),
            )
            EditTextBox(
                value = apiKey,
                onValueChange = {
                    error = null
                    apiKey = it
                },
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onGo = { onSubmit.invoke(url, apiKey) },
                    ),
                isInputValid = { true },
                modifier = Modifier.focusRequester(passwordFocusRequester),
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
            onClick = { onSubmit.invoke(url, apiKey) },
            enabled = error.isNullOrBlank() && url.isNotNullOrBlank() && apiKey.isNotNullOrBlank(),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
fun AddSeerrServerUsername(
    onSubmit: (url: String, username: String, password: String) -> Unit,
    username: String,
    status: LoadingState,
    modifier: Modifier = Modifier,
) {
    var error by remember(status) { mutableStateOf((status as? LoadingState.Error)?.localizedMessage) }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .focusGroup()
                .padding(16.dp)
                .wrapContentSize(),
    ) {
        var url by remember { mutableStateOf("") }
        var username by remember { mutableStateOf(username) }
        var password by remember { mutableStateOf("") }

        val focusRequester = remember { FocusRequester() }
        val usernameFocusRequester = remember { FocusRequester() }
        val passwordFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
        Text(
            text = stringResource(R.string.username_or_password),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        val labelWidth = 90.dp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = stringResource(R.string.url),
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .width(labelWidth)
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
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onNext = {
                            usernameFocusRequester.tryRequestFocus()
                        },
                    ),
                isInputValid = { true },
                modifier = Modifier.focusRequester(focusRequester),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = stringResource(R.string.username),
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .width(labelWidth)
                        .padding(end = 8.dp),
            )
            EditTextBox(
                value = username,
                onValueChange = {
                    error = null
                    username = it
                },
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
                isInputValid = { true },
                modifier = Modifier.focusRequester(usernameFocusRequester),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = stringResource(R.string.password),
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .width(labelWidth)
                        .padding(end = 8.dp),
            )
            EditTextBox(
                value = password,
                onValueChange = {
                    error = null
                    password = it
                },
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onGo = { onSubmit.invoke(url, username, password) },
                    ),
                isInputValid = { true },
                modifier = Modifier.focusRequester(passwordFocusRequester),
            )
        }
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Button(
            onClick = { onSubmit.invoke(url, username, password) },
            enabled =
                error.isNullOrBlank() && url.isNotNullOrBlank() && username.isNotNullOrBlank() &&
                    status != LoadingState.Loading,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            if (status != LoadingState.Loading) {
                Text(text = stringResource(R.string.submit))
            } else {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.border,
                    modifier =
                        Modifier.size(24.dp),
                )
            }
        }
    }
}

@PreviewTvSpec
@Composable
private fun AddSeerrServerUsernamePreview() {
    WholphinTheme {
        AddSeerrServerUsername(
            onSubmit = { string: String, string1: String, string2: String -> },
            username = "test",
            status = LoadingState.Pending,
            modifier = Modifier,
        )
    }
}
