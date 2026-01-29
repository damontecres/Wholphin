package com.github.damontecres.wholphin.ui.main.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.ifElse

@Composable
fun HomeSettingsAddRow(
    libraries: List<Library>,
    showDiscover: Boolean,
    onClick: (Library) -> Unit,
    onClickMeta: (MetaRowType) -> Unit,
    modifier: Modifier,
    firstFocus: FocusRequester = remember { FocusRequester() },
) {
//    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
    Column(modifier = modifier) {
        Text(
            text = "Add row for...",
        )
        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                modifier
                    .fillMaxHeight()
                    .focusRestorer(firstFocus),
        ) {
            item {
                Text(
                    text = stringResource(R.string.continue_watching),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                )
            }
            itemsIndexed(
                listOf(
                    MetaRowType.CONTINUE_WATCHING,
                    MetaRowType.NEXT_UP,
                    MetaRowType.COMBINED_CONTINUE_WATCHING,
                ),
            ) { index, type ->
                HomeSettingsListItem(
                    selected = false,
                    headlineContent = {
                        Text(stringResource(type.stringId))
                    },
                    onClick = { onClickMeta.invoke(type) },
                    modifier = Modifier.ifElse(index == 0, Modifier.focusRequester(firstFocus)),
                )
            }
            item {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.library),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                )
            }
            itemsIndexed(libraries) { index, library ->
                HomeSettingsListItem(
                    selected = false,
                    headlineContent = {
                        Text(library.name)
                    },
                    onClick = { onClick.invoke(library) },
                    modifier = Modifier, // .ifElse(index == 0, Modifier.focusRequester(firstFocus)),
                )
            }
            item {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.more),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                )
            }
            item {
                HomeSettingsListItem(
                    selected = false,
                    headlineContent = {
                        Text(stringResource(MetaRowType.FAVORITES.stringId))
                    },
                    onClick = { onClickMeta.invoke(MetaRowType.FAVORITES) },
                    modifier = Modifier,
                )
            }
            if (showDiscover) {
                item {
                    HomeSettingsListItem(
                        selected = false,
                        headlineContent = {
                            Text(stringResource(MetaRowType.DISCOVER.stringId))
                        },
                        onClick = { onClickMeta.invoke(MetaRowType.DISCOVER) },
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}

enum class MetaRowType(
    @param:StringRes val stringId: Int,
) {
    CONTINUE_WATCHING(R.string.continue_watching),
    NEXT_UP(R.string.next_up),
    COMBINED_CONTINUE_WATCHING(R.string.combine_continue_next),
    FAVORITES(R.string.favorites),
    DISCOVER(R.string.discover),
}
