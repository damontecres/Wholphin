package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.theme.WholphinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTextBox(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    maxLines: Int = 1,
    isInputValid: (String) -> Boolean = { true },
    isPassword: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val colors =
        TextFieldDefaults.colors(
            unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            focusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
            errorTextColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    val textColor =
        when {
            !isInputValid(state.text.toString()) -> colors.errorTextColor
            focused -> colors.focusedTextColor
            !enabled -> colors.disabledTextColor
            !focused -> colors.unfocusedTextColor
            else -> colors.unfocusedTextColor
        }
    val lineLimits =
        remember {
            if (maxLines == 1) {
                TextFieldLineLimits.SingleLine
            } else {
                TextFieldLineLimits.MultiLine(
                    maxLines,
                    maxLines,
                )
            }
        }
    val decorator =
        remember {
            TextFieldDecorator { innerTextField: @Composable () -> Unit ->
                val containerColor =
                    when {
                        !isInputValid(state.text.toString()) -> colors.errorContainerColor
                        focused -> colors.focusedContainerColor
                        !enabled -> colors.disabledContainerColor
                        !focused -> colors.unfocusedContainerColor
                        else -> colors.unfocusedContainerColor
                    }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .background(
                                containerColor,
                                shape = if (maxLines > 1) RoundedCornerShape(8.dp) else CircleShape,
                            ).padding(8.dp),
                ) {
                    CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                            leadingIcon?.invoke()
                            innerTextField.invoke()
                        }
                    }
                }
            }
        }

    if (isPassword) {
        BasicSecureTextField(
            state = state,
            modifier =
                modifier.defaultMinSize(
                    minWidth = TextFieldDefaults.MinWidth,
                ),
            enabled = enabled,
            readOnly = readOnly,
            keyboardOptions = keyboardOptions,
            onKeyboardAction = onKeyboardAction,
            textStyle = MaterialTheme.typography.bodyLarge.merge(textColor),
            interactionSource = interactionSource,
            cursorBrush = SolidColor(colors.cursorColor),
            decorator = decorator,
        )
    } else {
        BasicTextField(
            state = state,
            modifier =
                modifier.defaultMinSize(
                    minWidth = TextFieldDefaults.MinWidth,
                ),
            enabled = enabled,
            readOnly = readOnly,
            keyboardOptions = keyboardOptions,
            onKeyboardAction = onKeyboardAction,
            textStyle = MaterialTheme.typography.bodyLarge.merge(textColor),
            interactionSource = interactionSource,
            lineLimits = lineLimits,
            cursorBrush = SolidColor(colors.cursorColor),
            decorator = decorator,
        )
    }
}

/**
 * And [EditTextBox] styles for searches
 */
@Composable
fun SearchEditTextBox(
    state: TextFieldState,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    EditTextBox(
        state = state,
        modifier = modifier,
        keyboardOptions =
            KeyboardOptions(
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
        onKeyboardAction = {
            onSearchClick.invoke()
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.search),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        enabled = enabled,
        readOnly = readOnly,
    )
}

@PreviewTvSpec
@Composable
private fun EditTextBoxPreview() {
    WholphinTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EditTextBox(
                state = rememberTextFieldState("string"),
            )
            SearchEditTextBox(
                state = rememberTextFieldState("search query"),
                onSearchClick = { },
            )
            EditTextBox(
                state = rememberTextFieldState("string\nstring\nstring"),
                maxLines = 5,
//                height = 88.dp,
            )
            EditTextBox(
                state = rememberTextFieldState("search"),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                },
                isInputValid = { true },
                modifier = Modifier.width(280.dp),
            )
            EditTextBox(
                state = rememberTextFieldState("password"),
                isPassword = true,
            )
        }
    }
}
