package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.GenreFilter
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.filter.PlayedFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.ui.tryRequestFocus
import timber.log.Timber
import java.util.UUID

@Composable
fun FilterByButton(
    filterOptions: List<ItemFilterBy<*>>,
    current: GetItemsFilter,
    onFilterChange: (GetItemsFilter) -> Unit,
    getPossibleValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
    modifier: Modifier = Modifier,
) {
    var dropDown by remember { mutableStateOf(false) }
    var nestedDropDown by remember { mutableStateOf<ItemFilterBy<*>?>(null) }
    val context = LocalContext.current

    Box(modifier = modifier) {
        Button(
            onClick = { dropDown = true },
        ) {
            Text(
                text = stringResource(R.string.search),
            )
        }

        DropdownMenu(
            expanded = dropDown,
            onDismissRequest = {
                dropDown = false
                nestedDropDown = null
            },
        ) {
            filterOptions
//                .sortedBy { it.name }
                .forEach { filterOption ->
                    val currentValue = remember(current) { filterOption.get(current) }

                    DropdownMenuItem(
                        leadingIcon = {
                            if (currentValue != null) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Filter active",
                                )
                            }
                        },
                        text = {
                            Text(
                                text = stringResource(filterOption.stringRes),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        },
                        onClick = {
                            nestedDropDown = filterOption
                        },
                    )
                }
        }

        DropdownMenu(
            expanded = nestedDropDown != null,
            tonalElevation = 10.dp,
            shadowElevation = 3.dp,
            offset = DpOffset(32.dp, 16.dp),
            onDismissRequest = {
                nestedDropDown = null
            },
        ) {
            nestedDropDown?.let { filterOption ->
                filterOption as ItemFilterBy<Any>
                val currentValue = filterOption.get(current)

                var possibleValues by remember { mutableStateOf<List<FilterValueOption>>(listOf()) }
                LaunchedEffect(Unit) {
                    possibleValues = getPossibleValues.invoke(filterOption)
                    Timber.v("Got %s", possibleValues)
                }
                possibleValues.forEachIndexed { index, value ->
                    val focusRequester = remember { FocusRequester() }
                    if (index == 0) {
                        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                    }
                    DropdownMenuItem(
                        leadingIcon = {},
                        text = {
                            Text(
                                text = value.name,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        },
                        onClick = {
                            when (filterOption) {
                                GenreFilter -> {
                                    val list = (currentValue as? List<UUID>).orEmpty()
                                    val newValue = list.toMutableList().apply { add(value.id!!) }
                                    onFilterChange.invoke(filterOption.set(newValue, current))
                                }
                                PlayedFilter -> {
                                    val played = value.name.toBoolean()
                                    onFilterChange.invoke(filterOption.set(played, current))
                                }
                            }
                            if (!filterOption.supportMultiple) {
                                nestedDropDown = null
                            }
                        },
                        modifier = Modifier.focusRequester(focusRequester),
                    )
                }
                if (currentValue != null) {
                    DropdownMenuItem(
                        leadingIcon = {},
                        text = {
                            Text(
                                text = stringResource(R.string.delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            onFilterChange.invoke(filterOption.set(null, current))
                            nestedDropDown = null
                        },
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}
