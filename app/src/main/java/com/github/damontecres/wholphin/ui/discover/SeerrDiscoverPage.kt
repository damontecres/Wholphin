package com.github.damontecres.wholphin.ui.discover

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.infrastructure.ClientException
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.QuickDetailsData
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.components.Genre
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.listToDotString
import com.github.damontecres.wholphin.ui.main.HomePageHeader
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.EmptyStringProvider
import com.github.damontecres.wholphin.ui.util.ResProviderStringProvider
import com.github.damontecres.wholphin.ui.util.ResStringProvider
import com.github.damontecres.wholphin.ui.util.ScrollToTopBringIntoViewSpec
import com.github.damontecres.wholphin.ui.util.StringProvider
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.DiscoverPagerType
import com.github.damontecres.wholphin.util.DiscoverRequestType
import com.github.damontecres.wholphin.util.successValue
import com.google.common.cache.CacheBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SeerrDiscoverViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val seerrService: SeerrService,
        val navigationManager: NavigationManager,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        val state = MutableStateFlow<DiscoverState>(DiscoverState())
        val rating = MutableStateFlow<Map<Int, DiscoverRating>>(mapOf())

        init {
            viewModelScope.launchIO {
                backdropService.clearBackdrop()
            }
            fetchAndUpdateState(seerrService::discoverMovies) {
                this.copy(
                    movies =
                        DiscoverRowData(
                            ResStringProvider(R.string.movies_title),
                            it,
                            DiscoverRequestType.DISCOVER_MOVIES,
                        ),
                )
            }
            fetchAndUpdateState(seerrService::discoverTv) {
                this.copy(
                    tv =
                        DiscoverRowData(
                            ResStringProvider(R.string.tv_shows_title),
                            it,
                            DiscoverRequestType.DISCOVER_TV,
                        ),
                )
            }
            fetchAndUpdateState(seerrService::trending) {
                this.copy(
                    trending =
                        DiscoverRowData(
                            ResStringProvider(R.string.trending),
                            it,
                            DiscoverRequestType.TRENDING,
                        ),
                )
            }
            fetchAndUpdateState(seerrService::upcomingMovies) {
                this.copy(
                    upcomingMovies =
                        DiscoverRowData(
                            ResStringProvider(R.string.upcoming_movies),
                            it,
                            DiscoverRequestType.UPCOMING_MOVIES,
                        ),
                )
            }
            fetchAndUpdateState(seerrService::upcomingTv) {
                this.copy(
                    upcomingTv =
                        DiscoverRowData(
                            ResStringProvider(R.string.upcoming_tv),
                            it,
                            DiscoverRequestType.UPCOMING_TV,
                        ),
                )
            }
            viewModelScope.launchIO {
                val movieGenres =
                    try {
                        val result = seerrService.api.searchApi.discoverGenresliderMovieGet()
                        val genres =
                            result
                                .filter { it.id != null && it.name != null }
                                .map {
                                    val id = it.id!!.toUUID()
                                    val imageUrl =
                                        it.backdrops?.randomOrNull()?.let { path ->
                                            seerrService.createImageUrl(
                                                imageType = ImageType.BACKDROP,
                                                path = path,
                                                mediaInfo = null,
                                                // Don't need high resolution
                                                backdropWidth = 1280,
                                                backdropHeight = 720,
                                            )
                                        }
                                    Genre(
                                        id = id,
                                        name = it.name!!,
                                        imageUrl = imageUrl,
                                    )
                                }
                        Timber.v("Got %s movie genres", genres.size)
                        DataLoadingState.Success(genres)
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error getting movie genres")
                        DataLoadingState.Error(ex)
                    }
                state.update {
                    it.copy(
                        movieGenres = movieGenres,
                    )
                }
            }

            viewModelScope.launchIO {
                val tvGenres =
                    try {
                        val result = seerrService.api.searchApi.discoverGenresliderTvGet()
                        val genres =
                            result
                                .filter { it.id != null && it.name != null }
                                .map {
                                    val id = it.id!!.toUUID()
                                    val imageUrl =
                                        it.backdrops?.randomOrNull()?.let { path ->
                                            seerrService.createImageUrl(
                                                imageType = ImageType.BACKDROP,
                                                path = path,
                                                mediaInfo = null,
                                                // Don't need high resolution
                                                backdropWidth = 1280,
                                                backdropHeight = 720,
                                            )
                                        }
                                    Genre(
                                        id = id,
                                        name = it.name!!,
                                        imageUrl = imageUrl,
                                    )
                                }
                        Timber.v("Got %s tv genres", genres.size)
                        DataLoadingState.Success(genres)
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error getting tv genres")
                        DataLoadingState.Error(ex)
                    }
                state.update {
                    it.copy(
                        tvGenres = tvGenres,
                    )
                }
            }
        }

        private fun fetchAndUpdateState(
            getData: suspend () -> List<DiscoverItem>,
            copyFunc: DiscoverState.(DataLoadingState<List<DiscoverItem>>) -> DiscoverState,
        ) {
            viewModelScope.launchIO {
                state.update {
                    copyFunc.invoke(it, DataLoadingState.Loading)
                }
                try {
                    val results = getData.invoke()
                    state.update {
                        copyFunc.invoke(it, DataLoadingState.Success(results))
                    }
                } catch (ex: Exception) {
                    state.update {
                        copyFunc.invoke(it, DataLoadingState.Error(ex))
                    }
                }
            }
        }

        fun updateBackdrop(item: DiscoverFocusedItem?) {
            viewModelScope.launchIO {
                if (item != null) {
                    backdropService.submit("discover_${item.id}", item.backDropUrl)
                    if (item is DiscoverFocusedItem.Item) {
                        fetchRating(item.id, item.item.type)
                    }
                } else {
                    backdropService.clearBackdrop()
                }
            }
        }

        private val ratingCache =
            CacheBuilder
                .newBuilder()
                .maximumSize(100)
                .build<Int, DiscoverRating>()

        // TODO this is not very efficient
        fun fetchRating(
            id: Int,
            type: SeerrItemType,
        ) {
            viewModelScope.launchIO {
                val cachedResult = ratingCache.getIfPresent(id)
                if (cachedResult != null) {
                    return@launchIO
                }
                val result =
                    try {
                        when (type) {
                            SeerrItemType.MOVIE -> {
                                DiscoverRating(
                                    seerrService.api.moviesApi.movieMovieIdRatingsGet(
                                        movieId = id,
                                    ),
                                )
                            }

                            SeerrItemType.TV -> {
                                DiscoverRating(seerrService.api.tvApi.tvTvIdRatingsGet(tvId = id))
                            }

                            SeerrItemType.PERSON -> {
                                DiscoverRating(null, null)
                            }

                            SeerrItemType.UNKNOWN -> {
                                DiscoverRating(null, null)
                            }
                        }
                    } catch (ex: ClientException) {
                        if (ex.statusCode == 404) {
                            Timber.w("No rating found for %s", id)
                            DiscoverRating(null, null)
                        } else {
                            Timber.e(ex, "Error getting rating for %s", id)
                            return@launchIO
                        }
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error getting rating for %s", id)
                        return@launchIO
                    }
                ratingCache.put(id, result)
                rating.update {
                    ratingCache.asMap().toMap()
                }
            }
        }
    }

fun Int.toUUID() = UUID(0L, toLong())

fun UUID.toInt() = leastSignificantBits.toInt()

data class DiscoverRowData(
    val title: StringProvider,
    val items: DataLoadingState<List<DiscoverItem>>,
    val type: DiscoverRequestType,
) {
    companion object {
        val EMPTY =
            DiscoverRowData(
                EmptyStringProvider,
                DataLoadingState.Pending,
                DiscoverRequestType.UNKNOWN,
            )
    }
}

data class DiscoverState(
    val movies: DiscoverRowData = DiscoverRowData.EMPTY,
    val tv: DiscoverRowData = DiscoverRowData.EMPTY,
    val trending: DiscoverRowData = DiscoverRowData.EMPTY,
    val upcomingMovies: DiscoverRowData = DiscoverRowData.EMPTY,
    val upcomingTv: DiscoverRowData = DiscoverRowData.EMPTY,
    val movieGenres: DataLoadingState<List<Genre>> = DataLoadingState.Pending,
    val tvGenres: DataLoadingState<List<Genre>> = DataLoadingState.Pending,
)

private const val ROW_TRENDING = 0
private const val ROW_MOVIES = ROW_TRENDING + 1
private const val ROW_GENRES_MOVIE = ROW_MOVIES + 1
private const val ROW_UPCOMING_MOVIES = ROW_GENRES_MOVIE + 1
private const val ROW_TV = ROW_UPCOMING_MOVIES + 1
private const val ROW_GENRES_TV = ROW_TV + 1
private const val ROW_UPCOMING_TV = ROW_GENRES_TV + 1

private const val LAST_ROW = ROW_UPCOMING_TV

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeerrDiscoverPage(
    preferences: UserPreferences,
    positionCallback: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeerrDiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val ratingMap by viewModel.rating.collectAsState()

    val focusRequesters = remember { List(LAST_ROW + 1) { FocusRequester() } }
    var position by rememberPosition(0, -1)
    val focusedItem: DiscoverFocusedItem? =
        remember(position) {
            position.let {
                val discoverRow =
                    when (position.row) {
                        ROW_TRENDING -> state.trending
                        ROW_MOVIES -> state.movies
                        ROW_UPCOMING_MOVIES -> state.upcomingMovies
                        ROW_TV -> state.tv
                        ROW_UPCOMING_TV -> state.upcomingTv
                        else -> null
                    }
                if (discoverRow != null) {
                    (discoverRow.items as? DataLoadingState.Success)
                        ?.data
                        ?.getOrNull(it.column)
                        ?.let {
                            DiscoverFocusedItem.Item(it)
                        }
                } else {
                    when (position.row) {
                        ROW_GENRES_MOVIE -> {
                            state.movieGenres.successValue
                                ?.getOrNull(position.column)
                                ?.let {
                                    DiscoverFocusedItem.Genre(
                                        id = it.id.toInt(),
                                        name = it.name,
                                        type = SeerrItemType.MOVIE,
                                        backDropUrl = it.imageUrl,
                                    )
                                }
                        }

                        ROW_GENRES_TV -> {
                            state.movieGenres.successValue
                                ?.getOrNull(position.column)
                                ?.let {
                                    DiscoverFocusedItem.Genre(
                                        id = it.id.toInt(),
                                        name = it.name,
                                        type = SeerrItemType.TV,
                                        backDropUrl = it.imageUrl,
                                    )
                                }
                        }

                        else -> {
                            null
                        }
                    }
                }
            }
        }
    LaunchedEffect(focusedItem) {
        viewModel.updateBackdrop(focusedItem)
    }
    var firstFocused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.trending) {
        if (!firstFocused && state.trending.items is DataLoadingState.Success<*>) {
            firstFocused = focusRequesters.getOrNull(0)?.tryRequestFocus("discover") == true
        } else if (firstFocused) {
            focusRequesters.getOrNull(position.row)?.tryRequestFocus()
        }
    }

    Column(
        modifier = modifier,
    ) {
        val details =
            remember(focusedItem, ratingMap) {
                (focusedItem as? DiscoverFocusedItem.Item)?.let { focusedItem ->
                    buildList {
                        focusedItem.item
                            .releaseDate
                            ?.year
                            ?.toString()
                            ?.let(::add)
                    }.let {
                        val rating = focusedItem.id.let { ratingMap[it] }
                        val str =
                            listToDotString(
                                it,
                                rating?.audienceRating,
                                rating?.criticRating?.toFloat(),
                            )
                        QuickDetailsData(str)
                    }
                }
            }
        HomePageHeader(
            title = focusedItem?.title?.getString(),
            subtitle = focusedItem?.subtitle,
            overview = focusedItem?.overview,
            overviewTwoLines = true,
            quickDetails = details,
            timeRemaining = null,
            endsAt = null,
            showLogo = preferences.appPreferences.interfacePreferences.showLogos,
            logoImageUrl = null, // TODO
            modifier =
                Modifier
                    .padding(top = 24.dp, bottom = 16.dp, start = 32.dp)
                    .fillMaxHeight(.25f),
        )

        val density = LocalDensity.current
        val spaceAbovePx =
            with(density) {
                // The size of the row titles & spacing
                50.dp.toPx()
            }
        val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current

        @Composable
        fun discoverItemRow(
            rowIndex: Int,
            row: DiscoverRowData,
        ) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoViewSpec) {
                DiscoverRow(
                    row = row,
                    onClickItem = { index, item ->
                        position = RowColumn(rowIndex, index)
                        viewModel.navigationManager.navigateTo(item.destination)
                    },
                    onLongClickItem = { index, item -> },
                    onCardFocus = { index ->
                        position = RowColumn(rowIndex, index)
                        positionCallback.invoke(position)
                    },
                    focusRequester = focusRequesters[rowIndex],
                    enableViewMore = row.type != DiscoverRequestType.UNKNOWN,
                    onClickViewMore = {
                        (row.items as? DataLoadingState.Success<List<DiscoverItem>>)?.data?.size?.let {
                            position = RowColumn(rowIndex, it)
                            positionCallback.invoke(position)
                        }
                        viewModel.navigationManager.navigateTo(
                            Destination.DiscoverMoreResult(DiscoverPagerType.RequestType(row.type)),
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
            }
        }
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides ScrollToTopBringIntoViewSpec(spaceAbovePx),
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
                modifier =
                    Modifier
                        .focusRestorer()
                        .fillMaxSize(),
            ) {
                item { discoverItemRow(ROW_TRENDING, state.trending) }

                // Movies
                item { discoverItemRow(ROW_MOVIES, state.movies) }
                item {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoViewSpec) {
                        DiscoverGenreRow(
                            title =
                                remember {
                                    ResProviderStringProvider(
                                        R.string.genres_in,
                                        ResStringProvider(R.string.movies_title),
                                    )
                                },
                            items = state.movieGenres,
                            onClickItem = { index, genre ->
                                position = RowColumn(ROW_GENRES_MOVIE, index)
                                viewModel.navigationManager.navigateTo(
                                    Destination.DiscoverMoreResult(
                                        type =
                                            DiscoverPagerType.Genre(
                                                genreId = genre.id.toInt(),
                                                name = genre.name,
                                                type = SeerrItemType.MOVIE,
                                            ),
                                        startIndex = 0,
                                    ),
                                )
                            },
                            onCardFocus = { index ->
                                position = RowColumn(ROW_GENRES_MOVIE, index)
                                positionCallback.invoke(position)
                            },
                            modifier = Modifier.focusRequester(focusRequesters[ROW_GENRES_MOVIE]),
                        )
                    }
                }
                item { discoverItemRow(ROW_UPCOMING_MOVIES, state.upcomingMovies) }

                // TV
                item { discoverItemRow(ROW_TV, state.tv) }
                item {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoViewSpec) {
                        DiscoverGenreRow(
                            title =
                                remember {
                                    ResProviderStringProvider(
                                        R.string.genres_in,
                                        ResStringProvider(R.string.tv_shows_title),
                                    )
                                },
                            items = state.tvGenres,
                            onClickItem = { index, genre ->
                                position = RowColumn(ROW_GENRES_TV, index)
                                viewModel.navigationManager.navigateTo(
                                    Destination.DiscoverMoreResult(
                                        type =
                                            DiscoverPagerType.Genre(
                                                genreId = genre.id.toInt(),
                                                name = genre.name,
                                                type = SeerrItemType.TV,
                                            ),
                                        startIndex = 0,
                                    ),
                                )
                            },
                            onCardFocus = { index ->
                                position = RowColumn(ROW_GENRES_TV, index)
                                positionCallback.invoke(position)
                            },
                            modifier = Modifier.focusRequester(focusRequesters[ROW_GENRES_TV]),
                        )
                    }
                }
                item { discoverItemRow(ROW_UPCOMING_TV, state.upcomingTv) }
            }
        }
    }
}
