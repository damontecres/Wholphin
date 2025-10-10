package com.github.damontecres.dolphin.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.ui.DefaultButtonPadding
import com.github.damontecres.dolphin.ui.FontAwesome
import com.github.damontecres.dolphin.ui.ifElse
import kotlin.time.Duration

/**
 * An icon button typically used in a row for playing media
 */
@Composable
fun PlayButton(
    @StringRes title: Int,
    resume: Duration,
    icon: ImageVector,
    onClick: (position: Duration) -> Unit,
    modifier: Modifier = Modifier,
    mirrorIcon: Boolean = false,
) {
    Button(
        onClick = { onClick.invoke(resume) },
        modifier = modifier,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.ifElse(mirrorIcon, Modifier.graphicsLayer { scaleX = -1f }),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

/**
 * An icon button typically used in a row for playing media
 *
 * Only shows the icon until focused when it expands to show the title
 */
@Composable
fun ExpandablePlayButton(
    @StringRes title: Int,
    resume: Duration,
    icon: ImageVector,
    onClick: (position: Duration) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    mirrorIcon: Boolean = false,
) {
    val isFocused = interactionSource.collectIsFocusedAsState().value
    Button(
        onClick = { onClick.invoke(resume) },
        modifier = modifier,
        contentPadding = DefaultButtonPadding,
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.ifElse(mirrorIcon, Modifier.graphicsLayer { scaleX = -1f }),
        )
        AnimatedVisibility(isFocused) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

/**
 * Similar to [ExpandablePlayButton], but uses a [FontAwesome] string instead of an Icon
 */
@Composable
fun ExpandableFaButton(
    @StringRes title: Int,
    @StringRes iconStringRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val isFocused = interactionSource.collectIsFocusedAsState().value
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        contentPadding = DefaultButtonPadding,
        interactionSource = interactionSource,
    ) {
        Text(
            text = stringResource(iconStringRes),
            style = MaterialTheme.typography.titleSmall,
            fontSize = 16.sp,
            fontFamily = FontAwesome,
            textAlign = TextAlign.Center,
            modifier = Modifier,
        )
        AnimatedVisibility(isFocused) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}
