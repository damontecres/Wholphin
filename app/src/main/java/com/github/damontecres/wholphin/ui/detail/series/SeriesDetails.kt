package com.github.damontecres.wholphin.ui.detail.series

import android.content.Context
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.TrailerService
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.RequestOrRestoreFocus
import com.github.damontecres.wholphin.ui.cards.ExtrasRow
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.PersonRow
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import com.github.damontecres.wholphin.ui.components.GenreText
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.Optional
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.components.TrailerButton
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.detail.MoreDialogActions
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItemsForHome
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItemsForPerson
import com.github.damontecres.wholphin.ui.discover.DiscoverRow
import com.github.damontecres.wholphin.ui.discover.DiscoverRowData
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
import java.util.UUID
import kotlin.time.Duration

@Composable
fun SeriesDetails(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel =
        hiltViewModel<SeriesViewModel, SeriesViewModel.Factory>(
            creationCallback = {
                it.create(destination.itemId, null, SeriesPageType.DETAILS)
            },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)

    val item by viewModel.item.observeAsState()
    val seasons by viewModel.seasons.observeAsState(listOf())
    val trailers by viewModel.trailers.observeAsState(listOf())
    val extras by viewModel.extras.observeAsState(listOf())
    val people by viewModel.people.observeAsState(listOf())
    val similar by viewModel.similar.observeAsState(listOf())
    val discovered by viewModel.discovered.collectAsState()

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var showWatchConfirmation by remember { mutableStateOf(false) }
    var seasonDialog by remember { mutableStateOf<DialogParams?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)

    when (val state = loading) {
        is LoadingState.Error -> {
            ErrorMessage(state)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage()
        }

        LoadingState.Success -> {
            item?.let { item ->
                LifecycleResumeEffect(destination.itemId) {
                    viewModel.onResumePage()

                    onPauseOrDispose {
                        viewModel.release()
                    }
                }

                val played = item.data.userData?.played ?: false
                SeriesDetailsContent(
                    preferences = preferences,
                    series = item,
                    seasons = seasons,
                    trailers = trailers,
                    extras = extras,
                    people = people,
                    similar = similar,
                    played = played,
                    favorite = item.data.userData?.isFavorite ?: false,
                    modifier = modifier,
                    onClickItem = { index, item ->
                        viewModel.navigateTo(item.destination())
                    },
                    onClickPerson = {
                        viewModel.navigateTo(
                            Destination.MediaItem(
                                it.id,
                                BaseItemKind.PERSON,
                            ),
                        )
                    },
                    onLongClickItem = { index, season ->
                        seasonDialog =
                            buildDialogForSeason(
                                context = context,
                                s = season,
                                onClickItem = { viewModel.navigateTo(it.destination()) },
                                markPlayed = { played ->
                                    viewModel.setSeasonWatched(season.id, played)
                                },
                                onClickPlay = { shuffle ->
                                    viewModel.navigateTo(
                                        Destination.PlaybackList(
                                            itemId = season.id,
                                            shuffle = shuffle,
                                        ),
                                    )
                                },
                            )
                    },
                    overviewOnClick = {
                        overviewDialog =
                            ItemDetailsDialogInfo(
                                title = item.name ?: context.getString(R.string.unknown),
                                overview = item.data.overview,
                                genres = item.data.genres.orEmpty(),
                                files = listOf(),
                            )
                    },
                    playOnClick = { shuffle ->
                        if (shuffle) {
                            viewModel.navigateTo(
                                Destination.PlaybackList(
                                    itemId = item.id,
                                    shuffle = true,
                                ),
                            )
                        } else {
                            viewModel.playNextUp()
                        }
                    },
                    watchOnClick = { showWatchConfirmation = true },
                    favoriteOnClick = {
                        val favorite = item.data.userData?.isFavorite ?: false
                        viewModel.setFavorite(item.id, !favorite, null)
                    },
                    trailerOnClick = {
                        TrailerService.onClick(context, it, viewModel::navigateTo)
                    },
                    onClickExtra = { _, extra ->
                        viewModel.navigateTo(extra.destination)
                    },
                    discovered = discovered,
                    onClickDiscover = { index, item ->
                        viewModel.navigateTo(item.destination)
                    },
                    moreActions =
                        MoreDialogActions(
                            navigateTo = { viewModel.navigateTo(it) },
                            onClickWatch = { itemId, played ->
                                viewModel.setWatched(itemId, played, null)
                            },
                            onClickFavorite = { itemId, played ->
                                viewModel.setFavorite(itemId, played, null)
                            },
                            onClickAddPlaylist = { itemId ->
                                playlistViewModel.loadPlaylists(MediaType.VIDEO)
                                showPlaylistDialog.makePresent(itemId)
                            },
                            onSendMediaInfo = viewModel.mediaReportService::sendReportFor,
                        ),
                )
                if (showWatchConfirmation) {
                    ConfirmDialog(
                        title = item.name ?: "",
                        body =
                            stringResource(if (played) R.string.mark_entire_series_as_unplayed else R.string.mark_entire_series_as_played),
                        onCancel = {
                            showWatchConfirmation = false
                        },
                        onConfirm = {
                            viewModel.setWatchedSeries(!played)
                            showWatchConfirmation = false
                        },
                    )
                }
            }
        }
    }
    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            showFilePath = false,
            onDismissRequest = { overviewDialog = null },
        )
    }
    seasonDialog?.let { params ->
        DialogPopup(
            showDialog = true,
            title = params.title,
            dialogItems = params.items,
            waitToLoad = params.fromLongClick,
            onDismissRequest = { seasonDialog = null },
        )
    }
    showPlaylistDialog.compose { itemId ->
        PlaylistDialog(
            title = stringResource(R.string.add_to_playlist),
            state = playlistState,
            onDismissRequest = { showPlaylistDialog.makeAbsent() },
            onClick = {
                playlistViewModel.addToPlaylist(it.id, itemId)
                showPlaylistDialog.makeAbsent()
            },
            createEnabled = true,
            onCreatePlaylist = {
                playlistViewModel.createPlaylistAndAddItem(it, itemId)
                showPlaylistDialog.makeAbsent()
            },
            elevation = 3.dp,
        )
    }
}

private const val HEADER_ROW = 0
private const val SEASONS_ROW = HEADER_ROW + 1
private const val PEOPLE_ROW = SEASONS_ROW + 1
private const val TRAILER_ROW = PEOPLE_ROW + 1
private const val EXTRAS_ROW = TRAILER_ROW + 1
private const val SIMILAR_ROW = EXTRAS_ROW + 1
private const val DISCOVER_ROW = SIMILAR_ROW + 1

@Composable
fun SeriesDetailsContent(
    preferences: UserPreferences,
    series: BaseItem,
    seasons: List<BaseItem?>,
    similar: List<BaseItem>,
    trailers: List<Trailer>,
    extras: List<ExtrasItem>,
    people: List<Person>,
    discovered: List<DiscoverItem>,
    played: Boolean,
    favorite: Boolean,
    onClickItem: (Int, BaseItem) -> Unit,
    onClickPerson: (Person) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    overviewOnClick: () -> Unit,
    playOnClick: (Boolean) -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    trailerOnClick: (Trailer) -> Unit,
    onClickExtra: (Int, ExtrasItem) -> Unit,
    moreActions: MoreDialogActions,
    onClickDiscover: (Int, DiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    var position by rememberInt()
    val focusRequesters = remember { List(DISCOVER_ROW + 1) { FocusRequester() } }
    val playFocusRequester = remember { FocusRequester() }
    RequestOrRestoreFocus(focusRequesters.getOrNull(position))
    var moreDialog by remember { mutableStateOf<DialogParams?>(null) }

    Box(
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier,
            ) {
                item {
                    SeriesDetailsHeader(
                        series = series,
                        overviewOnClick = overviewOnClick,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(bringIntoViewRequester)
                                .padding(top = 32.dp, bottom = 16.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier =
                            Modifier
                                .padding(start = 8.dp)
                                .focusRequester(focusRequesters[HEADER_ROW])
                                .focusRestorer(playFocusRequester)
                                .focusGroup()
                                .padding(bottom = 16.dp),
                    ) {
                        ExpandablePlayButton(
                            title = R.string.play,
                            resume = Duration.ZERO,
                            icon = Icons.Default.PlayArrow,
                            onClick = {
                                position = HEADER_ROW
                                playOnClick.invoke(false)
                            },
                            modifier =
                                Modifier
                                    .focusRequester(playFocusRequester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            scope.launch(ExceptionHandler()) {
                                                bringIntoViewRequester.bringIntoView()
                                            }
                                        }
                                    },
                        )
                        ExpandableFaButton(
                            title = R.string.shuffle,
                            iconStringRes = R.string.fa_shuffle,
                            onClick = {
                                position = HEADER_ROW
                                playOnClick.invoke(true)
                            },
                            modifier =
                                Modifier.onFocusChanged {
                                    if (it.isFocused) {
                                        scope.launch(ExceptionHandler()) {
                                            bringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                        )
                        ExpandableFaButton(
                            title = if (played) R.string.mark_unwatched else R.string.mark_watched,
                            iconStringRes = if (played) R.string.fa_eye else R.string.fa_eye_slash,
                            onClick = watchOnClick,
                            modifier =
                                Modifier.onFocusChanged {
                                    if (it.isFocused) {
                                        scope.launch(ExceptionHandler()) {
                                            bringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                        )
                        ExpandableFaButton(
                            title = if (favorite) R.string.remove_favorite else R.string.add_favorite,
                            iconStringRes = R.string.fa_heart,
                            onClick = favoriteOnClick,
                            iconColor = if (favorite) Color.Red else Color.Unspecified,
                            modifier =
                                Modifier.onFocusChanged {
                                    if (it.isFocused) {
                                        scope.launch(ExceptionHandler()) {
                                            bringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                        )
                        TrailerButton(
                            trailers = trailers,
                            trailerOnClick = trailerOnClick,
                            modifier =
                                Modifier.onFocusChanged {
                                    if (it.isFocused) {
                                        scope.launch(ExceptionHandler()) {
                                            bringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                        )
                    }
                }
                item {
                    ItemRow(
                        title = stringResource(R.string.tv_seasons) + " (${seasons.size})",
                        items = seasons,
                        onClickItem = { index, item ->
                            position = SEASONS_ROW
                            onClickItem.invoke(index, item)
                        },
                        onLongClickItem = { index, item ->
                            position = SEASONS_ROW
                            onLongClickItem.invoke(index, item)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[SEASONS_ROW]),
                        cardContent = @Composable { index, item, mod, onClick, onLongClick ->
                            SeasonCard(
                                item = item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                imageHeight = Cards.height2x3,
                                imageWidth = Dp.Unspecified,
                                showImageOverlay = true,
                                modifier = mod,
                            )
                        },
                    )
                }
                if (people.isNotEmpty()) {
                    item {
                        PersonRow(
                            people = people,
                            onClick = {
                                position = PEOPLE_ROW
                                onClickPerson.invoke(it)
                            },
                            onLongClick = { index, person ->
                                position = PEOPLE_ROW
                                val items =
                                    buildMoreDialogItemsForPerson(
                                        context = context,
                                        person = person,
                                        actions = moreActions,
                                    )
                                moreDialog =
                                    DialogParams(
                                        fromLongClick = true,
                                        title = person.name ?: "",
                                        items = items,
                                    )
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesters[PEOPLE_ROW]),
                        )
                    }
                }
                if (extras.isNotEmpty()) {
                    item {
                        ExtrasRow(
                            extras = extras,
                            onClickItem = { index, item ->
                                position = EXTRAS_ROW
                                onClickExtra.invoke(index, item)
                            },
                            onLongClickItem = { _, _ -> },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesters[EXTRAS_ROW]),
                        )
                    }
                }
                if (similar.isNotEmpty()) {
                    item {
                        ItemRow(
                            title = stringResource(R.string.more_like_this),
                            items = similar,
                            onClickItem = { index, item ->
                                position = SIMILAR_ROW
                                onClickItem.invoke(index, item)
                            },
                            onLongClickItem = { index, item ->
                                position = SIMILAR_ROW
                                val items =
                                    buildMoreDialogItemsForHome(
                                        context = context,
                                        item = item,
                                        seriesId = null,
                                        playbackPosition = item.playbackPosition,
                                        watched = item.played,
                                        favorite = item.favorite,
                                        actions = moreActions,
                                    )
                                moreDialog =
                                    DialogParams(
                                        fromLongClick = true,
                                        title = item.name ?: "",
                                        items = items,
                                    )
                            },
                            cardContent = { index, item, mod, onClick, onLongClick ->
                                SeasonCard(
                                    item = item,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    modifier = mod,
                                    showImageOverlay = true,
                                    imageHeight = Cards.height2x3,
                                    imageWidth = Dp.Unspecified,
                                )
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesters[SIMILAR_ROW]),
                        )
                    }
                }
                if (discovered.isNotEmpty()) {
                    item {
                        DiscoverRow(
                            row =
                                DiscoverRowData(
                                    stringResource(R.string.discover),
                                    DataLoadingState.Success(discovered),
                                ),
                            onClickItem = { index: Int, item: DiscoverItem ->
                                position = DISCOVER_ROW
                                onClickDiscover.invoke(index, item)
                            },
                            onLongClickItem = { _, _ -> },
                            onCardFocus = {},
                            focusRequester = focusRequesters[DISCOVER_ROW],
                        )
                    }
                }
            }
        }
    }
    moreDialog?.let { params ->
        DialogPopup(
            showDialog = true,
            title = params.title,
            dialogItems = params.items,
            onDismissRequest = { moreDialog = null },
            dismissOnClick = true,
            waitToLoad = params.fromLongClick,
        )
    }
}

@Composable
fun SeriesDetailsHeader(
    series: BaseItem,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val dto = series.data
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Text(
            text = series.name ?: stringResource(R.string.unknown),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth(.75f)
                    .padding(start = 8.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(.60f),
        ) {
            QuickDetails(series.ui.quickDetails, null, Modifier.padding(start = 8.dp))
            dto.genres?.letNotEmpty {
                GenreText(it, Modifier.padding(start = 8.dp, bottom = 12.dp))
            }
            dto.overview?.let { overview ->
                OverviewText(
                    overview = overview,
                    maxLines = 3,
                    onClick = overviewOnClick,
                    textBoxHeight = Dp.Unspecified,
                )
            }
        }
    }
}

fun buildDialogForSeason(
    context: Context,
    s: BaseItem,
    onClickItem: (BaseItem) -> Unit,
    markPlayed: (Boolean) -> Unit,
    onClickPlay: (Boolean) -> Unit,
): DialogParams {
    val items =
        buildList {
            add(
                DialogItem(context.getString(R.string.go_to), Icons.Default.PlayArrow) {
                    onClickItem.invoke(s)
                },
            )
            if (s.data.userData?.played == true) {
                add(
                    DialogItem(context.getString(R.string.mark_unwatched), R.string.fa_eye) {
                        markPlayed.invoke(false)
                    },
                )
            } else {
                add(
                    DialogItem(context.getString(R.string.mark_watched), R.string.fa_eye_slash) {
                        markPlayed.invoke(true)
                    },
                )
            }
            add(
                DialogItem(
                    context.getString(R.string.play),
                    Icons.Default.PlayArrow,
                    iconColor = Color.Green.copy(alpha = .8f),
                ) {
                    onClickPlay.invoke(false)
                },
            )
            add(
                DialogItem(
                    context.getString(R.string.shuffle),
                    R.string.fa_shuffle,
                ) {
                    onClickPlay.invoke(true)
                },
            )
        }
    return DialogParams(
        title = s.name ?: context.getString(R.string.tv_season),
        fromLongClick = true,
        items = items,
    )
}
