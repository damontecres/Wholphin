package com.github.damontecres.wholphin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.filter.CommunityRatingFilter
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
import com.github.damontecres.wholphin.ui.DefaultButtonPadding
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.tryRequestFocus
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
    val filterCount = remember(current, filterOptions) { current.countFilters(filterOptions) }

    Box(modifier = modifier) {
        ExpandableFilterButton(
            filterCount = filterCount,
            onClick = { dropDown = true },
            modifier = Modifier,
        )

        DropdownMenu(
            expanded = dropDown,
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            onDismissRequest = {
                dropDown = false
                nestedDropDown = null
            },
        ) {
            if (filterCount > 0) {
                val interactionSource = remember { MutableInteractionSource() }
                val focused by interactionSource.collectIsFocusedAsState()
                TvDropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color.Red.copy(alpha = .8f),
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.remove),
                            color = if (focused) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        onFilterChange.invoke(current.delete(filterOptions))
                        dropDown = false
                    },
                    interactionSource = interactionSource,
                    modifier = Modifier,
                )
                HorizontalDivider()
            }
            filterOptions
                .forEachIndexed { index, filterOption ->
                    val focusRequester = remember { FocusRequester() }
                    if (index == 0) {
                        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                    }
                    val currentValue = remember(current) { filterOption.get(current) }
                    TvDropdownMenuItem(
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
                            )
                        },
                        onClick = {
                            nestedDropDown = filterOption
                        },
                        modifier = Modifier.focusRequester(focusRequester),
                    )
                }
        }

        DropdownMenu(
            expanded = nestedDropDown != null,
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(5.dp),
            offset = DpOffset(80.dp, 16.dp),
            onDismissRequest = {
                nestedDropDown = null
            },
        ) {
            nestedDropDown?.let { filterOption ->
                filterOption as ItemFilterBy<Any>
                val currentValue = remember(current) { filterOption.get(current) }

                var possibleValues by remember { mutableStateOf<List<FilterValueOption>?>(null) }
                LaunchedEffect(Unit) {
                    possibleValues = getPossibleValues.invoke(filterOption)
                }

                if (currentValue != null) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val focused by interactionSource.collectIsFocusedAsState()
                    TvDropdownMenuItem(
                        elevation = 5.dp,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color.Red.copy(alpha = .8f),
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.remove),
                                color = if (focused) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            onFilterChange.invoke(filterOption.set(null, current))
                            nestedDropDown = null
                        },
                        interactionSource = interactionSource,
                        modifier = Modifier,
                    )
                    HorizontalDivider()
                }

                if (possibleValues == null) {
                    CircularProgress(Modifier.size(48.dp))
                } else if (possibleValues?.isEmpty() == true) {
                    Text(
                        text = stringResource(R.string.no_results),
                    )
                } else {
                    possibleValues.orEmpty().forEachIndexed { index, value ->
                        val focusRequester = remember { FocusRequester() }
                        if (index == 0) {
                            LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                        }

                        val isSelected =
                            remember(currentValue) {
                                when (filterOption) {
                                    GenreFilter -> {
                                        (currentValue as? List<UUID>)
                                            .orEmpty()
                                            .contains(value.value)
                                    }

                                    FavoriteFilter,
                                    PlayedFilter,
                                    -> (currentValue as? Boolean) == value.name.toBoolean()

                                    OfficialRatingFilter -> {
                                        (currentValue as? List<String>)
                                            .orEmpty()
                                            .contains(value.name)
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

                                    CommunityRatingFilter -> (currentValue as? Int) == value.value
                                }
                            }
                        val interactionSource = remember { MutableInteractionSource() }
                        val focused by interactionSource.collectIsFocusedAsState()
                        TvDropdownMenuItem(
                            elevation = 8.dp,
                            interactionSource = interactionSource,
                            leadingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Filter active",
                                    )
                                }
                            },
                            text = {
                                if (filterOption == CommunityRatingFilter) {
                                    SimpleStarRating(
                                        "${value.name}+",
                                        starColor = if (focused) EmptyStarColor else FilledStarColor,
                                    )
                                } else {
                                    Text(
                                        text = value.name,
                                    )
                                }
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

                                        CommunityRatingFilter ->
                                            filterOption.set(
                                                value.value as? Int,
                                                current,
                                            )
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
}

@Composable
fun ExpandableFilterButton(
    filterCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconColor: Color = Color.Unspecified,
) {
    val resources = LocalResources.current
    val isFocused = interactionSource.collectIsFocusedAsState().value
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        contentPadding = DefaultButtonPadding,
        interactionSource = interactionSource,
    ) {
        Text(
            text = stringResource(R.string.fa_filter),
            style = MaterialTheme.typography.titleSmall,
            color = iconColor,
            fontSize = 16.sp,
            fontFamily = FontAwesome,
            textAlign = TextAlign.Center,
            modifier = Modifier,
        )
        AnimatedVisibility(filterCount > 0) {
            Text(
                text = filterCount.toString(),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        AnimatedVisibility(isFocused) {
            Text(
                text = stringResource(R.string.filter),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
