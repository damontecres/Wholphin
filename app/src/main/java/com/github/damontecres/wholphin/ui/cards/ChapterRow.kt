package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.ui.theme.FluidLayout

@Composable
fun ChapterRow(
    chapters: List<Chapter>,
    onClick: (Chapter) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: ((Chapter) -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.chapters),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 8.dp),
        )
        LazyRow(
            state = rememberLazyListState(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(
                    // The first card lines up with the headlines; the rest scroll full width
                    start = FluidLayout.rowStart(16.dp),
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 8.dp,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRestorer(),
        ) {
            itemsIndexed(chapters) { index, item ->
                ChapterCard(
                    chapter = item,
                    onClick = { onClick(item) },
                    modifier = Modifier,
                    onLongClick = onLongClick?.let { { it(item) } },
                )
            }
        }
    }
}
