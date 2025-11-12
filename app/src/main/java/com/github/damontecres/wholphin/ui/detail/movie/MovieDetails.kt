package com.github.damontecres.wholphin.ui.detail.movie

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.LocalTrailer
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.data.model.RemoteTrailer
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.data.model.chooseSource
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.ChapterRow
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
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
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
    val item by viewModel.item.observeAsState()
    val people by viewModel.people.observeAsState(listOf())
    val chapters by viewModel.chapters.observeAsState(listOf())
    val trailers by viewModel.trailers.observeAsState(listOf())
    val similar by viewModel.similar.observeAsState(listOf())
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    val chosenStreams by viewModel.chosenStreams.observeAsState(null)

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
        is LoadingState.Error -> ErrorMessage(state)
        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage()
        LoadingState.Success -> {
            item?.let { movie ->
                LifecycleStartEffect(destination.itemId) {
                    viewModel.maybePlayThemeSong(
                        destination.itemId,
                        preferences.appPreferences.interfacePreferences.playThemeSongs,
                    )
                    onStopOrDispose {
                        viewModel.release()
                    }
                }
                MovieDetailsContent(
                    preferences = preferences,
                    movie = movie,
                    chosenStreams = chosenStreams,
                    people = people,
                    chapters = chapters,
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
                                movie,
                            ),
                        )
                    },
                    overviewOnClick = {
                        overviewDialog =
                            ItemDetailsDialogInfo(
                                title = movie.name ?: context.getString(R.string.unknown),
                                overview = movie.data.overview,
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
                                        series = null,
                                        sourceId = chosenStreams?.sourceId,
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
                                            chooseSource(
                                                movie.data,
                                                chosenStreams?.itemPlayback,
                                            )?.let { source ->
                                                chooseVersion =
                                                    chooseStream(
                                                        context = context,
                                                        streams = source.mediaStreams.orEmpty(),
                                                        type = type,
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
                    trailerOnClick = { trailer ->
                        when (trailer) {
                            is LocalTrailer ->
                                viewModel.navigateTo(
                                    Destination.Playback(
                                        itemId = trailer.baseItem.id,
                                        item = trailer.baseItem,
                                        positionMs = 0L,
                                    ),
                                )

                            is RemoteTrailer -> {
                                val intent = Intent(Intent.ACTION_VIEW, trailer.url.toUri())
                                context.startActivity(intent)
                            }
                        }
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
private const val SIMILAR_ROW = CHAPTER_ROW + 1

@Composable
fun MovieDetailsContent(
    preferences: UserPreferences,
    movie: BaseItem,
    chosenStreams: ChosenStreams?,
    people: List<Person>,
    chapters: List<Chapter>,
    trailers: List<Trailer>,
    similar: List<BaseItem>,
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
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var position by rememberInt(0)
    val focusRequesters = remember { List(SIMILAR_ROW + 1) { FocusRequester() } }
    val dto = movie.data
    val backdropImageUrl = movie.backdropImageUrl
    val resumePosition = dto.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        focusRequesters.getOrNull(position)?.tryRequestFocus()
    }
    Box(modifier = modifier) {
        if (backdropImageUrl.isNotNullOrBlank()) {
            val gradientColor = MaterialTheme.colorScheme.background
            AsyncImage(
                model = backdropImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd,
                modifier =
                    Modifier
                        .fillMaxHeight(.75f)
                        .alpha(.5f)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, gradientColor),
                                    startY = size.height * .5f,
                                ),
                            )
                            drawRect(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, gradientColor),
                                    endX = 0f,
                                    startX = size.width * .75f,
                                ),
                            )
                        },
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .bringIntoViewRequester(bringIntoViewRequester)
                            .padding(bottom = 56.dp),
                ) {
                    MovieDetailsHeader(
                        preferences = preferences,
                        movie = movie,
                        chosenStreams = chosenStreams,
                        bringIntoViewRequester = bringIntoViewRequester,
                        overviewOnClick = overviewOnClick,
                        Modifier
                            .fillMaxWidth(.75f)
                            .padding(bottom = 8.dp),
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
                        modifier = Modifier.focusRequester(focusRequesters[HEADER_ROW]),
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
            if (trailers.isNotEmpty()) {
                item {
                    TrailerRow(
                        trailers = trailers,
                        onClickTrailer = {
                            position = TRAILER_ROW
                            trailerOnClick.invoke(it)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[TRAILER_ROW]),
                    )
                }
            }
            if (chapters.isNotEmpty()) {
                item {
                    ChapterRow(
                        chapters = chapters,
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
            if (similar.isNotEmpty()) {
                item {
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
        }
    }
}

@Composable
fun TrailerRow(
    trailers: List<Trailer>,
    onClickTrailer: (Trailer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.trailers),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRestorer(firstFocus),
        ) {
            itemsIndexed(trailers) { index, item ->
                val cardModifier =
                    if (index == 0) {
                        Modifier.focusRequester(firstFocus)
                    } else {
                        Modifier
                    }
                when (item) {
                    is LocalTrailer ->
                        SeasonCard(
                            item = item.baseItem,
                            onClick = { onClickTrailer.invoke(item) },
                            onLongClick = {},
                            imageHeight = Cards.height2x3,
                            imageWidth = Dp.Unspecified,
                            showImageOverlay = false,
                            modifier = cardModifier,
                        )

                    is RemoteTrailer -> {
                        val subtitle =
                            when (item.url.toUri().host) {
                                "youtube.com", "www.youtube.com" -> "YouTube"
                                else -> null
                            }
                        SeasonCard(
                            title = item.name,
                            subtitle = subtitle,
                            name = item.name,
                            imageUrl = null,
                            isFavorite = false,
                            isPlayed = false,
                            unplayedItemCount = 0,
                            playedPercentage = 0.0,
                            onClick = { onClickTrailer.invoke(item) },
                            onLongClick = {},
                            modifier = cardModifier,
                            showImageOverlay = false,
                            imageHeight = Cards.height2x3,
                            imageWidth = Dp.Unspecified,
                        )
                    }
                }
            }
        }
    }
}
