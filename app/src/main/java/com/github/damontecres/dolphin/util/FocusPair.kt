package com.github.damontecres.dolphin.util

import androidx.compose.ui.focus.FocusRequester

data class FocusPair(
    val row: Int,
    val column: Int,
    val focusRequester: FocusRequester,
)
