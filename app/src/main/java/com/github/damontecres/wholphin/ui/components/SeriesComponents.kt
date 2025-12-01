package com.github.damontecres.wholphin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.formatDateTime
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.seasonEpisode
import com.github.damontecres.wholphin.ui.timeRemaining
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.extensions.ticks

@Composable
fun SeriesName(
    seriesName: String?,
    modifier: Modifier = Modifier,
) {
    Text(
        text = seriesName ?: "",
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.headlineMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun EpisodeName(
    episodeName: String?,
    modifier: Modifier = Modifier,
) {
    Text(
        text = episodeName ?: "",
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.headlineSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun EpisodeName(
    episode: BaseItemDto?,
    modifier: Modifier = Modifier,
) = EpisodeName(episode?.episodeTitle ?: episode?.name, modifier)

@Composable
fun EpisodeQuickDetails(
    dto: BaseItemDto?,
    modifier: Modifier = Modifier,
) {
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
        communityRating = dto?.communityRating,
        criticRating = dto?.criticRating,
        textStyle = MaterialTheme.typography.titleSmall,
        modifier = modifier,
    )
}
