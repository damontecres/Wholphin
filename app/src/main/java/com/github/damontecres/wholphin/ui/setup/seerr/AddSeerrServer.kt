package com.github.damontecres.wholphin.ui.setup.seerr

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
fun AddSeerrServerContent(
    onSubmit: (url: String, apiKey: String) -> Unit,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    var error by remember(errorMessage) { mutableStateOf(errorMessage) }
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
