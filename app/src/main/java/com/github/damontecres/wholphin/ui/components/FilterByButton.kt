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
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.filter.DecadeFilter
import com.github.damontecres.wholphin.data.filter.FavoriteFilter
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.FilterVideoType
import com.github.damontecres.wholphin.data.filter.GenreFilter
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.filter.OfficialRatingFilter
import com.github.damontecres.wholphin.data.filter.PlayedFilter
import com.github.damontecres.wholphin.data.filter.VideoTypeFilter
import com.github.damontecres.wholphin.data.filter.YearFilter
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
        ExpandableFaButton(
            title = R.string.filter,
            iconStringRes = R.string.fa_filter,
            onClick = { dropDown = true },
            modifier = Modifier,
        )

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
                val currentValue = remember(current) { filterOption.get(current) }

                var possibleValues by remember { mutableStateOf<List<FilterValueOption>>(listOf()) }
                LaunchedEffect(Unit) {
                    possibleValues = getPossibleValues.invoke(filterOption)
                    Timber.v("Got %s", possibleValues)
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
                possibleValues.forEachIndexed { index, value ->
                    val focusRequester = remember { FocusRequester() }
                    if (index == 0) {
                        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                    }

                    val isSelected =
                        remember(currentValue) {
                            when (filterOption) {
                                GenreFilter -> {
                                    (currentValue as? List<UUID>).orEmpty().contains(value.value)
                                }

                                FavoriteFilter,
                                PlayedFilter,
                                -> (currentValue as? Boolean) == value.name.toBoolean()

                                OfficialRatingFilter -> {
                                    (currentValue as? List<String>).orEmpty().contains(value.name)
                                }

                                VideoTypeFilter ->
                                    (currentValue as? List<FilterVideoType>)
                                        .orEmpty()
                                        .contains(value.value)

                                YearFilter,
                                DecadeFilter,
                                ->
                                    (currentValue as? List<Int>)
                                        .orEmpty()
                                        .contains(value.value)
                            }
                        }

                    DropdownMenuItem(
                        leadingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Filter active",
                                )
                            }
                        },
                        text = {
                            Text(
                                text = value.name,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        },
                        onClick = {
                            val newFilter =
                                when (filterOption) {
                                    GenreFilter -> {
                                        val list = (currentValue as? List<UUID>).orEmpty()
                                        val newValue =
                                            list
                                                .toMutableList()
                                                .apply {
                                                    if (isSelected) {
                                                        remove(value.value!!)
                                                    } else {
                                                        add(value.value!! as UUID)
                                                    }
                                                }.takeIf { it.isNotEmpty() }
                                        filterOption.set(newValue, current)
                                    }

                                    FavoriteFilter,
                                    PlayedFilter,
                                    -> {
                                        val played = value.name.toBoolean()
                                        filterOption.set(played, current)
                                    }

                                    OfficialRatingFilter -> {
                                        val list = (currentValue as? List<String>).orEmpty()
                                        val newValue =
                                            list
                                                .toMutableList()
                                                .apply {
                                                    if (isSelected) {
                                                        remove(value.name)
                                                    } else {
                                                        add(value.name)
                                                    }
                                                }.takeIf { it.isNotEmpty() }
                                        filterOption.set(newValue, current)
                                    }

                                    VideoTypeFilter -> {
                                        val list =
                                            (currentValue as? List<FilterVideoType>).orEmpty()
                                        val newValue =
                                            list
                                                .toMutableList()
                                                .apply {
                                                    if (isSelected) {
                                                        remove(value.value)
                                                    } else {
                                                        add(value.value as FilterVideoType)
                                                    }
                                                }.takeIf { it.isNotEmpty() }
                                        filterOption.set(newValue, current)
                                    }

                                    YearFilter,
                                    DecadeFilter,
                                    -> {
                                        val list =
                                            (currentValue as? List<Int>).orEmpty()
                                        val newValue =
                                            list
                                                .toMutableList()
                                                .apply {
                                                    if (isSelected) {
                                                        remove(value.value)
                                                    } else {
                                                        add(value.value as Int)
                                                    }
                                                }.takeIf { it.isNotEmpty() }
                                        filterOption.set(newValue, current)
                                    }
                                }

                            onFilterChange.invoke(newFilter)
                            if (!filterOption.supportMultiple) {
                                nestedDropDown = null
                            }
                        },
                        modifier = Modifier.focusRequester(focusRequester),
                    )
                }
            }
        }
    }
}
