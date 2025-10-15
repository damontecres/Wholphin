package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.isNotNullOrBlank

@Composable
fun TableRowComposable(
    row: TableRow,
    modifier: Modifier = Modifier,
    keyWeight: Float = .3f,
    valueWeight: Float = .7f,
    focusable: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val background =
        if (isFocused && row.onClick != null) {
            MaterialTheme.colorScheme.border.copy(alpha = .75f)
        } else if (isFocused) {
            MaterialTheme.colorScheme.onBackground.copy(alpha = .25f)
        } else {
            Color.Unspecified
        }
    Row(
        modifier
            .background(background)
            .ifElse(
                row.onClick != null,
                ifTrueModifier =
                    Modifier
                        .clickable(
                            enabled = true,
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = { row.onClick?.invoke() },
                        ),
                ifFalseModifier =
                    Modifier.focusable(
                        enabled = focusable, // TODO, this allows scrolling
                        interactionSource = interactionSource,
                    ),
            ),
    ) {
        val keyModifier =
            Modifier
                .weight(keyWeight)
        val valueModifier =
            Modifier
                .weight(valueWeight)
        ProvideTextStyle(textStyle) {
            Box(modifier = keyModifier) {
                row.key.invoke(this, Modifier.padding(4.dp))
            }
            Box(modifier = valueModifier) {
                row.value.invoke(this, Modifier.padding(4.dp))
            }
        }
    }
}

data class TableRow(
    val key: @Composable BoxScope.(modifier: Modifier) -> Unit,
    val value: @Composable BoxScope.(modifier: Modifier) -> Unit,
    val onClick: (() -> Unit)? = null,
) {
    constructor(key: String, value: String, onClick: (() -> Unit)? = null) : this(
        { modifier: Modifier -> Text(text = "$key:", modifier = modifier) },
        { modifier: Modifier -> Text(text = value, modifier = modifier) },
        onClick,
    )

    companion object {
        @Composable
        fun from(
            @StringRes keyStringId: Int,
            value: String?,
            onClick: (() -> Unit)? = null,
        ): TableRow? =
            if (value.isNotNullOrBlank()) {
                TableRow(stringResource(keyStringId), value, onClick)
            } else {
                null
            }

        fun from(
            context: Context,
            @StringRes keyStringId: Int,
            value: String?,
            onClick: (() -> Unit)? = null,
        ): TableRow? =
            if (value.isNotNullOrBlank()) {
                TableRow(context.getString(keyStringId), value, onClick)
            } else {
                null
            }

        fun from(
            key: String,
            value: String?,
            onClick: (() -> Unit)? = null,
        ): TableRow? =
            if (value.isNotNullOrBlank()) {
                TableRow(key, value, onClick)
            } else {
                null
            }
    }
}
