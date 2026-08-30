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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import androidx.tv.material3.contentColorFor
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.model.Season
import com.github.damontecres.wholphin.data.model.RequestStatus
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

data class RequestSeason(
    val season: Season,
    val status: RequestStatus,
    val availability: SeerrAvailability,
    val editable: Boolean,
)

@Composable
fun RequestSeasons(
    id: Int,
    title: String,
    seasons: List<RequestSeason>,
    seasons4k: List<RequestSeason>,
    data: SeerrRequestData,
    request4kEnabled: Boolean,
    onSubmit: (TvRequest) -> Unit,
    initialSeasonNumber: Int? = null,
    modifier: Modifier = Modifier,
) {
    var is4k by remember { mutableStateOf(request4kEnabled) }
    val seasons = remember(is4k, seasons, seasons4k) { if (is4k) seasons4k else seasons }

    val allSeasonNumbers =
        remember(seasons) {
            seasons
                .filter { it.editable }
                .mapNotNull { it.season.seasonNumber }
                .toSet()
        }
    val availableSeasons =
        remember(seasons) {
            mutableStateSetOf(
                *seasons
                    .filter { season ->
                        !season.editable && (
                            season.status == RequestStatus.PENDING ||
                                season.status == RequestStatus.APPROVED ||
                                season.status == RequestStatus.COMPLETED ||
                                season.availability == SeerrAvailability.PARTIALLY_AVAILABLE ||
                                season.availability == SeerrAvailability.AVAILABLE
                        )
                    }.mapNotNull { season -> season.season.seasonNumber }
                    .toTypedArray(),
            )
        }
    val selectedSeasons =
        remember(seasons, initialSeasonNumber) {
            mutableStateSetOf<Int>(
                *seasons
                    .filter { season ->
                        season.status == RequestStatus.PENDING ||
                            (season.editable && season.season.seasonNumber == initialSeasonNumber)
                    }
                    .mapNotNull { season -> season.season.seasonNumber }
                    .toTypedArray(),
            )
        }

    var profile by remember(is4k) {
        mutableStateOf(
            if (is4k) {
                data.profiles4k.firstOrNull { it.default } ?: data.profiles4k.firstOrNull()
            } else {
                data.profiles.firstOrNull { it.default } ?: data.profiles.firstOrNull()
            },
        )
    }
    var folder by remember(is4k) {
        mutableStateOf(
            if (is4k) {
                data.rootFolders4k.firstOrNull { it.default } ?: data.rootFolders4k.firstOrNull()
            } else {
                data.rootFolders.firstOrNull { it.default } ?: data.rootFolders.firstOrNull()
            },
        )
    }
    val profiles = remember(is4k, data) { if (is4k) data.profiles4k else data.profiles }
    val folders = remember(is4k, data) { if (is4k) data.rootFolders4k else data.rootFolders }
    val initialSeason =
        remember(seasons, initialSeasonNumber) {
            seasons.firstOrNull { it.season.seasonNumber == initialSeasonNumber }
        }
    val remainingSeasons =
        remember(seasons, initialSeasonNumber) {
            seasons.filterNot { it.season.seasonNumber == initialSeasonNumber }
        }
    var moreSeasonsExpanded by rememberSaveable(initialSeasonNumber) {
        mutableStateOf(initialSeasonNumber == null)
    }
    var advancedOptionsExpanded by rememberSaveable(initialSeasonNumber) { mutableStateOf(false) }
    val submitFocusRequester = remember { FocusRequester() }
    val seasonsContentBringIntoViewRequester = remember { BringIntoViewRequester() }
    val advancedContentBringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val hasAdvancedOptions =
        request4kEnabled ||
            (profiles.isNotEmpty() && profile != null) ||
            (folders.isNotEmpty() && folder != null)

    LaunchedEffect(Unit) {
        submitFocusRequester.tryRequestFocus()
    }

    fun submit() {
        onSubmit.invoke(
            TvRequest(
                data = data,
                tvId = id,
                seasons = selectedSeasons.toList(),
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
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                ) {
                    Button(
                        onClick = ::submit,
                        enabled = selectedSeasons.isNotEmpty(),
                        modifier = Modifier.focusRequester(submitFocusRequester),
                    ) {
                        Text(text = stringResource(R.string.submit_request))
                    }
                }
            }
            initialSeason?.let { season ->
                item(key = "initial_${season.season.seasonNumber}") {
                    val seasonNumber = season.season.seasonNumber
                    val checked = seasonNumber in selectedSeasons || seasonNumber in availableSeasons
                    SeasonListItem(
                        season = season,
                        checked = checked,
                        onClick = {
                            if (checked) {
                                selectedSeasons.remove(seasonNumber)
                            } else {
                                seasonNumber?.let { selectedSeasons.add(it) }
                            }
                        },
                    )
                }
            }
            item {
                RequestSectionHeader(
                    title =
                        stringResource(
                            if (initialSeasonNumber == null) {
                                R.string.tv_seasons
                            } else {
                                R.string.more_seasons
                            },
                        ),
                    expanded = moreSeasonsExpanded,
                    onClick = {
                        moreSeasonsExpanded = !moreSeasonsExpanded
                        if (moreSeasonsExpanded) {
                            scope.launch {
                                yield()
                                seasonsContentBringIntoViewRequester.bringIntoView()
                            }
                        }
                    },
                )
            }
            if (moreSeasonsExpanded) {
                item {
                    val isSelected = selectedSeasons.containsAll(allSeasonNumbers)
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(seasonsContentBringIntoViewRequester),
                    ) {
                        ClickSwitch(
                            label = stringResource(R.string.select_all),
                            checked = isSelected,
                            onClick = {
                                if (isSelected) {
                                    selectedSeasons.removeAll(allSeasonNumbers)
                                } else {
                                    selectedSeasons.addAll(allSeasonNumbers)
                                }
                            },
                        )
                    }
                }
                itemsIndexed(
                    items = remainingSeasons,
                    key = { _, season ->
                        season.season.seasonNumber ?: season.season.id ?: season.hashCode()
                    },
                ) { _, season ->
                    val seasonNumber = season.season.seasonNumber
                    val checked = seasonNumber in selectedSeasons || seasonNumber in availableSeasons
                    SeasonListItem(
                        season = season,
                        checked = checked,
                        onClick = {
                            if (checked) {
                                selectedSeasons.remove(seasonNumber)
                            } else {
                                seasonNumber?.let { selectedSeasons.add(it) }
                            }
                        },
                    )
                }
            }
            item {
                HorizontalDivider()
                RequestSectionHeader(
                    title = stringResource(R.string.advanced_options),
                    expanded = advancedOptionsExpanded,
                    onClick = {
                        advancedOptionsExpanded = !advancedOptionsExpanded
                        if (advancedOptionsExpanded && hasAdvancedOptions) {
                            scope.launch {
                                yield()
                                advancedContentBringIntoViewRequester.bringIntoView()
                            }
                        }
                    },
                )
            }
            if (advancedOptionsExpanded) {
                if (request4kEnabled) {
                    item {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(advancedContentBringIntoViewRequester),
                        ) {
                            ClickSwitch(
                                label = stringResource(R.string.request_4k),
                                checked = is4k,
                                onClick = { is4k = !is4k },
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
                                modifier =
                                    if (!request4kEnabled) {
                                        Modifier.bringIntoViewRequester(
                                            advancedContentBringIntoViewRequester,
                                        )
                                    } else {
                                        Modifier
                                    },
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
                                modifier =
                                    if (!request4kEnabled && profiles.isEmpty()) {
                                        Modifier.bringIntoViewRequester(
                                            advancedContentBringIntoViewRequester,
                                        )
                                    } else {
                                        Modifier
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestSectionHeader(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    ClickSurface(
        onClick = onClick,
        modifier =
            Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            Text(text = title)
            Text(text = if (expanded) "−" else "+")
        }
    }
}

@Composable
fun SeasonListItem(
    season: RequestSeason,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        enabled = season.editable,
        selected = false,
        headlineContent = {
            val seasonNumber = season.season.seasonNumber
            Text(
                text =
                    when (seasonNumber) {
                        0 -> stringResource(R.string.specials)
                        null -> season.season.name ?: stringResource(R.string.unknown)
                        else -> stringResource(R.string.tv_season) + " $seasonNumber"
                    },
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(32.dp),
            ) {
                when (season.availability) {
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

                    SeerrAvailability.UNKNOWN,
                    SeerrAvailability.DELETED,
                    SeerrAvailability.BLOCKLISTED,
                    -> {
                        if (season.editable) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        },
        trailingContent = {
            Row {
                Switch(
                    checked = checked,
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
    data: SeerrRequestData,
    seasons: List<RequestSeason>,
    seasons4k: List<RequestSeason>,
    request4kEnabled: Boolean,
    initialSeasonNumber: Int? = null,
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
                LoadingPage(
                    focusEnabled = false,
                    modifier =
                        Modifier
                            .width(400.dp)
                            .height(280.dp),
                )
            }

            LoadingState.Success -> {
                RequestSeasons(
                    id = id,
                    title = title,
                    data = data,
                    seasons = seasons,
                    seasons4k = seasons4k,
                    request4kEnabled = request4kEnabled,
                    initialSeasonNumber = initialSeasonNumber,
                    onSubmit = onSubmit,
                    modifier = Modifier.padding(16.dp),
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
                status = RequestStatus.UNKNOWN,
                availability =
                    if (it < 3) {
                        SeerrAvailability.AVAILABLE
                    } else {
                        SeerrAvailability.UNKNOWN
                    },
                editable = it >= 3,
            )
        }

    WholphinTheme {
        RequestSeasons(
            id = 1,
            title = "Series title",
            seasons = seasons,
            seasons4k = emptyList(),
            data =
                SeerrRequestData(
                    profiles4k =
                        listOf(
                            SeerrProfile(1, "HD", true),
                            SeerrProfile(2, "Ultra HD", false),
                        ),
                    rootFolders4k =
                        listOf(
                            SeerrRootFolder(1, "/tv", "400GB", true),
                        ),
                ),
            request4kEnabled = true,
            onSubmit = { },
            modifier = Modifier.width(400.dp),
        )
    }
}
