package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldDefaults.Container
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.google.protobuf.value

/**
 * A modified [BasicTextField] that looks & fits better with TV material controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTextBox(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    height: Dp = 40.dp,
    isInputValid: (String) -> Boolean = { true },
    supportingText: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    // From ButtonDefaults
    val colors =
        TextFieldDefaults.colors(
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            focusedTextColor = MaterialTheme.colorScheme.inverseOnSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
            focusedIndicatorColor = MaterialTheme.colorScheme.border,
            unfocusedLabelColor = Color.Unspecified,
        )
    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
        BasicTextField(
            value = value,
            modifier =
                modifier
                    .defaultMinSize(
                        minWidth = TextFieldDefaults.MinWidth,
                        minHeight = height,
                    ).height(height),
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = MaterialTheme.typography.bodyLarge.merge(MaterialTheme.colorScheme.onPrimaryContainer),
            cursorBrush = SolidColor(colors.cursorColor),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            singleLine = true,
            maxLines = 1,
            minLines = 1,
            visualTransformation =
                if (keyboardOptions.keyboardType == KeyboardType.Password ||
                    keyboardOptions.keyboardType == KeyboardType.NumberPassword
                ) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
            decorationBox =
                @Composable { innerTextField ->
                    // places leading icon, text field with label and placeholder, trailing icon
                    TextFieldDefaults.DecorationBox(
                        value = value,
                        visualTransformation =
                            if (keyboardOptions.keyboardType == KeyboardType.Password ||
                                keyboardOptions.keyboardType == KeyboardType.NumberPassword
                            ) {
                                PasswordVisualTransformation()
                            } else {
                                VisualTransformation.None
                            },
                        innerTextField = innerTextField,
                        placeholder = placeholder,
                        label = null,
                        leadingIcon = leadingIcon,
                        trailingIcon = null,
                        prefix = null,
                        suffix = null,
                        supportingText = supportingText,
                        shape = CircleShape,
                        singleLine = true,
                        enabled = enabled,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = colors,
                        contentPadding =
                            PaddingValues(
                                horizontal = 8.dp,
                                vertical = 10.dp,
                            ),
                        container = {
                            Container(
                                enabled = enabled,
                                isError = !isInputValid.invoke(value),
                                interactionSource = interactionSource,
                                modifier = Modifier,
                                colors = colors,
                                shape = CircleShape,
                                focusedIndicatorLineThickness = 4.dp,
                                unfocusedIndicatorLineThickness = 0.dp,
                            )
                        },
                    )
                },
        )
    }
}

/**
 * And [EditTextBox] styles for searches
 */
@Composable
fun SearchEditTextBox(
    value: String,
    onValueChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    height: Dp = 40.dp,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    EditTextBox(
        value,
        onValueChange,
        modifier,
        keyboardOptions =
            KeyboardOptions(
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
        keyboardActions =
            KeyboardActions(
                onSearch = {
                    onSearchClick.invoke()
                    this.defaultKeyboardAction(ImeAction.Search)
                },
            ),
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.search),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        enabled = enabled,
        readOnly = readOnly,
        height = height,
        interactionSource = interactionSource,
    )
}

@PreviewTvSpec
@Composable
private fun EditTextBoxPreview() {
    WholphinTheme {
        Column {
            EditTextBox(
                value = "string",
                onValueChange = {},
            )
            SearchEditTextBox(
                value = "search query",
                onValueChange = {},
                onSearchClick = { },
            )
        }
    }
}
