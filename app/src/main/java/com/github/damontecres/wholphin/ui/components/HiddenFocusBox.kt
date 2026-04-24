package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp

/**
 * An invisible, no size Box which can be focused.
 *
 * Used to allow for transitioning to restore hidden content
 */
@Composable
fun HiddenFocusBox(
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocus: () -> Unit,
) = Box(
    modifier =
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.isFocused) onFocus.invoke()
            }.focusable(),
)
