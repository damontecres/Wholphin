package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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
    if (focusEnabled) {
        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    }
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

/**
 * Fill the space and take focus, showing a loading indicator after the specified delay
 */
@Composable
fun DelayedLoadingPage(
    modifier: Modifier = Modifier,
    focusEnabled: Boolean = true,
    delay: Duration = 300.milliseconds,
) {
    val focusRequester = remember { FocusRequester() }
    if (focusEnabled) {
        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    }
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delay)
        show = true
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable(focusEnabled),
    ) {
        if (show) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.border,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(48.dp),
            )
        }
    }
}

@Composable
fun BoxScope.CornerLoadingIndicator(
    showClock: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .padding(top = if (showClock) 40.dp else 20.dp, start = 20.dp)
                .size(48.dp)
                .align(Alignment.TopEnd),
    ) {
        CircularProgress(Modifier.fillMaxSize())
    }
}
