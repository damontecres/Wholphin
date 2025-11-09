package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.components.DotSeparatedRow
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.SimpleStarRating
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.timeRemaining
import com.github.damontecres.wholphin.util.formatDateTime
import com.github.damontecres.wholphin.util.seasonEpisode
import org.jellyfin.sdk.model.extensions.ticks

@Composable
fun FocusedEpisodeHeader(
    ep: BaseItem?,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dto = ep?.data
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Text(
            text = dto?.episodeTitle ?: dto?.name ?: "",
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val details =
                buildList {
                    dto?.seasonEpisode?.let(::add)
                    dto?.premiereDate?.let { add(formatDateTime(it)) }
                    val duration = dto?.runTimeTicks?.ticks
                    duration
                        ?.roundMinutes
                        ?.toString()
                        ?.let {
                            add(it)
                        }
                    dto?.officialRating?.let(::add)
                    dto?.timeRemaining?.roundMinutes?.let { add("$it left") }
                }
            DotSeparatedRow(
                texts = details,
                textStyle = MaterialTheme.typography.titleSmall,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SimpleStarRating(
                dto?.communityRating,
                Modifier.height(20.dp),
            )
        }
        OverviewText(
            overview = dto?.overview ?: "",
            maxLines = 3,
            onClick = overviewOnClick,
        )
    }
}
