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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.data.model.chooseSource
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.ChapterRow
import com.github.damontecres.wholphin.ui.cards.PersonRow
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButtons
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.chooseStream
import com.github.damontecres.wholphin.ui.components.chooseVersionParams
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.data.LocalTrailer
import com.github.damontecres.wholphin.ui.data.RemoteTrailer
import com.github.damontecres.wholphin.ui.data.Trailer
import com.github.damontecres.wholphin.ui.detail.LoadingItemViewModel
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItems
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.NavigationManager
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUID
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration

@HiltViewModel
class MovieViewModel
    @Inject
    constructor(
        api: ApiClient,
        val navigationManager: NavigationManager,
        val itemPlaybackRepository: ItemPlaybackRepository,
    ) : LoadingItemViewModel(api) {
        private lateinit var itemId: UUID
        val people = MutableLiveData<List<Person>>(listOf())
        val chapters = MutableLiveData<List<Chapter>>(listOf())
        val chosenStreams = MutableLiveData<ChosenStreams?>(null)
        val trailers = MutableLiveData<List<Trailer>>(listOf())

        override fun init(
            itemId: UUID,
            potential: BaseItem?,
        ): Job? {
            this.itemId = itemId
            return viewModelScope.launch(ExceptionHandler()) {
                super.init(itemId, potential)?.join()
                item.value?.let { item ->
                    viewModelScope.launchIO {
                        val result = itemPlaybackRepository.getSelectedTracks(item.id, item)
                        withContext(Dispatchers.Main) {
                            chosenStreams.value = result
                        }
                        val remoteTrailers =
                            item.data.remoteTrailers
                                ?.mapNotNull { t ->
                                    t.url?.let { url ->
                                        val name =
                                            t.name
                                                // TODO would be nice to clean up the trailer name
//                                                ?.replace(item.name ?: "", "")
//                                                ?.removePrefix(" - ")
                                                ?: "Trailer"
                                        RemoteTrailer(name, url)
                                    }
                                }.orEmpty()
                                .sortedBy { it.name }
                        val localTrailerCount = item.data.localTrailerCount ?: 0
                        val localTrailers =
                            if (localTrailerCount > 0) {
                                api.userLibraryApi.getLocalTrailers(itemId).content.map {
                                    LocalTrailer(BaseItem.from(it, api))
                                }
                            } else {
                                listOf()
                            }
                        withContext(Dispatchers.Main) {
                            this@MovieViewModel.trailers.value = localTrailers + remoteTrailers
                        }
                    }
                    withContext(Dispatchers.Main) {
                        people.value =
                            item.data.people
                                ?.letNotEmpty { people ->
                                    people.map { Person.fromDto(it, api) }
                                }.orEmpty()
                        chapters.value = Chapter.fromDto(item.data, api)
                    }
                }
            }
        }

        fun setWatched(played: Boolean) =
            viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                if (played) {
                    api.playStateApi.markPlayedItem(itemId)
                } else {
                    api.playStateApi.markUnplayedItem(itemId)
                }
                fetchAndSetItem(itemId)
            }

        fun savePlayVersion(
            item: BaseItem,
            sourceId: UUID,
        ) {
            viewModelScope.launchIO {
                val result = itemPlaybackRepository.savePlayVersion(item.id, sourceId)
                val chosen =
                    result?.let {
                        itemPlaybackRepository.getChosenItemFromPlayback(item, result)
                    }
                withContext(Dispatchers.Main) {
                    chosenStreams.value = chosen
                }
            }
        }

        fun saveTrackSelection(
            item: BaseItem,
            itemPlayback: ItemPlayback?,
            trackIndex: Int,
            type: MediaStreamType,
        ) {
            viewModelScope.launchIO {
                val result =
                    itemPlaybackRepository.saveTrackSelection(
                        item = item,
                        itemPlayback = itemPlayback,
                        trackIndex = trackIndex,
                        type = type,
                    )
                val chosen =
                    result?.let {
                        itemPlaybackRepository.getChosenItemFromPlayback(item, result)
                    }
                withContext(Dispatchers.Main) {
                    chosenStreams.value = chosen
                }
            }
        }
    }

@Composable
fun MovieDetails(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: MovieViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.init(destination.itemId, destination.item)
    }
    val item by viewModel.item.observeAsState()
    val people by viewModel.people.observeAsState(listOf())
    val chapters by viewModel.chapters.observeAsState(listOf())
    val trailers by viewModel.trailers.observeAsState(listOf())
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    val chosenStreams by viewModel.chosenStreams.observeAsState(null)

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var moreDialog by remember { mutableStateOf<DialogParams?>(null) }
    var chooseVersion by remember { mutableStateOf<DialogParams?>(null) }

    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state)
        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage()
        LoadingState.Success -> {
            item?.let { movie ->
                MovieDetailsContent(
                    preferences = preferences,
                    movie = movie,
                    chosenStreams = chosenStreams,
                    people = people,
                    chapters = chapters,
                    trailers = trailers,
                    playOnClick = {
                        viewModel.navigationManager.navigateTo(
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
                                title = movie.name ?: "Unknown",
                                overview = movie.data.overview,
                                files =
                                    movie.data.mediaSources
                                        ?.mapNotNull { it.path }
                                        .orEmpty(),
                            )
                    },
                    moreOnClick = {
                        moreDialog =
                            DialogParams(
                                fromLongClick = false,
                                title = movie.name + " (${movie.data.productionYear ?: ""})",
                                items =
                                    buildMoreDialogItems(
                                        item = movie,
                                        watched = movie.data.userData?.played ?: false,
                                        series = null,
                                        sourceId = chosenStreams?.sourceId,
                                        navigateTo = viewModel.navigationManager::navigateTo,
                                        onClickWatch = viewModel::setWatched,
                                        onChooseVersion = {
                                            chooseVersion =
                                                chooseVersionParams(movie.data.mediaSources!!) { idx ->
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
                        viewModel.setWatched((movie.data.userData?.played ?: false).not())
                    },
                    trailerOnClick = { trailer ->
                        when (trailer) {
                            is LocalTrailer ->
                                viewModel.navigationManager.navigateTo(
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
}

@Composable
fun MovieDetailsContent(
    preferences: UserPreferences,
    movie: BaseItem,
    chosenStreams: ChosenStreams?,
    people: List<Person>,
    chapters: List<Chapter>,
    trailers: List<Trailer>,
    playOnClick: (Duration) -> Unit,
    trailerOnClick: (Trailer) -> Unit,
    overviewOnClick: () -> Unit,
    watchOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val dto = movie.data
    val backdropImageUrl = movie.backdropImageUrl
    val resumePosition = dto.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
//        bringIntoViewRequester.bringIntoView()
        focusRequester.tryRequestFocus()
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
                        playOnClick = playOnClick,
                        moreOnClick = moreOnClick,
                        watchOnClick = watchOnClick,
                        buttonOnFocusChanged = {
                            if (it.isFocused) {
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                        modifier = Modifier.focusRequester(focusRequester),
                    )
                }
            }
            if (people.isNotEmpty()) {
                item {
                    PersonRow(
                        people = people,
                        onClick = {},
                        onLongClick = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (trailers.isNotEmpty()) {
                item {
                    TrailerRow(
                        trailers = trailers,
                        onClickTrailer = trailerOnClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (chapters.isNotEmpty()) {
                item {
                    ChapterRow(
                        chapters = chapters,
                        onClick = { playOnClick.invoke(it.position) },
                        onLongClick = {},
                        modifier = Modifier.fillMaxWidth(),
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
