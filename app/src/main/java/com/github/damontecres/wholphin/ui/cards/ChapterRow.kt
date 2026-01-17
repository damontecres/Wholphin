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

@Composable
fun ChapterRow(
    chapters: List<Chapter>,
    aspectRatio: Float,
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
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRestorer(),
        ) {
            itemsIndexed(chapters) { index, item ->
                ChapterCard(
                    name = item.name,
                    position = item.position,
                    imageUrl = item.imageUrl,
                    onClick = { onClick(item) },
                    aspectRatio = aspectRatio,
                    modifier = Modifier,
                    onLongClick = onLongClick?.let { { it(item) } },
                )
            }
        }
    }
}
