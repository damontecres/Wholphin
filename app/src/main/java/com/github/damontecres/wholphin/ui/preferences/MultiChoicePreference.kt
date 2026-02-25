package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup

@Composable
fun <T> MultiChoicePreference(
    title: String,
    summary: String?,
    possibleValues: List<T>,
    selectedValues: Set<T>,
    onValueChange: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    valueDisplay: @Composable (index: Int, item: T) -> Unit = { _, item -> Text(item.toString()) },
) {
//    val values = stringArrayResource(preference.displayValues).toList()
//    val summary =
//        preference.summary?.let { stringResource(it) }
//            ?: preference.summary(context, value)
    val selectedValues =
        remember {
            val list = mutableStateSetOf<T>()
            list.addAll(selectedValues)
            list
        }

    val onClick = { item: T ->
        if (selectedValues.contains(item)) {
            selectedValues.remove(item)
        } else {
            selectedValues.add(item)
        }
        onValueChange.invoke(selectedValues.toList())
    }

    var dialogParams by remember { mutableStateOf<DialogParams?>(null) }

    ClickPreference(
        title = title,
        summary = summary,
        onClick = {
            dialogParams =
                DialogParams(
                    title = title,
                    fromLongClick = false,
                    items =
                        possibleValues.mapIndexed { index, item ->
                            DialogItem(
                                headlineContent = { valueDisplay.invoke(index, item) },
                                trailingContent = {
                                    Switch(
                                        checked = selectedValues.contains(item),
                                        onCheckedChange = {
                                            onClick.invoke(item)
                                        },
                                    )
                                },
                                onClick = {
                                    onClick.invoke(item)
                                },
                            )
                        },
                )
        },
        interactionSource = interactionSource,
        modifier = modifier,
    )
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
}
