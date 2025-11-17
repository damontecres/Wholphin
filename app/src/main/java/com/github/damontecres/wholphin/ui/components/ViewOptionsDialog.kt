package com.github.damontecres.wholphin.ui.components

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppChoicePreference
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppSliderPreference
import com.github.damontecres.wholphin.preferences.AppSwitchPreference
import com.github.damontecres.wholphin.preferences.PrefContentScale
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.preferences.ComposablePreference
import com.github.damontecres.wholphin.ui.tryRequestFocus

data class ViewOptions(
    val columns: Int = 6,
    val spacing: Int = 16,
    val contentScale: PrefContentScale = PrefContentScale.FIT,
    val aspectRatio: AspectRatio = AspectRatio.TALL,
    val showDetails: Boolean = false,
)

@Composable
fun ViewOptionsDialog(
    viewOptions: ViewOptions,
    onDismissRequest: () -> Unit,
    onViewOptionsChange: (ViewOptions) -> Unit,
) {
    val settings = listOf(ViewOptionsColumns, ViewOptionsSpacing, ViewOptionsContentScale, ViewOptionsAspectRatio, ViewOptionsDetailHeader)
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .width(256.dp)
                    .heightIn(max = 380.dp)
                    .focusRequester(focusRequester)
                    .background(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                        shape = RoundedCornerShape(8.dp),
                    ),
        ) {
            items(settings) { pref ->
                pref as AppPreference<ViewOptions, Any>
                val interactionSource = remember { MutableInteractionSource() }
                val value = pref.getter.invoke(viewOptions)
                ComposablePreference(
                    preference = pref,
                    value = value,
                    onNavigate = {},
                    onValueChange = { newValue ->
                        onViewOptionsChange.invoke(pref.setter(viewOptions, newValue))
                    },
                    interactionSource = interactionSource,
                    modifier = Modifier,
                )
            }
        }
    }
}

val ViewOptionsColumns =
    AppSliderPreference<ViewOptions>(
        title = R.string.columns,
        defaultValue = 6,
        min = 1,
        max = 10,
        interval = 1,
        getter = { it.columns.toLong() },
        setter = { prefs, value -> prefs.copy(columns = value.toInt()) },
    )
val ViewOptionsSpacing =
    AppSliderPreference<ViewOptions>(
        title = R.string.spacing,
        defaultValue = 16,
        min = 0,
        max = 32,
        interval = 2,
        getter = { it.spacing.toLong() },
        setter = { prefs, value -> prefs.copy(spacing = value.toInt()) },
    )

val ViewOptionsContentScale =
    AppChoicePreference<ViewOptions, PrefContentScale>(
        title = R.string.global_content_scale,
        defaultValue = PrefContentScale.FIT,
        displayValues = R.array.content_scale,
        getter = { it.contentScale },
        setter = { viewOptions, value -> viewOptions.copy(contentScale = value) },
        indexToValue = { PrefContentScale.forNumber(it) },
        valueToIndex = { it.number },
    )

val ViewOptionsAspectRatio =
    AppChoicePreference<ViewOptions, AspectRatio>(
        title = R.string.aspect_ratio,
        defaultValue = AspectRatio.TALL,
        displayValues = R.array.aspect_ratios,
        getter = { it.aspectRatio },
        setter = { viewOptions, value -> viewOptions.copy(aspectRatio = value) },
        indexToValue = { AspectRatio.entries[it] },
        valueToIndex = { it.ordinal },
    )

val ViewOptionsDetailHeader =
    AppSwitchPreference<ViewOptions>(
        title = R.string.show_details,
        defaultValue = false,
        getter = { it.showDetails },
        setter = { vo, value -> vo.copy(showDetails = value) },
    )
