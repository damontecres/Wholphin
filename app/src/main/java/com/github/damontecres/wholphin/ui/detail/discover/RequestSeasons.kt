package com.github.damontecres.wholphin.ui.detail.discover

import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import androidx.tv.material3.contentColorFor
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.model.Season
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.ui.cards.AvailableIndicator
import com.github.damontecres.wholphin.ui.cards.PartiallyAvailableIndicator
import com.github.damontecres.wholphin.ui.cards.PendingIndicator
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.theme.WholphinTheme

data class RequestSeason(
    val season: Season,
    val availability: SeerrAvailability,
)

@Composable
fun RequestSeasons(
    title: String,
    seasons: List<RequestSeason>,
    onSubmit: (Set<Int>, Boolean) -> Unit,
    request4kEnabled: Boolean,
    modifier: Modifier,
) {
    val allSeasonNumbers = remember(seasons) { seasons.mapNotNull { it.season.seasonNumber }.toSet() }
    val selected =
        remember {
            mutableStateSetOf<Int>(
                *seasons
                    .mapNotNull {
                        if (it.availability > SeerrAvailability.UNKNOWN) {
                            it.season.seasonNumber
                        } else {
                            null
                        }
                    }.toTypedArray(),
            )
        }
    var is4k by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn {
            item {
                val isSelected = selected.containsAll(allSeasonNumbers)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ClickSwitch(
                        label = stringResource(R.string.select_all),
                        checked = isSelected,
                        onClick = {
                            if (isSelected) {
                                selected.removeAll(allSeasonNumbers)
                            } else {
                                selected.addAll(allSeasonNumbers)
                            }
                        },
                    )
                    Button(
                        onClick = { onSubmit.invoke(selected, is4k) },
                    ) {
                        Text(
                            text = stringResource(R.string.submit),
                        )
                    }
                }
            }
            if (request4kEnabled) {
                item {
                    ClickSwitch(
                        label = stringResource(R.string.request_4k),
                        checked = is4k,
                        onClick = { is4k = !is4k },
                    )
                }
            }
            itemsIndexed(seasons) { index, season ->
                val seasonNumber = season.season.seasonNumber
                val isSelected = seasonNumber in selected
                SeasonListItem(
                    season = season,
                    selected = isSelected,
                    onClick = {
                        if (isSelected) {
                            selected.remove(seasonNumber)
                        } else {
                            seasonNumber?.let { selected.add(it) }
                        }
                    },
                    modifier = Modifier,
                )
            }
            if (seasons.size > 7) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) {
                        Button(
                            onClick = { onSubmit.invoke(selected, is4k) },
                        ) {
                            Text(
                                text = stringResource(R.string.submit),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeasonListItem(
    season: RequestSeason,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = false,
        headlineContent = {
            Text(
                text =
                    season.season.name
                        ?: (stringResource(R.string.tv_season) + " ${season.season.seasonNumber}"),
            )
        },
        supportingContent = {
            season.season.episodeCount?.let {
                Text(
                    // TODO should use plurals string
                    text = "${season.season.episodeCount} " + stringResource(R.string.episodes),
                )
            }
        },
        leadingContent = {
            when (season.availability) {
                SeerrAvailability.UNKNOWN -> {}

                SeerrAvailability.DELETED -> {}

                SeerrAvailability.PENDING,
                SeerrAvailability.PROCESSING,
                -> {
                    PendingIndicator()
                }

                SeerrAvailability.PARTIALLY_AVAILABLE -> {
                    PartiallyAvailableIndicator()
                }

                SeerrAvailability.AVAILABLE -> {
                    AvailableIndicator()
                }
            }
        },
        trailingContent = {
            Row {
                Switch(
                    checked = selected,
                    onCheckedChange = {
                        onClick.invoke()
                    },
                )
            }
        },
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun ClickSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                focusedContentColor = contentColorFor(MaterialTheme.colorScheme.inverseSurface),
                pressedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                pressedContentColor = contentColorFor(MaterialTheme.colorScheme.inverseSurface),
            ),
        onClick = onClick,
        content = content,
        modifier = modifier,
    )
}

@Composable
private fun ClickSwitch(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    ClickSurface(
        onClick = onClick,
        modifier = Modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .padding(horizontal = 8.dp)
                    .height(54.dp),
        ) {
            Switch(
                checked = checked,
                onCheckedChange = {},
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = label,
            )
        }
    }
}

@Composable
fun RequestSeasonsDialog(
    title: String,
    seasons: List<RequestSeason>,
    request4kEnabled: Boolean,
    onSubmit: (Set<Int>, Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BasicDialog(
        onDismissRequest = onDismissRequest,
    ) {
        RequestSeasons(
            title = title,
            seasons = seasons,
            request4kEnabled = request4kEnabled,
            onSubmit = onSubmit,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(
    device = "spec:parent=tv_1080p",
    backgroundColor = 0xFF383535,
    uiMode = UI_MODE_TYPE_TELEVISION,
    heightDp = 800,
)
@Composable
fun RequestSeasonsPreview() {
    val seasons =
        List(10) {
            RequestSeason(
                season =
                    Season(
                        seasonNumber = it + 1,
                        episodeCount = 10 + it,
                    ),
                availability =
                    if (it < 3) {
                        SeerrAvailability.AVAILABLE
                    } else {
                        SeerrAvailability.UNKNOWN
                    },
            )
        }

    WholphinTheme {
        RequestSeasons(
            title = "Series title",
            seasons = seasons,
            request4kEnabled = true,
            onSubmit = { _, _ -> },
            modifier = Modifier.width(400.dp),
        )
    }
}
