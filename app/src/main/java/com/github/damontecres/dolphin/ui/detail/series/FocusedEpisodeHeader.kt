package com.github.damontecres.dolphin.ui.detail.series

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.ui.components.DotSeparatedRow
import com.github.damontecres.dolphin.ui.components.StarRating
import com.github.damontecres.dolphin.ui.components.StarRatingPrecision
import com.github.damontecres.dolphin.ui.playOnClickSound
import com.github.damontecres.dolphin.ui.playSoundOnFocus
import com.github.damontecres.dolphin.ui.roundMinutes
import com.github.damontecres.dolphin.ui.timeRemaining
import com.github.damontecres.dolphin.util.formatDateTime
import com.github.damontecres.dolphin.util.seasonEpisode
import org.jellyfin.sdk.model.extensions.ticks

@Composable
fun FocusedEpisodeHeader(
    ep: BaseItem,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dto = ep.data
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Text(
            text = dto.episodeTitle ?: dto.name ?: "",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val details =
                buildList {
                    dto.seasonEpisode?.let(::add)
                    dto.premiereDate?.let { add(formatDateTime(it)) }
                    val duration = dto.runTimeTicks?.ticks
                    duration
                        ?.roundMinutes
                        ?.toString()
                        ?.let {
                            add(it)
                        }
                    dto.timeRemaining?.roundMinutes?.let { add("$it left") }
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
            // TODO ratings?
            dto.communityRating?.let {
                if (it > 0f) {
                    StarRating(
                        rating100 = (it * 10).toInt(),
                        onRatingChange = {},
                        enabled = false,
                        precision = StarRatingPrecision.HALF,
                        playSoundOnFocus = true,
                        modifier = Modifier.height(24.dp),
                    )
                } else {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        val interactionSource = remember { MutableInteractionSource() }
        val isFocused = interactionSource.collectIsFocusedAsState().value
        val bgColor =
            if (isFocused) {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = .4f)
            } else {
                Color.Unspecified
            }
        Box(
            modifier =
                Modifier
                    .background(bgColor, shape = RoundedCornerShape(8.dp))
                    .playSoundOnFocus(true)
                    .clickable(
                        enabled = true,
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                    ) {
                        playOnClickSound(context)
                        overviewOnClick.invoke()
                    },
        ) {
            Text(
                text = dto.overview ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .padding(8.dp)
                        .height(60.dp),
            )
        }
    }
}
