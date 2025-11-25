package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
fun CircularProgress(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.border,
            modifier =
                Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
        )
    }
}

/**
 * Fill the space with a loading indicator and take focus
 */
@Composable
fun LoadingPage(
    modifier: Modifier = Modifier,
    focusEnabled: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable(focusEnabled),
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.border,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
        )
    }
}
