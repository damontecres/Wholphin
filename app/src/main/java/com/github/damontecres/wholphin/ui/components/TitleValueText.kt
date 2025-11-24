/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.playOnClickSound

/**
 * An optionally clickable composable that displays a Key-Value pair vertically
 */
@Composable
fun TitleValueText(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    playSoundOnFocus: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    valueTextOverflow: TextOverflow = TextOverflow.Ellipsis,
) {
    MaybeClickColumn(
        onClick = onClick,
        onLongClick = onLongClick,
        playSoundOnFocus = playSoundOnFocus,
        modifier = modifier,
    ) {
        Text(
            modifier = Modifier.alpha(0.8f),
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = valueTextOverflow,
        )
    }
}

@Composable
private fun MaybeClickColumn(
    playSoundOnFocus: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null || onLongClick != null) {
        val context = LocalContext.current
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()
        if (playSoundOnFocus) {
            LaunchedEffect(isFocused) {
                if (isFocused) playOnClickSound(context)
            }
        }
        val bgColor =
            if (isFocused) {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f)
            } else {
                Color.Unspecified
            }

        Column(
            content = content,
            modifier =
                modifier
                    .background(bgColor, shape = RoundedCornerShape(4.dp))
                    .ifElse(isFocused, Modifier.scale(1.1f))
                    .combinedClickable(
                        enabled = true,
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = {
                            if (playSoundOnFocus) playOnClickSound(context)
                            onClick?.invoke()
                        },
                        onLongClick = {
                            if (playSoundOnFocus) playOnClickSound(context)
                            onLongClick?.invoke()
                        },
                    ),
        )
    } else {
        Column(content = content, modifier = modifier)
    }
}
