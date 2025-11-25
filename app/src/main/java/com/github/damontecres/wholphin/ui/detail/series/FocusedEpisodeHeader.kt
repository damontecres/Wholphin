package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.DotSeparatedRow
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.VideoStreamDetails
import com.github.damontecres.wholphin.ui.formatDateTime
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.seasonEpisode
import com.github.damontecres.wholphin.ui.timeRemaining
import org.jellyfin.sdk.model.extensions.ticks

@Composable
fun FocusedEpisodeHeader(
    preferences: UserPreferences,
    ep: BaseItem?,
    chosenStreams: ChosenStreams?,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dto = ep?.data
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = dto?.episodeTitle ?: dto?.name ?: "",
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier,
        )
        val details =
            remember(dto) {
                buildList {
                    dto?.seasonEpisode?.let(::add)
                    dto?.premiereDate?.let { add(formatDateTime(it)) }
                    val duration = dto?.runTimeTicks?.ticks
                    duration
                        ?.roundMinutes
                        ?.toString()
                        ?.let(::add)
                    dto?.timeRemaining?.roundMinutes?.let { add("$it left") }
                    dto?.officialRating?.let(::add)
                }
            }
        DotSeparatedRow(
            texts = details,
            rating = dto?.communityRating,
            textStyle = MaterialTheme.typography.titleSmall,
        )

        if (dto != null) {
            VideoStreamDetails(
                preferences = preferences,
                dto = dto,
                itemPlayback = chosenStreams?.itemPlayback,
                modifier = Modifier,
            )
        }
        OverviewText(
            overview = dto?.overview ?: "",
            maxLines = 3,
            onClick = overviewOnClick,
        )
    }
}
