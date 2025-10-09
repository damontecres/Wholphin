package com.github.damontecres.dolphin.ui.detail.movie

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.R
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.ui.components.DotSeparatedRow
import com.github.damontecres.dolphin.ui.components.StarRating
import com.github.damontecres.dolphin.ui.components.StarRatingPrecision
import com.github.damontecres.dolphin.ui.components.TitleValueText
import com.github.damontecres.dolphin.ui.isNotNullOrBlank
import com.github.damontecres.dolphin.ui.letNotEmpty
import com.github.damontecres.dolphin.ui.playOnClickSound
import com.github.damontecres.dolphin.ui.playSoundOnFocus
import com.github.damontecres.dolphin.ui.roundMinutes
import com.github.damontecres.dolphin.ui.timeRemaining
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
                        text = overview,
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
                        modifier = Modifier,
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
                                )
                            }
                    }
                dto.mediaStreams
                    ?.filter { it.type == MediaStreamType.SUBTITLE && it.language.isNotNullOrBlank() }
                    ?.mapNotNull { it.language }
                    ?.joinToString(", ")
                    ?.let {
                        if (it.isNotNullOrBlank()) {
                            TitleValueText(
                                "Subtitles",
                                it,
                                modifier = Modifier.widthIn(max = 64.dp),
                            )
                        }
                    }
                // TODO add writers, studio, etc to overview dialog
                dto.people?.firstOrNull { it.type == PersonKind.DIRECTOR }?.name?.let {
                    TitleValueText(
                        stringResource(R.string.director),
                        it,
                        modifier = Modifier.widthIn(max = 80.dp),
                    )
                }
//                dto.studios?.letNotEmpty {
//                    TitleValueText(
//                        stringResource(R.string.studios),
//                        it.joinToString(", ") { s -> s.name ?: "" },
//                        modifier = Modifier.widthIn(max = 80.dp),
//                    )
//                }
                dto.genres?.letNotEmpty {
                    TitleValueText(
                        stringResource(R.string.genres),
                        it.joinToString(", "),
                        modifier = Modifier.widthIn(max = 80.dp),
                    )
                }
            }
        }
    }
}
