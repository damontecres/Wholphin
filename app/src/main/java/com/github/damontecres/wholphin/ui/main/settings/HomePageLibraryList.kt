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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
fun HomePageLibraryList(
    libraries: List<Library>,
    onClick: (Library) -> Unit,
    onClickMeta: (MetaRowType) -> Unit,
    modifier: Modifier,
    firstFocus: FocusRequester = remember { FocusRequester() },
) {
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
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
            itemsIndexed(MetaRowType.entries) { index, type ->
                ListItem(
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
            }
            itemsIndexed(libraries) { index, library ->
                ListItem(
                    selected = false,
                    headlineContent = {
                        Text(library.name)
                    },
                    onClick = { onClick.invoke(library) },
                    modifier = Modifier, // .ifElse(index == 0, Modifier.focusRequester(firstFocus)),
                )
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
}
