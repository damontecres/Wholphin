package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.surfaceColorAtElevation

@Composable
fun TvDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    elevation: Dp = 3.dp,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val backgroundColor =
        if (focused) {
            MaterialTheme.colorScheme.inverseSurface
        } else {
            MaterialTheme.colorScheme.surfaceColorAtElevation(elevation)
        }
    val contentColor =
        if (focused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        DropdownMenuItem(
            enabled = enabled,
            colors =
                MenuDefaults.itemColors(
                    textColor = contentColor,
                    leadingIconColor = contentColor,
                    trailingIconColor = contentColor,
                ),
            leadingIcon = leadingIcon,
            text = text,
            trailingIcon = trailingIcon,
            onClick = onClick,
            interactionSource = interactionSource,
            contentPadding = contentPadding,
            modifier =
                modifier.background(backgroundColor),
        )
    }
}
