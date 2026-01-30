package com.github.damontecres.wholphin.ui.main.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
fun HomeLibraryRowTypeList(
    library: Library,
    onClick: (LibraryRowType) -> Unit,
    modifier: Modifier,
    firstFocus: FocusRequester = remember { FocusRequester() },
) {
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
    Column(modifier = modifier) {
        TitleText(stringResource(R.string.add_row_for, library.name))
        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                modifier
                    .fillMaxHeight()
                    .focusRestorer(firstFocus),
        ) {
            itemsIndexed(LibraryRowType.entries) { index, rowType ->
                ListItem(
                    selected = false,
                    headlineContent = {
                        Text(
                            text = stringResource(rowType.stringId),
                        )
                    },
                    onClick = { onClick.invoke(rowType) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .ifElse(index == 0, Modifier.focusRequester(firstFocus)),
                )
            }
        }
    }
}

enum class LibraryRowType(
    @param:StringRes val stringId: Int,
) {
    RECENTLY_ADDED(R.string.recently_added),
    RECENTLY_RELEASED(R.string.recently_released),
    GENRES(R.string.genres),
}
