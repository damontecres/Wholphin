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
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.components.EditTextBox

@Composable
fun StringInputDialog(
    input: StringInput,
    onSave: (String) -> Unit,
    onDismissRequest: () -> Unit,
    elevation: Dp = 3.dp,
) {
    var mutableValue by remember { mutableStateOf(input.value ?: "") }
    val onDone = {
        onSave.invoke(mutableValue)
    }
    var showConfirm by remember { mutableStateOf(false) }
    Dialog(
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
        onDismissRequest = {
            if (input.confirmDiscard && mutableValue != input.value) {
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
                EditTextBox(
                    value = mutableValue,
                    onValueChange = { mutableValue = it },
                    keyboardOptions = input.keyboardOptions,
                    keyboardActions =
                        KeyboardActions(
                            onDone = { onDone.invoke() },
                        ),
                    leadingIcon = null,
                    isInputValid = { true },
                    maxLines = input.maxLines,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = onDismissRequest,
                        modifier = Modifier,
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                        )
                    }
                    Button(
                        onClick = onDone,
                        modifier = Modifier,
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                        )
                    }
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
