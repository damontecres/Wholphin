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
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
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
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButtons
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.Optional
import com.github.damontecres.wholphin.ui.components.chooseStream
import com.github.damontecres.wholphin.ui.components.chooseVersionParams
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.detail.MoreDialogActions
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItems
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItemsForHome
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItemsForPerson
import com.github.damontecres.wholphin.ui.discover.DiscoverRow
import com.github.damontecres.wholphin.ui.discover.DiscoverRowData
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
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
    val item by viewModel.item.observeAsState()
    val people by viewModel.people.observeAsState(listOf())
    val chapters by viewModel.chapters.observeAsState(listOf())
    val trailers by viewModel.trailers.observeAsState(listOf())
    val extras by viewModel.extras.observeAsState(listOf())
    val similar by viewModel.similar.observeAsState(listOf())
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    val chosenStreams by viewModel.chosenStreams.observeAsState(null)
    val discovered by viewModel.discovered.collectAsState()

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var moreDialog by remember { mutableStateOf<DialogParams?>(null) }
    var chooseVersion by remember { mutableStateOf<DialogParams?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)

    val moreActions =
        MoreDialogActions(
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
        )

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
            item?.let { movie ->
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
                    chosenStreams = chosenStreams,
                    people = people,
                    chapters = chapters,
                    extras = extras,
                    trailers = trailers,
                    similar = similar,
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
                            ItemDetailsDialogInfo(
                                title = movie.name ?: context.getString(R.string.unknown),
                                overview = movie.data.overview,
                                genres = movie.data.genres.orEmpty(),
                                files = movie.data.mediaSources.orEmpty(),
                            )
                    },
                    moreOnClick = {
                        moreDialog =
                            DialogParams(
                                fromLongClick = false,
                                title = movie.name + " (${movie.data.productionYear ?: ""})",
                                items =
                                    buildMoreDialogItems(
                                        context = context,
                                        item = movie,
                                        watched = movie.data.userData?.played ?: false,
                                        favorite = movie.data.userData?.isFavorite ?: false,
                                        seriesId = null,
                                        sourceId = chosenStreams?.source?.id?.toUUIDOrNull(),
                                        canClearChosenStreams = chosenStreams?.itemPlayback != null || chosenStreams?.plc != null,
                                        actions = moreActions,
                                        onChooseVersion = {
                                            chooseVersion =
                                                chooseVersionParams(
                                                    context,
                                                    movie.data.mediaSources!!,
                                                ) { idx ->
                                                    val source = movie.data.mediaSources!![idx]
                                                    viewModel.savePlayVersion(
                                                        movie,
                                                        source.id!!.toUUID(),
                                                    )
                                                }
                                            moreDialog = null
                                        },
                                        onChooseTracks = { type ->

                                            viewModel.streamChoiceService
                                                .chooseSource(
                                                    movie.data,
                                                    chosenStreams?.itemPlayback,
                                                )?.let { source ->
                                                    chooseVersion =
                                                        chooseStream(
                                                            context = context,
                                                            streams = source.mediaStreams.orEmpty(),
                                                            type = type,
                                                            currentIndex =
                                                                if (type == MediaStreamType.AUDIO) {
                                                                    chosenStreams?.audioStream?.index
                                                                } else {
                                                                    chosenStreams?.subtitleStream?.index
                                                                },
                                                            onClick = { trackIndex ->
                                                                viewModel.saveTrackSelection(
                                                                    movie,
                                                                    chosenStreams?.itemPlayback,
                                                                    trackIndex,
                                                                    type,
                                                                )
                                                            },
                                                        )
                                                }
                                        },
                                        onShowOverview = {
                                            overviewDialog =
                                                ItemDetailsDialogInfo(
                                                    title =
                                                        movie.name
                                                            ?: context.getString(R.string.unknown),
                                                    overview = movie.data.overview,
                                                    genres = movie.data.genres.orEmpty(),
                                                    files = movie.data.mediaSources.orEmpty(),
                                                )
                                        },
                                        onClearChosenStreams = {
                                            viewModel.clearChosenStreams(chosenStreams)
                                        },
                                    ),
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
                                actions = moreActions,
                            )
                        moreDialog =
                            DialogParams(
                                fromLongClick = true,
                                title = person.name ?: "",
                                items = items,
                            )
                    },
                    onLongClickSimilar = { index, similar ->
                        val items =
                            buildMoreDialogItemsForHome(
                                context = context,
                                item = similar,
                                seriesId = null,
                                playbackPosition = similar.playbackPosition,
                                watched = similar.played,
                                favorite = similar.favorite,
                                actions = moreActions,
                            )
                        moreDialog =
                            DialogParams(
                                fromLongClick = true,
                                title = similar.title ?: "",
                                items = items,
                            )
                    },
                    trailerOnClick = {
                        TrailerService.onClick(context, it, viewModel::navigateTo)
                    },
                    onClickExtra = { index, extra ->
                        viewModel.navigateTo(extra.destination)
                    },
                    discovered = discovered,
                    onClickDiscover = { index, item ->
                        viewModel.navigateTo(item.destination)
                    },
                    modifier = modifier,
                )
            }
        }
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
    chosenStreams: ChosenStreams?,
    people: List<Person>,
    chapters: List<Chapter>,
    trailers: List<Trailer>,
    extras: List<ExtrasItem>,
    similar: List<BaseItem>,
    discovered: List<DiscoverItem>,
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
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
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
                        chosenStreams = chosenStreams,
                        bringIntoViewRequester = bringIntoViewRequester,
                        overviewOnClick = overviewOnClick,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp, bottom = 16.dp),
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
                        trailers = trailers,
                        trailerOnClick = {
                            position = TRAILER_ROW
                            trailerOnClick.invoke(it)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .focusRequester(focusRequesters[HEADER_ROW]),
                    )
                }
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
                            onLongClickPerson.invoke(index, person)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[PEOPLE_ROW]),
                    )
                }
            }
            if (chapters.isNotEmpty()) {
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
