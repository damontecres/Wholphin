package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.filter.DiscoverSort
import com.github.damontecres.wholphin.data.filter.DiscoverSortAndDirection
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.data.flip
import com.github.damontecres.wholphin.ui.data.getStringRes
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

/**
 * Button that displays current sort option and provides ability to choose another
 *
 * Long pressing will reverse the current sort as will selecting the current sort option from the list
 */
@Composable
fun SortByButton(
    sortOptions: List<ItemSortBy>,
    current: SortAndDirection,
    onSortChange: (SortAndDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSort = current.sort
    val currentDirection = current.direction
    var sortByDropDown by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TextButton(
            onClick = { sortByDropDown = true },
            onLongClick = {
                onSortChange.invoke(current.flip())
            },
        ) {
            Text(
                text = current.toAnnotatedString(),
            )
        }

        DropdownMenu(
            expanded = sortByDropDown,
            onDismissRequest = { sortByDropDown = false },
        ) {
            sortOptions
//                .sortedBy { it.name }
                .forEach { sortOption ->
                    TvDropdownMenuItem(
                        leadingIcon = {
                            if (sortOption == currentSort) {
                                if (currentDirection == SortOrder.ASCENDING) {
                                    Text(
                                        text = stringResource(R.string.fa_caret_up),
                                        fontFamily = FontAwesome,
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.fa_caret_down),
                                        fontFamily = FontAwesome,
                                    )
                                }
                            }
                        },
                        text = {
                            Text(
                                text = stringResource(getStringRes(sortOption)),
                            )
                        },
                        onClick = {
                            sortByDropDown = false
                            val newDirection =
                                if (currentSort == sortOption) {
                                    currentDirection.flip()
                                } else {
                                    currentDirection
                                }
                            onSortChange.invoke(
                                SortAndDirection(
                                    sortOption,
                                    newDirection,
                                ),
                            )
                        },
                    )
                }
        }
    }
}

@Composable
fun SortAndDirection.toAnnotatedString() =
    buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = FontAwesome)) {
            append(
                stringResource(
                    if (direction == SortOrder.ASCENDING) {
                        R.string.fa_caret_up
                    } else {
                        R.string.fa_caret_down
                    },
                ),
            )
        }
        append(" ")
        append(stringResource(getStringRes(sort)))
    }

// TODO code duplicated from SortByButton
@Composable
fun DiscoverSortByButton(
    sortOptions: List<DiscoverSort>,
    current: DiscoverSortAndDirection,
    onSortChange: (DiscoverSortAndDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSort = current.sort
    val currentDirection = current.direction
    var sortByDropDown by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TextButton(
            onClick = { sortByDropDown = true },
            onLongClick = {
                onSortChange.invoke(current.flip())
            },
        ) {
            Text(
                text = current.toAnnotatedString(),
            )
        }

        DropdownMenu(
            expanded = sortByDropDown,
            onDismissRequest = { sortByDropDown = false },
        ) {
            sortOptions
//                .sortedBy { it.name }
                .forEach { sortOption ->
                    TvDropdownMenuItem(
                        leadingIcon = {
                            if (sortOption == currentSort) {
                                if (currentDirection == SortOrder.ASCENDING) {
                                    Text(
                                        text = stringResource(R.string.fa_caret_up),
                                        fontFamily = FontAwesome,
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.fa_caret_down),
                                        fontFamily = FontAwesome,
                                    )
                                }
                            }
                        },
                        text = {
                            Text(
                                text = stringResource(current.getStringRes()),
                            )
                        },
                        onClick = {
                            sortByDropDown = false
                            val newDirection =
                                if (currentSort == sortOption) {
                                    currentDirection.flip()
                                } else {
                                    currentDirection
                                }
                            onSortChange.invoke(
                                DiscoverSortAndDirection(
                                    sortOption,
                                    newDirection,
                                ),
                            )
                        },
                    )
                }
        }
    }
}

@Composable
fun DiscoverSortAndDirection.toAnnotatedString() =
    buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = FontAwesome)) {
            append(
                stringResource(
                    if (direction == SortOrder.ASCENDING) {
                        R.string.fa_caret_up
                    } else {
                        R.string.fa_caret_down
                    },
                ),
            )
        }
        append(" ")
        append(stringResource(getStringRes()))
    }
