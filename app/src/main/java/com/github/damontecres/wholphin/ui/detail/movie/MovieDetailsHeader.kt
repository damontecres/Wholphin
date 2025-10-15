package com.github.damontecres.wholphin.ui.detail.movie

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.components.DotSeparatedRow
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.StarRating
import com.github.damontecres.wholphin.ui.components.StarRatingPrecision
import com.github.damontecres.wholphin.ui.components.TitleValueText
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.timeRemaining
import com.github.damontecres.wholphin.util.formatSubtitleLang
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.extensions.ticks

@Composable
fun MovieDetailsHeader(
    movie: BaseItem,
    bringIntoViewRequester: BringIntoViewRequester,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dto = movie.data
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        // Title
        Text(
            text = movie.name ?: "",
            color = MaterialTheme.colorScheme.onSurface,
            style =
                MaterialTheme.typography.displayLarge.copy(
                    shadow =
                        Shadow(
                            color = Color.DarkGray,
                            offset = Offset(5f, 2f),
                            blurRadius = 2f,
                        ),
                ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.alpha(0.75f),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val details =
                    buildList {
                        dto.productionYear?.let { add(it.toString()) }
                        val duration = dto.runTimeTicks?.ticks
                        duration
                            ?.roundMinutes
                            ?.toString()
                            ?.let {
                                add(it)
                            }
                        dto.officialRating?.let(::add)
                        dto.timeRemaining?.roundMinutes?.let { add("$it left") }
                    }
                DotSeparatedRow(
                    texts = details,
                    textStyle = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            dto.communityRating?.let {
                if (it > 0f) {
                    StarRating(
                        rating100 = (it * 10).toInt(),
                        onRatingChange = {},
                        enabled = false,
                        precision = StarRatingPrecision.HALF,
                        playSoundOnFocus = true,
                        modifier = Modifier.height(32.dp),
                    )
                } else {
                    Spacer(Modifier.height(32.dp))
                }
            }

            // Description
            dto.overview?.let { overview ->
                OverviewText(
                    overview = overview,
                    maxLines = 3,
                    onClick = overviewOnClick,
                    textBoxHeight = Dp.Unspecified,
                )
            }
            movie.data.people
                ?.filter { it.type == PersonKind.DIRECTOR && it.name.isNotNullOrBlank() }
                ?.joinToString(", ") { it.name!! }
                ?.let {
                    Text(
                        text = "Directed by $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            // Key-Values
            Row(
                modifier =
                    Modifier
                        .padding(start = 16.dp)
                        .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                dto.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }?.displayTitle?.let {
                    TitleValueText(
                        stringResource(R.string.video),
                        it,
                        modifier = Modifier.widthIn(max = 200.dp),
                    )
                }
                dto.mediaStreams
                    ?.firstOrNull { it.type == MediaStreamType.AUDIO }
                    ?.displayTitle
                    ?.let {
                        // TODO probably a cleaner way to do this
                        // Removes part of "5.1 Surround - English - AAC - Default"
                        it
                            .replace(" - Default", "")
                            .ifBlank { null }
                            ?.let {
                                TitleValueText(
                                    stringResource(R.string.audio),
                                    it,
                                    modifier = Modifier.widthIn(max = 200.dp),
                                )
                            }
                    }
                formatSubtitleLang(dto.mediaStreams)
                    ?.let {
                        if (it.isNotNullOrBlank()) {
                            TitleValueText(
                                "Subtitles",
                                it,
                                modifier = Modifier.widthIn(max = 120.dp),
                            )
                        }
                    }
                // TODO add writers, studio, etc to overview dialog
//                dto.studios?.letNotEmpty {
//                    TitleValueText(
//                        stringResource(R.string.studios),
//                        it.joinToString(", ") { s -> s.name ?: "" },
//                        modifier = Modifier.widthIn(max = 80.dp),
//                    )
//                }
//                dto.genres?.letNotEmpty {
//                    TitleValueText(
//                        stringResource(R.string.genres),
//                        it.joinToString(", "),
//                        modifier = Modifier.widthIn(max = 80.dp),
//                    )
//                }
            }
        }
    }
}
