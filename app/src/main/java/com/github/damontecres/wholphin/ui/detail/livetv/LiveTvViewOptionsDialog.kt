package com.github.damontecres.wholphin.ui.detail.livetv

import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.liveTvPreferences
import com.github.damontecres.wholphin.ui.preferences.ComposablePreference
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
fun LiveTvViewOptionsDialog(
    preferences: AppPreferences,
    onDismissRequest: () -> Unit,
    onViewOptionsChange: (AppPreferences) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    val columnState = rememberLazyListState()
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
    ) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.let { window ->
            window.setGravity(Gravity.END)
            window.setDimAmount(0f)
        }
        LazyColumn(
            state = columnState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier =
                Modifier
                    .width(256.dp)
                    .heightIn(max = 380.dp)
                    .focusRequester(focusRequester)
                    .background(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                        shape = RoundedCornerShape(8.dp),
                    ),
        ) {
            stickyHeader {
                Text(
                    text = stringResource(R.string.view_options),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(liveTvPreferences) { pref ->
                pref as AppPreference<AppPreferences, Any>
                val interactionSource = remember { MutableInteractionSource() }
                val value = pref.getter.invoke(preferences)
                ComposablePreference(
                    preference = pref,
                    value = value,
                    onNavigate = {},
                    onValueChange = { newValue ->
                        onViewOptionsChange.invoke(pref.setter(preferences, newValue))
                    },
                    interactionSource = interactionSource,
                    modifier = Modifier,
                )
            }
        }
    }
}
