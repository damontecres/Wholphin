package com.github.damontecres.dolphin.ui.components

import android.widget.Toast
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text

@Composable
fun SwitchWithLabel(
    label: String,
    checked: Boolean,
    onStateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier =
            modifier
                .clip(shape = RoundedCornerShape(20.dp))
                .background(
                    if (isFocused) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    },
                ).clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Switch,
                    onClick = {
                        if (enabled) {
                            onStateChange(!checked)
                        } else {
                            // TODO there are other uses, so shouldn't hardcode this toast
                            Toast
                                .makeText(context, "Item has no children", Toast.LENGTH_SHORT)
                                .show()
                        }
                    },
                ).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (isFocused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )

        Spacer(modifier = Modifier.padding(start = 8.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = {
                onStateChange(!checked)
            },
        )
    }
}
