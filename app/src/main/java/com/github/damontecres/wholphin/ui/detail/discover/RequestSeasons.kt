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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState

data class RequestSeason(
    val season: Season,
    val availability: SeerrAvailability,
)

@Composable
fun RequestSeasons(
    id: Int,
    title: String,
    seasons: List<RequestSeason>,
    state: SeerrRequestData,
    request4kEnabled: Boolean,
    onSubmit: (TvRequest) -> Unit,
    modifier: Modifier = Modifier,
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
    var is4k by remember { mutableStateOf(request4kEnabled) }

    var profile by remember(is4k) {
        mutableStateOf(
            if (is4k) {
                state.profiles4k.firstOrNull { it.default } ?: state.profiles4k.firstOrNull()
            } else {
                state.profiles.firstOrNull { it.default } ?: state.profiles.firstOrNull()
            },
        )
    }
    var folder by remember(is4k) {
        mutableStateOf(
            if (is4k) {
                state.rootFolders4k.firstOrNull { it.default } ?: state.rootFolders4k.firstOrNull()
            } else {
                state.rootFolders.firstOrNull { it.default } ?: state.rootFolders.firstOrNull()
            },
        )
    }
    val profiles = remember(is4k, state) { if (is4k) state.profiles4k else state.profiles }
    val folders = remember(is4k, state) { if (is4k) state.rootFolders4k else state.rootFolders }

    fun submit() {
        onSubmit.invoke(
            TvRequest(
                tvId = id,
                seasons = selected.toList(),
                is4k = is4k,
                profileId = profile?.id,
                folder = folder?.path,
            ),
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier,
        )
        LazyColumn(
            modifier = Modifier,
        ) {
            item {
                if (request4kEnabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                    ) {
                        ClickSwitch(
                            label = stringResource(R.string.request_4k),
                            checked = is4k,
                            onClick = { is4k = !is4k },
                        )
                        Button(
                            onClick = ::submit,
                        ) {
                            Text(
                                text = stringResource(R.string.submit),
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = ::submit,
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.submit),
                        )
                    }
                }
            }
            if (profiles.isNotEmpty()) {
                profile?.let {
                    item {
                        ChooseProfile(
                            selectedProfile = it,
                            profiles = profiles,
                            onClickProfile = { profile = it },
                            modifier = Modifier,
                        )
                    }
                }
            }
            if (folders.isNotEmpty()) {
                folder?.let {
                    item {
                        ChooseFolder(
                            selectedFolder = it,
                            folders = folders,
                            onClickFolder = { folder = it },
                            modifier = Modifier,
                        )
                    }
                }
            }
            item {
                HorizontalDivider()
            }
            item {
                Text(
                    text = stringResource(R.string.tv_seasons),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val isSelected = selected.containsAll(allSeasonNumbers)
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
            if (seasons.size > 3) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                    ) {
                        Button(
                            onClick = ::submit,
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
fun ClickSwitch(
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
    id: Int,
    title: String,
    loading: LoadingState,
    state: SeerrRequestData,
    seasons: List<RequestSeason>,
    request4kEnabled: Boolean,
    onSubmit: (TvRequest) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BasicDialog(
        onDismissRequest = onDismissRequest,
    ) {
        when (loading) {
            is LoadingState.Error -> {
                ErrorMessage(loading, Modifier)
            }

            LoadingState.Loading,
            LoadingState.Pending,
            -> {
                LoadingPage(Modifier)
            }

            LoadingState.Success -> {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                RequestSeasons(
                    id = id,
                    title = title,
                    state = state,
                    seasons = seasons,
                    request4kEnabled = request4kEnabled,
                    onSubmit = onSubmit,
                    modifier =
                        Modifier
                            .padding(16.dp)
                            .focusRequester(focusRequester),
                )
            }
        }
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
            id = 1,
            title = "Series title",
            seasons = seasons,
            state =
                SeerrRequestData(
                    profiles4k =
                        listOf(
                            SeerrProfile(1, "HD", true),
                            SeerrProfile(2, "Ultra HD", false),
                        ),
                    rootFolders4k =
                        listOf(
                            SeerrRootFolder(1, "/movies", "400GB", true),
                        ),
                ),
            request4kEnabled = true,
            onSubmit = { },
            modifier = Modifier.width(400.dp),
        )
    }
}
