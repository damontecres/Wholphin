package com.github.damontecres.wholphin.ui.main.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.dp
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
        TitleText(stringResource(R.string.add_row))
        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                modifier
                    .fillMaxHeight()
                    .focusRestorer(firstFocus),
        ) {
            itemsIndexed(
                listOf(
                    MetaRowType.CONTINUE_WATCHING,
                    MetaRowType.NEXT_UP,
                    MetaRowType.COMBINED_CONTINUE_WATCHING,
                ),
            ) { index, type ->
                HomeSettingsListItem(
                    selected = false,
                    headlineText = stringResource(type.stringId),
                    onClick = { onClickMeta.invoke(type) },
                    modifier = Modifier.ifElse(index == 0, Modifier.focusRequester(firstFocus)),
                )
            }
            item {
                TitleText(stringResource(R.string.library))
                HorizontalDivider()
            }
            itemsIndexed(libraries) { index, library ->
                HomeSettingsListItem(
                    selected = false,
                    headlineText = library.name,
                    onClick = { onClick.invoke(library) },
                    modifier = Modifier, // .ifElse(index == 0, Modifier.focusRequester(firstFocus)),
                )
            }
            item {
                TitleText(stringResource(R.string.more))
                HorizontalDivider()
            }
            itemsIndexed(
                listOf(
                    MetaRowType.FAVORITES,
                    MetaRowType.COLLECTION,
                    MetaRowType.PLAYLIST,
                ),
            ) { index, type ->
                HomeSettingsListItem(
                    selected = false,
                    headlineText = stringResource(type.stringId),
                    onClick = { onClickMeta.invoke(type) },
                    modifier = Modifier,
                )
            }
            if (showDiscover) {
                item {
                    HomeSettingsListItem(
                        selected = false,
                        headlineText = stringResource(MetaRowType.DISCOVER.stringId),
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
    COLLECTION(R.string.collection),
    PLAYLIST(R.string.playlist),
}
