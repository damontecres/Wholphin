package com.github.damontecres.wholphin.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.LoadingRow
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.NavigationManager
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.RowLoadingState
import com.github.damontecres.wholphin.util.formatDate
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PersonViewModel
    @Inject
    constructor(
        api: ApiClient,
        val navigationManager: NavigationManager,
    ) : LoadingItemViewModel(api) {
        val movies = MutableLiveData<RowLoadingState>(RowLoadingState.Pending)
        val series = MutableLiveData<RowLoadingState>(RowLoadingState.Pending)
        val episodes = MutableLiveData<RowLoadingState>(RowLoadingState.Pending)

        fun init(itemId: UUID) {
            viewModelScope.launchIO(
                LoadingExceptionHandler(
                    loading,
                    "Error loading item $itemId",
                ),
            ) {
                super.init(itemId, null).await()?.let { person ->
                    val dto = person.data
                    if ((dto.movieCount ?: 0) > 0) {
                        fetchRow(person.id, BaseItemKind.MOVIE, movies)
                    } else {
                        movies.setValueOnMain(RowLoadingState.Success(listOf()))
                    }
                    if ((dto.seriesCount ?: 0) > 0) {
                        fetchRow(person.id, BaseItemKind.SERIES, series)
                    } else {
                        series.setValueOnMain(RowLoadingState.Success(listOf()))
                    }
                    if ((dto.episodeCount ?: 0) > 0) {
                        fetchRow(person.id, BaseItemKind.EPISODE, episodes)
                    } else {
                        episodes.setValueOnMain(RowLoadingState.Success(listOf()))
                    }
                }
            }
        }

        private fun fetchRow(
            itemId: UUID,
            type: BaseItemKind,
            target: MutableLiveData<RowLoadingState>,
        ) {
            viewModelScope.launchIO {
                target.setValueOnMain(RowLoadingState.Loading)
                try {
                    val request =
                        GetItemsRequest(
                            personIds = listOf(itemId),
                            includeItemTypes = listOf(type),
                            fields = SlimItemFields,
                            recursive = true,
                            sortBy = listOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME),
                            sortOrder = listOf(SortOrder.DESCENDING, SortOrder.DESCENDING, SortOrder.ASCENDING),
                        )
                    val pager =
                        ApiRequestPager(
                            api,
                            request,
                            GetItemsRequestHandler,
                            viewModelScope,
                            pageSize = 15,
                            useSeriesForPrimary = false,
                        ).init()
                    target.setValueOnMain(RowLoadingState.Success(pager))
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching $type for $itemId")
                    target.setValueOnMain(RowLoadingState.Error(ex))
                }
            }
        }
    }

@Composable
fun PersonPage(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: PersonViewModel = hiltViewModel(),
) {
    OneTimeLaunchedEffect {
        viewModel.init(destination.itemId)
    }
    val person by viewModel.item.observeAsState()
    val movies by viewModel.movies.observeAsState(RowLoadingState.Pending)
    val series by viewModel.series.observeAsState(RowLoadingState.Pending)
    val episodes by viewModel.episodes.observeAsState(RowLoadingState.Pending)

    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state, modifier)
        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage(modifier)

        LoadingState.Success -> {
            person?.let { person ->
                var showOverviewDialog by remember { mutableStateOf(false) }
                val name = person.name ?: person.id.toString()
                PersonPageContent(
                    preferences = preferences,
                    name = name,
                    overview = person.data.overview,
                    imageUrl = person.imageUrl,
                    birthdate = person.data.premiereDate?.toLocalDate(),
                    deathdate = person.data.endDate?.toLocalDate(),
                    birthPlace = person.data.productionLocations?.firstOrNull(),
                    movies = movies,
                    series = series,
                    episodes = episodes,
                    onClickItem = {
                        viewModel.navigationManager.navigateTo(it.destination())
                    },
                    overviewOnClick = { showOverviewDialog = true },
                    modifier = modifier,
                )
                AnimatedVisibility(showOverviewDialog) {
                    ItemDetailsDialog(
                        info =
                            ItemDetailsDialogInfo(
                                title = name,
                                overview = person.data.overview,
                                files = listOf(),
                            ),
                        showFilePath = false,
                        onDismissRequest = { showOverviewDialog = false },
                    )
                }
            }
        }
    }
}

private const val HEADER_ROW = 0
private const val MOVIE_ROW = 1
private const val SERIES_ROW = 2
private const val EPISODE_ROW = 3

@Composable
fun PersonPageContent(
    preferences: UserPreferences,
    name: String,
    overview: String?,
    imageUrl: String?,
    birthdate: LocalDate?,
    deathdate: LocalDate?,
    birthPlace: String?,
    movies: RowLoadingState,
    series: RowLoadingState,
    episodes: RowLoadingState,
    onClickItem: (BaseItem) -> Unit,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val headerFocusRequester = remember { FocusRequester() }
    var position by rememberPosition()
    LaunchedEffect(Unit) {
        if (position.row > HEADER_ROW) {
            focusRequester.tryRequestFocus()
        } else {
            headerFocusRequester.tryRequestFocus()
        }
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            modifier
                .padding(start = 16.dp),
    ) {
        item {
            PersonHeader(
                name = name,
                overview = overview,
                imageUrl = imageUrl,
                birthdate = birthdate,
                birthPlace = birthPlace,
                deathdate = deathdate,
                overviewOnClick = overviewOnClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .padding(top = 16.dp, bottom = 40.dp)
                        .focusRequester(headerFocusRequester),
            )
        }
        item {
            LoadingRow(
                title = rowTitle("Movies", movies),
                state = movies,
                rowIndex = MOVIE_ROW,
                position = position,
                focusRequester = focusRequester,
                onClickItem = onClickItem,
                onClickPosition = { position = it },
                showIfEmpty = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            LoadingRow(
                title = rowTitle("Series", series),
                state = series,
                rowIndex = SERIES_ROW,
                position = position,
                focusRequester = focusRequester,
                onClickItem = onClickItem,
                onClickPosition = { position = it },
                showIfEmpty = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            LoadingRow(
                title = rowTitle("Episodes", episodes),
                state = episodes,
                rowIndex = EPISODE_ROW,
                position = position,
                focusRequester = focusRequester,
                onClickItem = onClickItem,
                onClickPosition = { position = it },
                showIfEmpty = false,
                horizontalPadding = 32.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

fun rowTitle(
    prefix: String,
    state: RowLoadingState,
): String =
    if (state is RowLoadingState.Success) {
        "$prefix (${state.items.size})"
    } else {
        prefix
    }

@Composable
fun PersonHeader(
    name: String,
    overview: String?,
    imageUrl: String?,
    birthdate: LocalDate?,
    deathdate: LocalDate?,
    birthPlace: String?,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        modifier = modifier,
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = name,
            modifier =
                Modifier
//                    .fillMaxWidth(.25f)
                    .weight(1f)
                    .clip(RoundedCornerShape(10)),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
//                    .fillMaxWidth(.7f)
                    .weight(3f)
                    .padding(top = 16.dp, end = 32.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        RoundedCornerShape(10),
                    ).padding(16.dp),
        ) {
            Text(
                text = name,
                color = MaterialTheme.colorScheme.onSurface,
                style =
                    MaterialTheme.typography.displaySmall.copy(
                        shadow =
                            Shadow(
                                color = Color.DarkGray,
                                offset = Offset(5f, 2f),
                                blurRadius = 2f,
                            ),
                    ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            birthdate?.let {
                val age = if (deathdate == null) birthdate.until(LocalDate.now())?.years else null
                val text =
                    if (age != null) {
                        "Born: ${formatDate(it)} ($age years old)"
                    } else {
                        "Born: ${formatDate(it)}"
                    }
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            birthPlace?.let {
                Text(
                    text = "Birthplace: $it",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            deathdate?.let {
                val age = birthdate?.until(it)?.years
                val text =
                    if (age != null) {
                        "Died: ${formatDate(it)} ($age years old)"
                    } else {
                        "Died: ${formatDate(it)}"
                    }
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OverviewText(
                overview = overview ?: "",
                maxLines = 3,
                onClick = overviewOnClick,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@PreviewTvSpec
@Composable
private fun PersonPreview() {
    WholphinTheme {
        PersonHeader(
            name = "John Smith",
            overview = "John Smith is an actor",
            imageUrl = null,
            birthdate = LocalDate.of(1975, 3, 22),
            birthPlace = "Phoenix, Arizona, USA",
            deathdate = LocalDate.of(2025, 2, 1),
            overviewOnClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
