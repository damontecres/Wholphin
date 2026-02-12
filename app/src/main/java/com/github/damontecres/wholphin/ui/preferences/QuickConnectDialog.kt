package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.components.TextButton

@Composable
fun QuickConnectDialog(
    onSubmit: (String) -> Unit,
    onDismissRequest: () -> Unit,
    elevation: Dp = 3.dp,
) {
    var code by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val isValidCode: (String) -> Boolean = { value ->
        val trimmed = value.trim()
        trimmed.length == 6 && trimmed.all { it.isDigit() }
    }

    val onSubmitCode = {
        if (isValidCode(code)) {
            showError = false
            onSubmit(code.trim())
        } else {
            showError = true
        }
    }

    Dialog(
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
        onDismissRequest = onDismissRequest,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .padding(16.dp)
                    .width(360.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(elevation),
                        shape = RoundedCornerShape(8.dp),
                    ),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.quick_connect_code),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )

                EditTextBox(
                    value = code,
                    onValueChange = {
                        code = it
                        showError = false
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                onSubmitCode()
                            },
                        ),
                    isInputValid = { value ->
                        !showError || isValidCode(value)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (showError) {
                    Text(
                        text = stringResource(R.string.quick_connect_code_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                TextButton(
                    stringRes = R.string.submit,
                    onClick = onSubmitCode,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}
