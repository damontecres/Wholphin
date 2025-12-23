package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.LocalTrailer
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.data.model.RemoteTrailer
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.TrailerService
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.PersonRow
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.detail.MoreDialogActions
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItemsForPerson
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.serializer.toUUIDOrNull

@Composable
fun DiscoverMovieDetails(
    preferences: UserPreferences,
    destination: Destination.DiscoveredItem,
    modifier: Modifier = Modifier,
    viewModel: DiscoverMovieViewModel =
        hiltViewModel<DiscoverMovieViewModel, DiscoverMovieViewModel.Factory>(
            creationCallback = { it.create(destination.item) },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.init()
        onPauseOrDispose { }
    }
    val item by viewModel.movie.observeAsState()
    val rating by viewModel.rating.observeAsState(null)
    val people by viewModel.people.observeAsState(listOf())
    val trailers by viewModel.trailers.observeAsState(listOf())
    val similar by viewModel.similar.observeAsState(listOf())
    val recommended by viewModel.similar.observeAsState(listOf())
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var moreDialog by remember { mutableStateOf<DialogParams?>(null) }

    val moreActions =
        MoreDialogActions(
            navigateTo = viewModel::navigateTo,
            onClickWatch = { itemId, watched -> },
            onClickFavorite = { itemId, favorite -> },
            onClickAddPlaylist = { itemId -> },
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
                DiscoverMovieDetailsContent(
                    preferences = preferences,
                    movie = movie,
                    rating = rating,
                    people = people,
                    trailers = trailers,
                    similar = similar,
                    recommended = recommended,
                    requestOnClick = {
                        movie.id?.let { viewModel.request(it) }
                    },
                    cancelOnClick = {
                        movie.id?.let { viewModel.cancelRequest(it) }
                    },
                    onClickItem = { index, item ->
                        viewModel.navigateTo(Destination.DiscoveredItem(item))
                    },
                    onClickPerson = {
                    },
                    overviewOnClick = {
                        overviewDialog =
                            ItemDetailsDialogInfo(
                                title = movie.title ?: context.getString(R.string.unknown),
                                overview = movie.overview,
                                genres = movie.genres?.mapNotNull { it.name }.orEmpty(),
                                files = listOf(),
                            )
                    },
                    goToOnClick = {
                        movie.mediaInfo?.jellyfinMediaId?.toUUIDOrNull()?.let {
                            viewModel.navigateTo(
                                Destination.MediaItem(
                                    itemId = it,
                                    type = BaseItemKind.MOVIE,
                                ),
                            )
                        }
                    },
                    moreOnClick = {
                        moreDialog =
                            DialogParams(
                                fromLongClick = false,
                                title = movie.title + " (${movie.releaseDate ?: ""})",
                                items = listOf(),
                            )
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
                    },
                    trailerOnClick = {
                        TrailerService.onClick(context, it, viewModel::navigateTo)
                    },
                    modifier = modifier,
                )
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

private const val HEADER_ROW = 0
private const val PEOPLE_ROW = HEADER_ROW + 1
private const val TRAILER_ROW = PEOPLE_ROW + 1
private const val CHAPTER_ROW = TRAILER_ROW + 1
private const val EXTRAS_ROW = CHAPTER_ROW + 1
private const val SIMILAR_ROW = EXTRAS_ROW + 1
private const val RECOMMENDED_ROW = SIMILAR_ROW + 1

@Composable
fun DiscoverMovieDetailsContent(
    preferences: UserPreferences,
    movie: MovieDetails,
    rating: DiscoverRating?,
    people: List<Person>,
    trailers: List<Trailer>,
    similar: List<DiscoverItem>,
    recommended: List<DiscoverItem>,
    requestOnClick: () -> Unit,
    cancelOnClick: () -> Unit,
    trailerOnClick: (Trailer) -> Unit,
    overviewOnClick: () -> Unit,
    goToOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    onClickItem: (Int, DiscoverItem) -> Unit,
    onClickPerson: (Person) -> Unit,
    onLongClickPerson: (Int, Person) -> Unit,
    onLongClickSimilar: (Int, DiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var position by rememberInt(0)
    val focusRequesters = remember { List(RECOMMENDED_ROW + 1) { FocusRequester() } }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        focusRequesters.getOrNull(position)?.tryRequestFocus()
    }
    Box(modifier = modifier) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
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
                    DiscoverMovieDetailsHeader(
                        preferences = preferences,
                        movie = movie,
                        rating = rating,
                        bringIntoViewRequester = bringIntoViewRequester,
                        overviewOnClick = overviewOnClick,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp, bottom = 16.dp),
                    )
                    ExpandableDiscoverButtons(
                        availability =
                            SeerrAvailability.from(movie.mediaInfo?.status)
                                ?: SeerrAvailability.UNKNOWN,
                        requestOnClick = requestOnClick,
                        cancelOnClick = cancelOnClick,
                        moreOnClick = moreOnClick,
                        goToOnClick = goToOnClick,
                        buttonOnFocusChanged = {
                            if (it.isFocused) {
                                position = HEADER_ROW
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
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
                            DiscoverItemCard(
                                item = item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                showOverlay = false,
                                modifier = mod,
                            )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[SIMILAR_ROW]),
                    )
                }
            }
            if (recommended.isNotEmpty()) {
                item {
                    ItemRow(
                        title = stringResource(R.string.recommended),
                        items = similar,
                        onClickItem = { index, item ->
                            position = RECOMMENDED_ROW
                            onClickItem.invoke(index, item)
                        },
                        onLongClickItem = { index, similar ->
                            position = RECOMMENDED_ROW
                            onLongClickSimilar.invoke(index, similar)
                        },
                        cardContent = { index, item, mod, onClick, onLongClick ->
                            DiscoverItemCard(
                                item = item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                showOverlay = true,
                                modifier = mod,
                            )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[RECOMMENDED_ROW]),
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
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
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
                    is LocalTrailer -> {
                        SeasonCard(
                            item = item.baseItem,
                            onClick = { onClickTrailer.invoke(item) },
                            onLongClick = {},
                            imageHeight = Cards.height2x3,
                            imageWidth = Dp.Unspecified,
                            showImageOverlay = false,
                            modifier = cardModifier,
                        )
                    }

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
