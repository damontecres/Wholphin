package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.components.TextButton

@Composable
fun StringInputDialog(
    input: StringInput,
    onSave: (String) -> Unit,
    onDismissRequest: () -> Unit,
    elevation: Dp = 3.dp,
) {
    val mutableValue = rememberTextFieldState(input.value ?: "")
    var mutableText by remember { mutableStateOf(input.value ?: "") }
//    var mutableValue by remember { mutableStateOf(input.value ?: "") }
    val onDone = {
        if (input.maxLines > 1) {
            onSave.invoke(mutableValue.text.toString())
        } else {
            onSave.invoke(mutableText)
        }
    }
    var showConfirm by remember { mutableStateOf(false) }
    Dialog(
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
        onDismissRequest = {
            if (input.confirmDiscard && mutableValue.text.toString() != input.value) {
                showConfirm = true
            } else {
                onDismissRequest.invoke()
            }
        },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .padding(16.dp)
                    .width(512.dp)
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
                    text = input.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier,
                )
                if (input.maxLines > 1) {
                    EditTextBox(
                        state = mutableValue,
                        keyboardOptions = input.keyboardOptions,
                        onKeyboardAction = {
                            onDone.invoke()
                        },
                        leadingIcon = null,
                        isInputValid = { true },
                        maxLines = input.maxLines,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    EditTextBox(
                        value = mutableText,
                        onValueChange = { mutableText = it },
                        keyboardOptions = input.keyboardOptions,
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    onDone.invoke()
                                },
                            ),
                        leadingIcon = null,
                        isInputValid = { true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(
                        stringRes = R.string.cancel,
                        onClick = onDismissRequest,
                        modifier = Modifier,
                    )
                    TextButton(
                        stringRes = R.string.save,
                        onClick = onDone,
                        modifier = Modifier,
                    )
                }
            }
        }
    }

    if (showConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.discard_change),
            body = null,
            onCancel = {
                showConfirm = false
            },
            onConfirm = {
                showConfirm = false
                onDismissRequest.invoke()
            },
            elevation = elevation * 2,
        )
    }
}
