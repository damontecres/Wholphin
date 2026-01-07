package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.ui.components.DotSeparatedRow
import timber.log.Timber

@Composable
fun DiscoverQuickDetails(
    item: DiscoverItem?,
    rating: DiscoverRating?,
    modifier: Modifier = Modifier,
) {
    Timber.v("id=${item?.id}, rating=$rating")
    val context = LocalContext.current
    val details =
        remember(item) {
            buildList {
                item
                    ?.releaseDate
                    ?.year
                    ?.toString()
                    ?.let(::add)
            }
        }
    DotSeparatedRow(
        texts = details,
        communityRating = rating?.audienceRating,
        criticRating = rating?.criticRating?.toFloat(),
        textStyle = MaterialTheme.typography.titleSmall,
        modifier = modifier,
    )
}
