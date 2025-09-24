package com.github.damontecres.dolphin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.dolphin.ui.ifElse

@Composable
fun CircularProgress(
    modifier: Modifier = Modifier,
    fillMaxSize: Boolean = true,
) {
    Box(modifier = modifier.ifElse(fillMaxSize, Modifier.fillMaxSize())) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.border,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
