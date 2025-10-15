package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.tv.material3.ListItem
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults

@Composable
fun SwitchPreference(
    title: String,
    value: Boolean,
    onClick: () -> Unit,
    summaryOn: String?,
    summaryOff: String?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = SwitchPreference(
    title = title,
    value = value,
    onClick = onClick,
    modifier = modifier,
    summary = if (value) summaryOn else summaryOff,
    onLongClick = onLongClick,
    interactionSource = interactionSource,
)

@Composable
fun SwitchPreference(
    title: String,
    value: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    ListItem(
        selected = false,
        onClick = onClick,
        onLongClick = onLongClick,
        headlineContent = {
            PreferenceTitle(title)
        },
        supportingContent = {
            PreferenceSummary(summary)
        },
        trailingContent = {
            Switch(
                checked = value,
                onCheckedChange = { onClick.invoke() },
                colors =
                    SwitchDefaults
                        .colors(),
            )
        },
        interactionSource = interactionSource,
        modifier = modifier,
    )
}
