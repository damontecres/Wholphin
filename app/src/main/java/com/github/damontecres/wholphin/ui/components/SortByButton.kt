package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
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
    val name = stringResource(getStringRes(currentSort))
    val currentDirection = current.direction
    var sortByDropDown by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier = modifier) {
        Button(
            onClick = { sortByDropDown = true },
            onLongClick = {
                onSortChange.invoke(current.flip())
            },
        ) {
            Text(
                text =
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontFamily = FontAwesome)) {
                            append(
                                stringResource(
                                    if (currentDirection == SortOrder.ASCENDING) {
                                        R.string.fa_caret_up
                                    } else {
                                        R.string.fa_caret_down
                                    },
                                ),
                            )
                        }
                        append(" ")
                        append(name)
                    },
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
