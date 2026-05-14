package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.util.TrackSupport

/**
 * Debug info about the current playback tracks
 */
@Composable
fun PlaybackTrackInfo(
    trackSupport: List<TrackSupport>,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
) {
    val selectedTracks = remember(trackSupport) { trackSupport.filter { it.selected } }
    val selectedWeight = .5f
    val weights = listOf(.25f, .4f, .5f, 1f, 1f)

    var expanded by remember { mutableStateOf(false) }
    val focusRequesters =
        remember(trackSupport.size) { List(trackSupport.size) { FocusRequester() } }
    val showButtonFocusRequester = remember { FocusRequester() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier,
                ) {
                    val texts =
                        listOf(
                            "ID",
                            "Type",
                            "Codec",
                            "Supported",
                            "Labels",
                        )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(selectedWeight),
                    ) {
                        Text(
                            text = "Selected",
                            style = textStyle,
                        )
                    }
                    texts.forEachIndexed { index, text ->
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier.weight(weights[index]),
                        ) {
                            Text(
                                text = text,
                                style = textStyle,
                            )
                        }
                    }
                }
            }
            val tracks = if (expanded) trackSupport else selectedTracks
            itemsIndexed(tracks) { index, track ->
                ProvideTextStyle(textStyle) {
                    TrackSupportRow(
                        track = track,
                        selectedWeight = selectedWeight,
                        weights = weights,
                        modifier =
                            Modifier
                                .focusRequester(focusRequesters[index])
                                .ifElse(
                                    index == tracks.lastIndex,
                                    Modifier.focusProperties {
                                        down = showButtonFocusRequester
                                        right = showButtonFocusRequester
                                    },
                                ),
                    )
                }
            }
        }
        if (trackSupport.size > selectedTracks.size) {
            val density = LocalDensity.current
            val height =
                remember(density, textStyle) {
                    with(density) {
                        textStyle.fontSize.toDp() * 2
                    }
                }
            Box(Modifier.fillMaxWidth()) {
                Button(
                    onClick = { expanded = !expanded },
                    shape =
                        ClickableSurfaceDefaults.shape(
                            shape = RoundedCornerShape(25),
                        ),
                    contentHeight = height,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .focusRequester(showButtonFocusRequester)
                            .focusProperties {
                                up = focusRequesters[0]
                            },
                ) {
                    val text =
                        if (expanded) {
                            stringResource(R.string.hide)
                        } else {
                            stringResource(
                                R.string.show_more,
                                trackSupport.size - selectedTracks.size,
                            )
                        }
                    Text(
                        text = text,
                        fontSize = textStyle.fontSize,
                    )
                }
            }
        }
    }
}

@Composable
fun TrackSupportRow(
    track: TrackSupport,
    selectedWeight: Float,
    weights: List<Float>,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = {},
        modifier = modifier,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(RectangleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = .25f),
            ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
                Modifier.ifElse(
                    track.selected,
                    Modifier.background(MaterialTheme.colorScheme.border.copy(alpha = .25f)),
                ),
        ) {
            val texts =
                listOf(
                    track.id ?: "",
                    track.type.name,
                    track.codecs ?: "",
                    track.supported.name,
                    track.labels.joinToString(", "),
                )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(selectedWeight),
            ) {
                if (track.selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(12.dp),
                    )
                } else {
                    Text(
                        text = "-",
                    )
                }
            }
            texts.forEachIndexed { index, text ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.weight(weights[index]),
                ) {
                    Text(
                        text = text,
                    )
                }
            }
        }
    }
}
