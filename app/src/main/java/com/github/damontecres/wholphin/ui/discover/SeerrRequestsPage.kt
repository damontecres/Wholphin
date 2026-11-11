package com.github.damontecres.wholphin.ui.discover

import android.content.Context
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.api.seerr.model.Season
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.overallProgressPercent
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.extractDownloads
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.formatDate
import com.github.damontecres.wholphin.ui.formatSeasonEpisode
import com.github.damontecres.wholphin.ui.formatSeasonNumber
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.toLocalDate
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

data class ScheduledRelease(
    val date: LocalDate?,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val label: String? = null,
    val overview: String? = null,
) : Comparable<ScheduledRelease> {
    override fun compareTo(other: ScheduledRelease): Int {
        val d1 = date
        val d2 = other.date
        if (d1 != null && d2 != null) {
            val cmp = d1.compareTo(d2)
            if (cmp != 0) return cmp
        } else if (d1 != null) {
            return -1
        } else if (d2 != null) {
            return 1
        }
        val sCmp = (seasonNumber ?: 0).compareTo(other.seasonNumber ?: 0)
        if (sCmp != 0) return sCmp
        return (episodeNumber ?: 0).compareTo(other.episodeNumber ?: 0)
    }
}

private data class FetchedTargetData(
    val item: DiscoverItem,
    val movieDetails: MovieDetails? = null,
    val tvDetails: TvDetails? = null,
    val seasonDetails: Map<Int, Season> = emptyMap(),
)

private data class ReleaseScheduleEvaluation(
    val scheduledReleases: List<ScheduledRelease>,
    val hasReleasedMissingContent: Boolean,
)

private fun evaluateMovieReleaseSchedule(
    today: LocalDate,
    movie: MovieDetails,
): ReleaseScheduleEvaluation {
    val theatricalDate = toLocalDate(movie.releaseDate?.take(10))
    val allReleaseDates =
        movie.releases
            ?.results
            .orEmpty()
            .flatMap { it.releaseDates.orEmpty() }
    val futureDigitalDate =
        allReleaseDates
            .filter { it.type == 4 || it.type == 5 }
            .mapNotNull { toLocalDate(it.releaseDate?.take(10)) }
            .filter { it.isAfter(today) }
            .minOrNull()
    val earliestTheatrical =
        theatricalDate
            ?: allReleaseDates
                .filter { it.type == 3 }
                .mapNotNull { toLocalDate(it.releaseDate?.take(10)) }
                .minOrNull()

    return when {
        earliestTheatrical != null && earliestTheatrical.isAfter(today) -> {
            ReleaseScheduleEvaluation(
                scheduledReleases =
                    listOf(
                        ScheduledRelease(
                            date = earliestTheatrical,
                            label = movie.status?.takeIf { it.isNotNullOrBlank() },
                            overview = movie.overview,
                        ),
                    ),
                hasReleasedMissingContent = false,
            )
        }

        earliestTheatrical != null && !earliestTheatrical.isAfter(today) && futureDigitalDate != null -> {
            ReleaseScheduleEvaluation(
                scheduledReleases =
                    listOf(
                        ScheduledRelease(
                            date = futureDigitalDate,
                            label = "Digital",
                            overview = movie.overview,
                        ),
                    ),
                hasReleasedMissingContent = false,
            )
        }

        earliestTheatrical == null &&
            (futureDigitalDate != null || (movie.status != null && !movie.status.equals("Released", ignoreCase = true))) -> {
            ReleaseScheduleEvaluation(
                scheduledReleases =
                    listOf(
                        ScheduledRelease(
                            date = futureDigitalDate,
                            label = movie.status?.takeIf { it.isNotNullOrBlank() },
                            overview = movie.overview,
                        ),
                    ),
                hasReleasedMissingContent = false,
            )
        }

        else -> {
            ReleaseScheduleEvaluation(
                scheduledReleases = emptyList(),
                hasReleasedMissingContent = true,
            )
        }
    }
}

private fun evaluateTvReleaseSchedule(
    today: LocalDate,
    tv: TvDetails,
    request: MediaRequest,
    seasonDetails: Map<Int, Season>,
): ReleaseScheduleEvaluation {
    val scheduled = mutableListOf<ScheduledRelease>()
    var hasAiredMissing = false

    val unavailableRequestedSeasons =
        request.seasons
            ?.filter { s ->
                val sAvail = SeerrAvailability.from(s.status) ?: SeerrAvailability.UNKNOWN
                val sAvail4k = SeerrAvailability.from(s.status4k) ?: SeerrAvailability.UNKNOWN
                val eff = if (request.is4k == true) sAvail4k else sAvail
                eff != SeerrAvailability.AVAILABLE
            }?.mapNotNull { it.seasonNumber }
            .orEmpty()

    val targetSeasons =
        unavailableRequestedSeasons.ifEmpty {
            tv.seasons
                ?.mapNotNull { it.seasonNumber }
                ?.filter { it > 0 }
                .orEmpty()
        }

    targetSeasons.forEach { seasonNum ->
        val detailedSeason = seasonDetails[seasonNum]
        val summarySeason = tv.seasons?.firstOrNull { it.seasonNumber == seasonNum }
        val episodes = detailedSeason?.episodes ?: summarySeason?.episodes
        if (!episodes.isNullOrEmpty()) {
            var seasonHasFutureEp = false
            var seasonHasPastEp = false
            episodes.forEach { ep ->
                val epDate = toLocalDate(ep.airDate?.take(10))
                if (epDate != null) {
                    if (!epDate.isBefore(today)) {
                        seasonHasFutureEp = true
                        scheduled.add(
                            ScheduledRelease(
                                date = epDate,
                                seasonNumber = ep.seasonNumber ?: seasonNum,
                                episodeNumber = ep.episodeNumber,
                                episodeTitle = ep.name,
                                overview = ep.overview?.takeIf { it.isNotNullOrBlank() } ?: tv.overview,
                            ),
                        )
                    } else {
                        seasonHasPastEp = true
                        hasAiredMissing = true
                    }
                }
            }
            if (!seasonHasFutureEp && !seasonHasPastEp) {
                val seasonAirDate = toLocalDate((detailedSeason?.airDate ?: summarySeason?.airDate)?.take(10))
                if (seasonAirDate == null || !seasonAirDate.isBefore(today)) {
                    scheduled.add(
                        ScheduledRelease(
                            date = seasonAirDate,
                            seasonNumber = seasonNum,
                            label = formatSeasonNumber(seasonNum),
                            overview = detailedSeason?.overview?.takeIf { it.isNotNullOrBlank() } ?: tv.overview,
                        ),
                    )
                } else {
                    hasAiredMissing = true
                }
            }
        } else {
            val seasonAirDate = toLocalDate((detailedSeason?.airDate ?: summarySeason?.airDate)?.take(10))
            if (seasonAirDate != null && !seasonAirDate.isBefore(today)) {
                scheduled.add(
                    ScheduledRelease(
                        date = seasonAirDate,
                        seasonNumber = seasonNum,
                        label = formatSeasonNumber(seasonNum),
                        overview = summarySeason?.overview?.takeIf { it.isNotNullOrBlank() } ?: tv.overview,
                    ),
                )
            } else if (seasonAirDate == null && tv.inProduction == true) {
                scheduled.add(
                    ScheduledRelease(
                        date = null,
                        seasonNumber = seasonNum,
                        label = formatSeasonNumber(seasonNum),
                        overview = tv.overview,
                    ),
                )
            } else {
                hasAiredMissing = true
            }
        }
    }

    if (scheduled.isEmpty()) {
        val nextEp = tv.nextEpisodeToAir
        val nextEpDate = toLocalDate(nextEp?.airDate?.take(10))
        val firstAirDate = toLocalDate(tv.firstAirDate?.take(10))
        if (nextEp != null && nextEpDate != null && !nextEpDate.isBefore(today)) {
            scheduled.add(
                ScheduledRelease(
                    date = nextEpDate,
                    seasonNumber = nextEp.seasonNumber,
                    episodeNumber = nextEp.episodeNumber,
                    episodeTitle = nextEp.name,
                    overview = nextEp.overview?.takeIf { it.isNotNullOrBlank() } ?: tv.overview,
                ),
            )
        } else if (firstAirDate != null && firstAirDate.isAfter(today)) {
            scheduled.add(
                ScheduledRelease(
                    date = firstAirDate,
                    label = tv.type,
                    overview = tv.overview,
                ),
            )
            hasAiredMissing = false
        } else if (firstAirDate == null && tv.inProduction == true && !hasAiredMissing) {
            scheduled.add(
                ScheduledRelease(
                    date = null,
                    label = tv.type,
                    overview = tv.overview,
                ),
            )
        }
    }

    return ReleaseScheduleEvaluation(
        scheduledReleases = scheduled.distinct().sorted(),
        hasReleasedMissingContent = hasAiredMissing || scheduled.isEmpty(),
    )
}

suspend fun fetchRequestGridItems(
    scope: CoroutineScope,
    seerrService: SeerrService,
    take: Int = 50,
): List<RequestGridItem> {
    val semaphore = Semaphore(3)
    val today = LocalDate.now()
    val mediaRequests =
        seerrService.api.requestApi
            .requestGet(take = take)
            .results
            .orEmpty()
    val targetCache = mutableMapOf<Pair<SeerrItemType, Int>, FetchedTargetData?>()
    val distinctTargets =
        mediaRequests
            .mapNotNull { req ->
                val tmdbId = req.media?.tmdbId ?: return@mapNotNull null
                val type = SeerrItemType.fromString(req.type)
                if (type == SeerrItemType.MOVIE || type == SeerrItemType.TV) {
                    type to tmdbId
                } else {
                    null
                }
            }.distinct()

    val fetchedTargets =
        distinctTargets
            .map { (type, tmdbId) ->
                scope.async(WholphinDispatchers.IO) {
                    semaphore.withPermit {
                        val data =
                            when (type) {
                                SeerrItemType.MOVIE -> {
                                    val movie = seerrService.api.moviesApi.movieMovieIdGet(movieId = tmdbId)
                                    val item = seerrService.createDiscoverItem(movie)
                                    FetchedTargetData(item = item, movieDetails = movie)
                                }

                                SeerrItemType.TV -> {
                                    val tv = seerrService.api.tvApi.tvTvIdGet(tvId = tmdbId)
                                    val item = seerrService.createDiscoverItem(tv)
                                    val unavailableRequestedSeasons =
                                        mediaRequests
                                            .filter { it.media?.tmdbId == tmdbId }
                                            .flatMap { req ->
                                                req.seasons
                                                    ?.filter { s ->
                                                        val sAvail = SeerrAvailability.from(s.status) ?: SeerrAvailability.UNKNOWN
                                                        val sAvail4k = SeerrAvailability.from(s.status4k) ?: SeerrAvailability.UNKNOWN
                                                        val eff = if (req.is4k == true) sAvail4k else sAvail
                                                        eff != SeerrAvailability.AVAILABLE
                                                    }?.mapNotNull { it.seasonNumber }
                                                    .orEmpty()
                                            }.distinct()
                                            .sorted()
                                            .take(4)
                                    val seasonMap = mutableMapOf<Int, Season>()
                                    unavailableRequestedSeasons.forEach { seasonNum ->
                                        try {
                                            val seasonDetail =
                                                seerrService.api.tvApi.tvTvIdSeasonSeasonNumberGet(
                                                    tvId = tmdbId,
                                                    seasonNumber = seasonNum,
                                                )
                                            seasonMap[seasonNum] = seasonDetail
                                        } catch (ex: Exception) {
                                            Timber.w(ex, "Could not fetch season %s for tv %s", seasonNum, tmdbId)
                                        }
                                    }
                                    FetchedTargetData(
                                        item = item,
                                        tvDetails = tv,
                                        seasonDetails = seasonMap,
                                    )
                                }

                                else -> {
                                    null
                                }
                            }
                        (type to tmdbId) to data
                    }
                }
            }.awaitAll()
    fetchedTargets.forEach { (key, value) -> targetCache[key] = value }

    return mediaRequests.mapNotNull { request ->
        val tmdbId = request.media?.tmdbId
        if (tmdbId != null) {
            val type = SeerrItemType.fromString(request.type)
            val targetData = targetCache[type to tmdbId] ?: return@mapNotNull null
            val baseItem = targetData.item
            val fallbackDownloads = request.media.extractDownloads()
            val downloads = baseItem.downloads.ifEmpty { fallbackDownloads }
            val reqStatus = RequestStatus.from(request.status)
            val resolvedAvailability =
                if (baseItem.availability == SeerrAvailability.UNKNOWN) {
                    when (reqStatus) {
                        RequestStatus.PENDING -> SeerrAvailability.PENDING
                        RequestStatus.APPROVED -> SeerrAvailability.PROCESSING
                        RequestStatus.COMPLETED -> SeerrAvailability.AVAILABLE
                        else -> baseItem.availability
                    }
                } else {
                    baseItem.availability
                }

            val scheduleEval =
                when {
                    targetData.movieDetails != null ->
                        evaluateMovieReleaseSchedule(today, targetData.movieDetails)
                    targetData.tvDetails != null ->
                        evaluateTvReleaseSchedule(today, targetData.tvDetails, request, targetData.seasonDetails)
                    else ->
                        ReleaseScheduleEvaluation(emptyList(), true)
                }

            val nextScheduledDate = scheduleEval.scheduledReleases.firstNotNullOfOrNull { it.date }
            val effectiveReleaseDate =
                if (nextScheduledDate != null && (baseItem.releaseDate == null || !baseItem.releaseDate.isAfter(today))) {
                    nextScheduledDate
                } else {
                    baseItem.releaseDate
                }

            val enrichedItem =
                baseItem.copy(
                    availability = resolvedAvailability,
                    releaseDate = effectiveReleaseDate,
                    downloads = downloads,
                    downloadProgress = downloads.overallProgressPercent(),
                )
            RequestGridItem(
                request = request,
                item = enrichedItem,
                scheduledReleases = scheduleEval.scheduledReleases,
                hasReleasedMissingContent = scheduleEval.hasReleasedMissingContent,
            )
        } else {
            Timber.v("No TMDB ID for request %s", request.id)
            null
        }
    }
}

@HiltViewModel
class SeerrRequestsViewModel
    @Inject
    constructor(
        private val seerrServerRepository: SeerrServerRepository,
        private val seerrService: SeerrService,
        val navigationManager: NavigationManager,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        val state = MutableStateFlow(SeerrRequestsState.EMPTY)

        init {
            viewModelScope.launchIO {
                backdropService.clearBackdrop()
            }
            seerrServerRepository.connection
                .onEach { user ->
                    state.update { it.copy(requests = DataLoadingState.Loading) }
                    if (user != null) {
                        val results = fetchRequestGridItems(viewModelScope, seerrService)
                        state.update { it.copy(requests = DataLoadingState.Success(results)) }
                    }
                }.catch { ex ->
                    Timber.e(ex, "Error fetching requests")
                    state.update { it.copy(requests = DataLoadingState.Error(ex)) }
                }.launchIn(viewModelScope)
        }

        fun refresh() {
            viewModelScope.launchIO {
                try {
                    if (seerrServerRepository.connection.firstOrNull() != null) {
                        val results = fetchRequestGridItems(viewModelScope, seerrService)
                        state.update { it.copy(requests = DataLoadingState.Success(results)) }
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Error refreshing requests")
                }
            }
        }

        fun updateBackdrop(item: DiscoverItem?) {
            viewModelScope.launchIO {
                if (item != null) {
                    backdropService.submit("discover_${item.id}", item.backDropUrl)
                }
            }
        }
    }

data class SeerrRequestsState(
    val requests: DataLoadingState<List<RequestGridItem>>,
) {
    companion object {
        val EMPTY = SeerrRequestsState(DataLoadingState.Pending)
    }
}

data class RequestGridItem(
    val request: MediaRequest,
    val item: DiscoverItem,
    val requestedSeasons: List<Int> =
        request.seasons
            ?.mapNotNull { it.seasonNumber }
            ?.sorted()
            .orEmpty(),
    val scheduledReleases: List<ScheduledRelease> = emptyList(),
    val hasReleasedMissingContent: Boolean = true,
) : CardGridItem {
    override val gridId: String = "${item.type}_${item.id}_${request.id}"
    override val playable: Boolean = false
    override val sortName: String = request.updatedAt ?: "0000"

    val requestStatus: RequestStatus
        get() = RequestStatus.from(request.status)

    val nextScheduledRelease: ScheduledRelease?
        get() = scheduledReleases.firstOrNull { it.date != null } ?: scheduledReleases.firstOrNull()

    val nextReleaseDate: LocalDate?
        get() =
            nextScheduledRelease?.date
                ?: item.releaseDate?.takeIf { it.isAfter(LocalDate.now()) }

    val isFutureRelease: Boolean
        get() =
            scheduledReleases.isNotEmpty() ||
                (item.releaseDate != null && item.releaseDate.isAfter(LocalDate.now()))

    val isActiveRequest: Boolean
        get() {
            if (item.isActivelyDownloading) return true
            if (requestStatus == RequestStatus.DECLINED || requestStatus == RequestStatus.FAILURE) {
                return false
            }
            if (item.availability != SeerrAvailability.AVAILABLE) {
                if (item.availability == SeerrAvailability.PARTIALLY_AVAILABLE && request.seasons != null) {
                    val hasUnavailableRequestedSeason =
                        request.seasons.any { season ->
                            val sAvail = SeerrAvailability.from(season.status) ?: SeerrAvailability.UNKNOWN
                            val sAvail4k = SeerrAvailability.from(season.status4k) ?: SeerrAvailability.UNKNOWN
                            val effective = if (request.is4k == true) sAvail4k else sAvail
                            effective != SeerrAvailability.AVAILABLE
                        }
                    if (hasUnavailableRequestedSeason) return true
                    return requestStatus == RequestStatus.PENDING || requestStatus == RequestStatus.APPROVED
                }
                return true
            }
            return false
        }

    fun statusSubtitle(context: Context): String {
        if (item.isActivelyDownloading) {
            val pct = item.downloadProgress?.roundToInt()
            val firstTimeLeft = item.downloads.firstNotNullOfOrNull { it.timeLeft }
            return when {
                pct != null && pct > 0 && firstTimeLeft != null -> "$pct% \u2022 $firstTimeLeft"
                pct != null && pct > 0 -> context.getString(R.string.downloading_progress, pct)
                else -> context.getString(R.string.queued)
            }
        }
        if (requestStatus == RequestStatus.DECLINED) {
            return context.getString(R.string.declined)
        }
        if (requestStatus == RequestStatus.FAILURE) {
            return context.getString(R.string.failed)
        }
        if (item.availability == SeerrAvailability.AVAILABLE) {
            return context.getString(R.string.available)
        }
        if (isFutureRelease) {
            val next = nextScheduledRelease
            val date = next?.date ?: nextReleaseDate
            val epCode = formatSeasonEpisode(next?.seasonNumber, next?.episodeNumber, padded = true)
            return when {
                epCode != null && date != null -> "$epCode \u2022 ${formatDate(date)}"
                date != null -> context.getString(R.string.releases_on, formatDate(date))
                else -> context.getString(R.string.unreleased_tba)
            }
        }
        return when {
            requestStatus == RequestStatus.PENDING || item.availability == SeerrAvailability.PENDING ->
                context.getString(R.string.pending)
            item.availability == SeerrAvailability.PARTIALLY_AVAILABLE ->
                context.getString(R.string.partially_available)
            else -> context.getString(R.string.released_not_found)
        }
    }
}

@Composable
fun SeerrRequestsPage(
    focusRequesterOnEmpty: FocusRequester?,
    positionCallback: (columns: Int, position: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeerrRequestsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState(SeerrRequestsState.EMPTY)
    val columns = 6

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(7_000L)
            viewModel.refresh()
        }
    }

    when (val state = state.requests) {
        is DataLoadingState.Error -> {
            ErrorMessage(state.message, state.exception, modifier.focusable())
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier.focusable())
        }

        is DataLoadingState.Success<List<RequestGridItem>> -> {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                if (state.data.isNotEmpty()) {
                    focusRequester.tryRequestFocus()
                } else {
                    focusRequesterOnEmpty?.tryRequestFocus()
                }
            }
            Column(modifier = modifier) {
                if (state.data.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_results),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CardGrid(
                        pager = state.data,
                        onClickItem = { index: Int, item: RequestGridItem ->
                            viewModel.navigationManager.navigateTo(Destination.DiscoveredItem(item.item))
                        },
                        onLongClickItem = { index: Int, item: RequestGridItem ->
                        },
                        onClickPlay = { _, item ->
                        },
                        letterPosition = { c: Char -> 0 },
                        gridFocusRequester = focusRequester,
                        showJumpButtons = false,
                        showLetterButtons = false,
                        spacing = 16.dp,
                        cardContent = @Composable { (item, index, onClick, onLongClick, widthPx, mod) ->
                            DiscoverItemCard(
                                item = item?.item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                showOverlay = true,
                                subtitleOverride = item?.statusSubtitle(context),
                                modifier = mod,
                            )
                        },
                        columns = columns,
                        positionCallback = positionCallback,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
