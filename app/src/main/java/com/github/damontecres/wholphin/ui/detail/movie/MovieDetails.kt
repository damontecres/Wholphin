package com.github.damontecres.wholphin.ui.detail.movie

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.data.model.aspectRatioFloat
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.TrailerService
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.RequestOrRestoreFocus
import com.github.damontecres.wholphin.ui.cards.ChapterRow
import com.github.damontecres.wholphin.ui.cards.ExtrasRow
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.PersonRow
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.components.ConfirmDeleteDialog
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButtons
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.Optional
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItemsForPerson
import com.github.damontecres.wholphin.ui.discover.DiscoverRow
import com.github.damontecres.wholphin.ui.discover.DiscoverRowData
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.DiscoverRequestType
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUID
import java.util.UUID
import kotlin.time.Duration

@Composable
fun MovieDetails(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: MovieViewModel =
        hiltViewModel<MovieViewModel, MovieViewModel.Factory>(
            creationCallback = { it.create(destination.itemId) },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.init()
        onPauseOrDispose { }
    }
    val state by viewModel.state.collectAsState()

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var moreDialog by remember { mutableStateOf<DialogParams?>(null) }
    var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    var chooseVersion by remember { mutableStateOf<DialogParams?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)
    var showDeleteDialog by remember { mutableStateOf<BaseItem?>(null) }

    val preferredSubtitleLanguage =
        viewModel.serverRepository.currentUserDto
            .observeAsState()
            .value
            ?.configuration
            ?.subtitleLanguagePreference

    val moreActions =
        ContextMenuActions(
            navigateTo = viewModel::navigateTo,
            onClickWatch = { itemId, watched ->
                viewModel.setWatched(itemId, watched)
            },
            onClickFavorite = { itemId, favorite ->
                viewModel.setFavorite(itemId, favorite)
            },
            onClickAddPlaylist = { itemId ->
                playlistViewModel.loadPlaylists(MediaType.VIDEO)
                showPlaylistDialog.makePresent(itemId)
            },
            onSendMediaInfo = viewModel.mediaReportService::sendReportFor,
            onClickDelete = { showDeleteDialog = it },
            onChooseVersion = { item, source ->
                viewModel.savePlayVersion(
                    item,
                    source.id!!.toUUID(),
                )
            },
            onChooseTracks = { result ->
                viewModel.saveTrackSelection(
                    result.item,
                    result.itemPlayback,
                    result.trackIndex,
                    result.streamType,
                )
            },
            onShowOverview = { overviewDialog = ItemDetailsDialogInfo(it) },
            onClearChosenStreams = { viewModel.clearChosenStreams(it) },
        )

    when (val s = state.loading) {
        is DataLoadingState.Error -> {
            ErrorMessage(s, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success -> {
            val unknownStr = stringResource(R.string.unknown)
            val movie by rememberUpdatedState(s.data)
            val chosenStreams by rememberUpdatedState(state.chosenStreams)
            LifecycleResumeEffect(destination.itemId) {
                viewModel.maybePlayThemeSong(
                    destination.itemId,
                    preferences.appPreferences.interfacePreferences.playThemeSongs,
                )
                onPauseOrDispose {
                    viewModel.release()
                }
            }
            MovieDetailsContent(
                preferences = preferences,
                movie = movie,
                state = state,
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
                playOnClick = {
                    viewModel.navigateTo(
                        Destination.Playback(
                            movie.id,
                            it.inWholeMilliseconds,
                        ),
                    )
                },
                overviewOnClick = {
                    overviewDialog =
                        ItemDetailsDialogInfo(movie)
                },
                moreOnClick = {
                    showContextMenu =
                        ContextMenu(
                            fromLongClick = false,
                            item = movie,
                            chosenStreams = chosenStreams,
                            showGoTo = false,
                            showStreamChoices = true,
                            canDelete = state.canDelete,
                            canRemoveContinueWatching = false,
                            canRemoveNextUp = false,
                        )
                },
                watchOnClick = {
                    viewModel.setWatched(movie.id, !movie.played)
                },
                favoriteOnClick = {
                    viewModel.setFavorite(movie.id, !movie.favorite)
                },
                onLongClickPerson = { index, person ->
                    val items =
                        buildMoreDialogItemsForPerson(
                            context = context,
                            person = person,
                            navigateTo = viewModel::navigateTo,
                            onClickFavorite = viewModel::setFavorite,
                        )
                    moreDialog =
                        DialogParams(
                            fromLongClick = true,
                            title = person.name ?: "",
                            items = items,
                        )
                },
                onLongClickSimilar = { _, similar ->
                    showContextMenu =
                        ContextMenu(
                            fromLongClick = true,
                            item = similar,
                            chosenStreams = null,
                            showGoTo = true,
                            showStreamChoices = false,
                            canDelete = false,
                            canRemoveContinueWatching = false,
                            canRemoveNextUp = false,
                        )
                },
                trailerOnClick = {
                    TrailerService.onClick(context, it, viewModel::navigateTo)
                },
                onClickExtra = { index, extra ->
                    viewModel.navigateTo(extra.destination)
                },
                onClickDiscover = { index, item ->
                    viewModel.navigateTo(item.destination)
                },
                canDelete = state.canDelete,
                deleteOnClick = { showDeleteDialog = state.movie },
                modifier = modifier,
            )
        }
    }
    showContextMenu?.let { contextMenu ->
        ContextMenuDialog(
            onDismissRequest = { showContextMenu = null },
            streamChoiceService = viewModel.streamChoiceService,
            contextMenu = contextMenu,
            preferredSubtitleLanguage = preferredSubtitleLanguage,
            actions = moreActions,
        )
    }
    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            showFilePath =
                viewModel.serverRepository.currentUserDto.value
                    ?.policy
                    ?.isAdministrator == true,
            onDismissRequest = { overviewDialog = null },
        )
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
    chooseVersion?.let { params ->
        DialogPopup(
            showDialog = true,
            title = params.title,
            dialogItems = params.items,
            onDismissRequest = { chooseVersion = null },
            dismissOnClick = true,
            waitToLoad = params.fromLongClick,
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
    showDeleteDialog?.let { item ->
        ConfirmDeleteDialog(
            itemTitle = item.title ?: "",
            onCancel = { showDeleteDialog = null },
            onConfirm = {
                viewModel.deleteItem(item)
                showDeleteDialog = null
            },
        )
    }
}

private const val HEADER_ROW = 0
private const val PEOPLE_ROW = HEADER_ROW + 1
private const val TRAILER_ROW = PEOPLE_ROW + 1
private const val CHAPTER_ROW = TRAILER_ROW + 1
private const val EXTRAS_ROW = CHAPTER_ROW + 1
private const val SIMILAR_ROW = EXTRAS_ROW + 1
private const val DISCOVER_ROW = SIMILAR_ROW + 1

@Composable
fun MovieDetailsContent(
    preferences: UserPreferences,
    movie: BaseItem,
    state: MovieState,
    playOnClick: (Duration) -> Unit,
    trailerOnClick: (Trailer) -> Unit,
    overviewOnClick: () -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    onClickItem: (Int, BaseItem) -> Unit,
    onClickPerson: (Person) -> Unit,
    onLongClickPerson: (Int, Person) -> Unit,
    onLongClickSimilar: (Int, BaseItem) -> Unit,
    onClickExtra: (Int, ExtrasItem) -> Unit,
    onClickDiscover: (Int, DiscoverItem) -> Unit,
    canDelete: Boolean,
    deleteOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var position by rememberInt(0)
    val focusRequesters = remember { List(DISCOVER_ROW + 1) { FocusRequester() } }
    val dto = movie.data
    val resumePosition = dto.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO

    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    RequestOrRestoreFocus(focusRequesters.getOrNull(position))

    Box(modifier = modifier) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(bringIntoViewRequester),
                ) {
                    MovieDetailsHeader(
                        preferences = preferences,
                        movie = movie,
                        chosenStreams = state.chosenStreams,
                        bringIntoViewRequester = bringIntoViewRequester,
                        overviewOnClick = overviewOnClick,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = HeaderUtils.topPadding, bottom = 16.dp),
                    )
                    ExpandablePlayButtons(
                        resumePosition = resumePosition,
                        watched = dto.userData?.played ?: false,
                        favorite = dto.userData?.isFavorite ?: false,
                        playOnClick = {
                            position = HEADER_ROW
                            playOnClick.invoke(it)
                        },
                        moreOnClick = moreOnClick,
                        watchOnClick = watchOnClick,
                        favoriteOnClick = favoriteOnClick,
                        buttonOnFocusChanged = {
                            if (it.isFocused) {
                                position = HEADER_ROW
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                        trailers = state.trailers,
                        trailerOnClick = {
                            position = TRAILER_ROW
                            trailerOnClick.invoke(it)
                        },
                        canDelete = canDelete,
                        deleteOnClick = deleteOnClick,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .focusRequester(focusRequesters[HEADER_ROW]),
                    )
                }
            }
            state.people.letNotEmpty { people ->
                item {
                    PersonRow(
                        people = people,
                        onClick = {
                            position = PEOPLE_ROW
                            onClickPerson.invoke(it)
                        },
                        onLongClick = { index, person ->
                            position = PEOPLE_ROW
                            onLongClickPerson.invoke(index, person)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[PEOPLE_ROW]),
                    )
                }
            }
            state.chapters.letNotEmpty { chapters ->
                item {
                    ChapterRow(
                        chapters = chapters,
                        aspectRatio = movie.data.aspectRatioFloat ?: AspectRatios.WIDE,
                        onClick = {
                            position = CHAPTER_ROW
                            playOnClick.invoke(it.position)
                        },
                        onLongClick = {},
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[CHAPTER_ROW]),
                    )
                }
            }
            state.extras.letNotEmpty { extras ->
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
            state.similar.letNotEmpty { similar ->
                item {
                    val imageHeight =
                        remember(movie.type) {
                            if (movie.type == BaseItemKind.MOVIE) {
                                Cards.height2x3
                            } else {
                                Cards.heightEpisode
                            }
                        }
                    ItemRow(
                        title = stringResource(R.string.more_like_this),
                        items = similar,
                        onClickItem = { index, item ->
                            position = SIMILAR_ROW
                            onClickItem.invoke(index, item)
                        },
                        onLongClickItem = { index, similar ->
                            position = SIMILAR_ROW
                            onLongClickSimilar.invoke(index, similar)
                        },
                        cardContent = { index, item, mod, onClick, onLongClick ->
                            SeasonCard(
                                item = item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                modifier = mod,
                                showImageOverlay = true,
                                imageHeight = imageHeight,
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
            state.discovered.letNotEmpty { discovered ->
                item {
                    DiscoverRow(
                        row =
                            DiscoverRowData(
                                stringResource(R.string.discover),
                                DataLoadingState.Success(discovered),
                                type = DiscoverRequestType.UNKNOWN,
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
