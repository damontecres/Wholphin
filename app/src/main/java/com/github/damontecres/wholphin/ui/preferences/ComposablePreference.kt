package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.preferences.AppChoicePreference
import com.github.damontecres.wholphin.preferences.AppClickablePreference
import com.github.damontecres.wholphin.preferences.AppDestinationPreference
import com.github.damontecres.wholphin.preferences.AppMultiChoicePreference
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppSliderPreference
import com.github.damontecres.wholphin.preferences.AppStringPreference
import com.github.damontecres.wholphin.preferences.AppSwitchPreference
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.launch
import java.io.File
import java.util.SortedSet

@Suppress("UNCHECKED_CAST")
@Composable
fun <T> ComposablePreference(
    preference: AppPreference<*, T>,
    value: T?,
    onValueChange: (T) -> Unit,
    onNavigate: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClickPreference: (AppClickablePreference<*>) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dialogParams by remember { mutableStateOf<DialogParams?>(null) }
    var showStringDialog by remember { mutableStateOf<StringInput?>(null) }

    val title = stringResource(preference.title)

    val onLongClick: () -> Unit = {
        scope.launch(ExceptionHandler()) {
            when (preference) {
                else -> null
            }
        }
    }

    when (preference) {
        AppPreference.MpvConfFile -> {
            ClickPreference(
                title = title,
                onClick = {
                    val confFile = File(context.filesDir, "mpv.conf")
                    val contents = if (confFile.exists()) confFile.readText() else ""
                    showStringDialog =
                        StringInput(
                            title = title,
                            value = contents,
                            keyboardOptions =
                                KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.None,
                                ),
                            onSubmit = {
                                confFile.writeText(it)
                                showStringDialog = null
                            },
                            maxLines = 10,
                            confirmDiscard = true,
                        )
                },
                summary = preference.summary(context, value),
                interactionSource = interactionSource,
                modifier = modifier,
            )
        }

        is AppDestinationPreference -> {
            ClickPreference(
                title = title,
                onClick = {
                    onNavigate.invoke(preference.destination)
                },
                summary = preference.summary(context, value),
                interactionSource = interactionSource,
                modifier = modifier,
            )
        }

        is AppClickablePreference -> {
            ClickPreference(
                title = title,
                onClick = { onClickPreference.invoke(preference) },
                onLongClick = onLongClick,
                summary = preference.summary(context, value),
                interactionSource = interactionSource,
                modifier = modifier,
            )
        }

        is AppSwitchPreference -> {
            SwitchPreference(
                title = title,
                value = value as Boolean,
                onClick = { onValueChange.invoke(!value as T) },
                summary = preference.summary(context, value),
                interactionSource = interactionSource,
                modifier = modifier,
            )
        }

        is AppStringPreference -> {
            ClickPreference(
                title = title,
                onClick = {
                    showStringDialog =
                        StringInput(
                            title = title,
                            value = value as String?,
                            keyboardOptions =
                                KeyboardOptions(
                                    autoCorrectEnabled = false,
//                                    keyboardType =
//                                        if (preference == AppPreference.UpdateUrl) {
//                                            KeyboardType.Uri
//                                        } else {
//                                            KeyboardType.Unspecified
//                                        },
                                    imeAction = ImeAction.Done,
                                ),
                            onSubmit = { input ->
                                onValueChange.invoke(input as T)
                                showStringDialog = null
                            },
                        )
                },
                summary =
                    preference.summary(context, value)
                        ?: preference.summary?.let { stringResource(it) },
                interactionSource = interactionSource,
                modifier = modifier,
            )
        }

        is AppChoicePreference -> {
            val values = stringArrayResource(preference.displayValues).toList()
            val subtitles =
                preference.subtitles?.let { stringArrayResource(preference.subtitles).toList() }
            val summary =
                preference.summary?.let { stringResource(it) }
                    ?: preference.summary(context, value)
                    ?: preference
                        .valueToIndex(value as T)
                        .let { values[it] }
            val selectedIndex = remember(value) { preference.valueToIndex.invoke(value as T) }
            ClickPreference(
                title = title,
                summary = summary,
                onClick = {
                    dialogParams =
                        DialogParams(
                            title = title,
                            fromLongClick = false,
                            items =
                                values.mapIndexed { index, it ->
                                    DialogItem(
                                        headlineContent = {
                                            Text(it)
                                        },
                                        leadingContent = {
                                            if (index == selectedIndex) {
                                                Icon(
                                                    imageVector = Icons.Default.Done,
                                                    contentDescription = "selected",
                                                )
                                            }
                                        },
                                        supportingContent = {
                                            subtitles?.let {
                                                val text = subtitles[index]
                                                if (text.isNotNullOrBlank()) {
                                                    Text(text)
                                                }
                                            }
                                        },
                                        onClick = {
                                            onValueChange(preference.indexToValue(index))
                                            dialogParams = null
                                        },
                                    )
                                },
                        )
                },
                interactionSource = interactionSource,
                modifier = modifier,
            )
        }

        is AppMultiChoicePreference<*, *> -> {
            val values = stringArrayResource(preference.displayValues).toSortedSet()
            val summary =
                preference.summary?.let { stringResource(it) }
                    ?: preference.summary(context, value)
            val selectedValues =
                remember {
                    val list = mutableStateSetOf<Any>()
                    list.addAll(value as List<Any>)
                    list
                }
            MultiChoicePreference(
                possibleValues = values as SortedSet<Any>,
                selectedValues = selectedValues,
                title = title,
                summary = summary,
                onValueChange = {
                    onValueChange.invoke(selectedValues.toList() as T)
                },
            )
        }

        is AppSliderPreference -> {
            val summary =
                preference.summary(context, value)
                    ?: preference.summary?.let { stringResource(it) }
            SliderPreference(
                preference = preference,
                title = title,
                summary = summary,
                value = value as Long,
                onChange = { onValueChange(it as T) },
                summaryBelow = false,
                interactionSource = interactionSource,
                modifier = modifier,
            )
        }
    }

    val dialogBackgroundColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)

    AnimatedVisibility(dialogParams != null) {
        dialogParams?.let {
            DialogPopup(
                showDialog = true,
                title = it.title,
                dialogItems = it.items,
                onDismissRequest = { dialogParams = null },
                waitToLoad = false,
                dismissOnClick = false,
            )
        }
    }
    AnimatedVisibility(showStringDialog != null) {
        showStringDialog?.let { input ->
            StringInputDialog(
                input = input,
                onSave = {
                    input.onSubmit.invoke(it)
                },
                onDismissRequest = { showStringDialog = null },
                elevation = 3.dp,
            )
        }
    }
}

val PreferenceTitleStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.titleSmall

val PreferenceSummaryStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.bodySmall

@Composable
fun PreferenceTitle(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Text(
        text = title,
        style = PreferenceTitleStyle,
        color = color,
        modifier = modifier,
    )
}

@Composable
fun PreferenceSummary(
    summary: String?,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    summary?.let {
        Text(
            text = it,
            style = PreferenceSummaryStyle,
            color = color,
            modifier = modifier,
        )
    }
}

data class StringInput(
    val title: String,
    val value: String?,
    val keyboardOptions: KeyboardOptions,
    val onSubmit: (String) -> Unit,
    val maxLines: Int = 1,
    val confirmDiscard: Boolean = false,
)
