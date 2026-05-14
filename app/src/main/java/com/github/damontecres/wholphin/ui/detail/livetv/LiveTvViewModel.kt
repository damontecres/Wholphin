package com.github.damontecres.wholphin.ui.detail.livetv

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.WholphinApplication
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisode
import com.github.damontecres.wholphin.ui.dot
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.GetProgramsDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.TimerInfoDto
import org.jellyfin.sdk.model.api.request.GetLiveTvChannelsRequest
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

const val MAX_HOURS = 48L

@OptIn(FlowPreview::class)
@HiltViewModel
class LiveTvViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val api: ApiClient,
        val navigationManager: NavigationManager,
        val preferences: DataStore<AppPreferences>,
        private val serverRepository: ServerRepository,
        private val imageUrlService: ImageUrlService,
    ) : ViewModel() {
        private lateinit var channelsIdToIndex: Map<UUID, Int>
        private val mutex = Mutex()

        private val _state = MutableStateFlow(LiveTvState())
        val state: StateFlow<LiveTvState> = _state

        private val _programDialogState = MutableStateFlow(ProgramDialogState())
        val programDialogState: StateFlow<ProgramDialogState> = _programDialogState

        private val range = 100

        init {
            viewModelScope.launchDefault {
                preferences.data
                    .map {
                        it.interfacePreferences.liveTvPreferences.let {
                            Pair(it.sortByRecentlyWatched, it.favoriteChannelsAtBeginning)
                        }
                    }.distinctUntilChanged()
                    .debounce { 500.milliseconds }
                    .collectLatest {
                        Timber.v("Init due to pref change")
                        _state.update { LiveTvState() }
                        init(it.first, it.second)
                    }
            }
        }

        private fun init(
            sortByRecentlyWatched: Boolean,
            favoriteChannelsAtBeginning: Boolean,
        ) {
            viewModelScope.launchDefault {
                try {
                    val guideTimes = buildGuideTimes()
                    _state.update { it.copy(guideTimes = guideTimes) }
                    val guideStart = guideTimes.first()
                    val channelData by api.liveTvApi.getLiveTvChannels(
                        GetLiveTvChannelsRequest(
                            startIndex = 0,
                            userId = serverRepository.currentUser.value?.id,
                            enableFavoriteSorting = favoriteChannelsAtBeginning,
                            sortBy =
                                if (sortByRecentlyWatched) {
                                    listOf(ItemSortBy.DATE_PLAYED)
                                } else {
                                    null
                                },
                            sortOrder =
                                if (sortByRecentlyWatched) {
                                    SortOrder.DESCENDING
                                } else {
                                    null
                                },
                            addCurrentProgram = false,
                        ),
                    )
                    val channels =
                        channelData.items
                            .map {
                                TvChannel(
                                    id = it.id,
                                    number = it.channelNumber,
                                    name = it.channelName,
                                    imageUrl =
                                        imageUrlService.getItemImageUrl(it.id, ImageType.PRIMARY),
                                    favorite = it.userData?.isFavorite == true,
                                )
                            }
                    Timber.d("Got ${channels.size} channels")
                    channelsIdToIndex =
                        channels.withIndex().associateBy({ it.value.id }, { it.index })
                    // Initially, quickly load the first 10 channels (only some are visible immediately), then below will load more
                    // This makes the guide appear faster, and load more usable data in the background
                    val initial = 10
                    fetchPrograms(guideStart, channels, 0..<initial.coerceAtMost(channels.size))

                    _state.update {
                        it.copy(
                            channels = channels,
                            loading = LoadingState.Success,
                        )
                    }
                    // Now load the full range
                    if (channels.size > initial) {
                        fetchPrograms(guideStart, channels, 0..<range.coerceAtMost(channels.size))
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Erroring during init")
                    _state.update { it.copy(loading = LoadingState.Error(ex)) }
                }
            }
        }

        /**
         * Creates a list of [LocalDateTime] for each hour from now
         */
        private fun buildGuideTimes() =
            buildList {
                val start = LocalDateTime.now().roundDownToHalfHour()
                add(start)
                if (start.minute == 30) {
                    add(start.plusMinutes(30))
                }
                repeat(MAX_HOURS.toInt() - 1) {
                    add(last().plusHours(1))
                }
            }

        private suspend fun refreshPrograms(
            channels: List<TvChannel>,
            range: IntRange,
        ) {
            _state.update { it.copy(refreshing = true) }
            val guideStart = _state.value.guideTimes.first()
            try {
                fetchPrograms(guideStart, channels, range)
                _state.update { it.copy(refreshing = false) }
            } catch (_: CancellationException) {
                // no-op
            } catch (ex: Exception) {
                Timber.e(ex, "Error fetching programs")
                _state.update { it.copy(loading = LoadingState.Error(ex)) }
            }
        }

        /**
         * Get live TV programs for a subset of channels
         *
         * @param guideStart The start timestamp to fetch from
         * @param channels The full list of channels
         * @param channelIndices The indices of which channels to fetch programs for
         */
        private suspend fun fetchPrograms(
            guideStart: LocalDateTime,
            channels: List<TvChannel>,
            channelIndices: IntRange,
        ) = mutex.withLock {
            val maxStartDate = guideStart.plusHours(MAX_HOURS).minusMinutes(1)
            val minEndDate = guideStart.plusMinutes(1L)
            val channelsToFetch = channels.subList(channelIndices.first, channelIndices.last + 1)
            Timber.v("Fetching programs for $channelIndices channels ${channelsToFetch.size}")
            val request =
                GetProgramsDto(
                    maxStartDate = maxStartDate,
                    minEndDate = minEndDate,
                    channelIds = channelsToFetch.map { it.id },
                    sortBy = listOf(ItemSortBy.START_DATE),
                    userId = serverRepository.currentUser.value?.id,
                    fields = listOf(ItemFields.OVERVIEW),
                )
            val fetchedPrograms =
                api.liveTvApi
                    .getPrograms(request)
                    .content.items
                    .filter { it.endDate?.isAfter(guideStart) == true }
            val programsByChannel = mutableMapOf<UUID, List<TvProgram>>()
            val fetchedGroupedBy = fetchedPrograms.groupBy { it.channelId }
            fetchedGroupedBy.forEach { (channelId, programs) ->
                if (channelId != null) {
                    val listing = mutableListOf<TvProgram>()
                    programs as MutableList<BaseItemDto>
                    programs.forEachIndexed { index, dto ->
                        val category =
                            if (dto.isKids ?: false) {
                                ProgramCategory.KIDS
                            } else if (dto.isMovie ?: false) {
                                ProgramCategory.MOVIE
                            } else if (dto.isNews ?: false) {
                                ProgramCategory.NEWS
                            } else if (dto.isSports ?: false) {
                                ProgramCategory.SPORTS
                            } else {
                                null
                            }
                        // Clean up name/subtitles by collapsing whitespace (including newlines) into single spaces
                        val name = (dto.seriesName ?: dto.name)?.replace(Regex("\\s+"), " ")
                        val subtitle =
                            dto.episodeTitle
                                .takeIf { dto.isSeries ?: false }
                                ?.replace(Regex("\\s+"), " ")
                        val p =
                            TvProgram(
                                id = dto.id,
                                channelId = dto.channelId!!,
                                start = dto.startDate!!,
                                end = dto.endDate!!,
                                startHours =
                                    hoursBetween(
                                        guideStart,
                                        dto.startDate!!,
                                    ).coerceAtLeast(0f),
                                endHours = hoursBetween(guideStart, dto.endDate!!),
                                duration = dto.runTimeTicks!!.ticks,
                                name = name,
                                subtitle = subtitle,
                                overview = dto.overview,
                                officialRating = dto.officialRating,
                                seasonEpisode =
                                    if (dto.indexNumber != null && dto.parentIndexNumber != null) {
                                        SeasonEpisode(
                                            dto.parentIndexNumber!!,
                                            dto.indexNumber!!,
                                        )
                                    } else {
                                        null
                                    },
                                isRecording = dto.timerId.isNotNullOrBlank(),
                                isSeriesRecording = dto.seriesTimerId.isNotNullOrBlank(),
                                isRepeat = dto.isRepeat ?: false,
                                category = category,
                                imageUrl =
                                    imageUrlService.getItemImageUrl(
                                        dto.id,
                                        ImageType.PRIMARY,
                                    ),
                            )
                        if (index == 0) {
                            if (p.startHours > 0) {
                                // Fill out before the first program
                                var start = 0f
                                var end = min(start + 1f, p.startHours)
                                while (start < p.startHours) {
                                    val fake =
                                        TvProgram.fake(
                                            guideStart,
                                            channelId,
                                            start,
                                            end,
                                        )
                                    start = end
                                    end = min(start + 1f, p.startHours)
                                    listing.add(fake)
                                }
                            }
                            listing.add(p)
                        } else if (index > 0 && listing.isNotEmpty()) {
                            var previous = listing.last()
                            while (previous.endHours < p.startHours) {
                                // Fill gaps between programs
                                val start = previous.endHours
                                val duration = (p.startHours - start).coerceAtMost(1f)
//                                Timber.v("Adding fake for $channelId: $start=>${start + duration}")
                                previous =
                                    TvProgram(
                                        id = UUID.randomUUID(),
                                        channelId = channelId,
                                        start = previous.end,
                                        end = previous.end.plusMinutes((duration * 60).toLong()),
                                        startHours = start,
                                        endHours = start + duration,
                                        duration = (duration * 60).toInt().minutes,
                                        name = context.getString(R.string.no_data),
                                        subtitle = null,
                                        seasonEpisode = null,
                                        isRecording = false,
                                        isSeriesRecording = false,
                                        isRepeat = false,
                                        category = ProgramCategory.FAKE,
                                    )
                                listing.add(previous)
                            }
                            listing.add(p)
                        }
                        if (index == (programs.size - 1)) {
                            if (p.endHours < MAX_HOURS) {
                                // Fill after the last program
                                val end = (p.endHours + 1).toInt()
                                listing.add(
                                    TvProgram.fake(
                                        guideStart,
                                        channelId,
                                        p.endHours,
                                        end.toFloat(),
                                    ),
                                )
                                (end..<MAX_HOURS).forEach {
                                    listing.add(
                                        TvProgram.fake(
                                            guideStart,
                                            channelId,
                                            it.toFloat(),
                                            it + 1f,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    programsByChannel[channelId] = listing
                }
            }
            val emptyChannels =
                channelsToFetch.filter { programsByChannel[it.id].orEmpty().isEmpty() }
            val fake = mutableListOf<TvProgram>()

            emptyChannels.forEach { channel ->
                val fakePrograms =
                    (0..<MAX_HOURS).map {
                        TvProgram(
                            id = UUID.randomUUID(),
                            channelId = channel.id,
                            start = guideStart.plusHours(it),
                            end = guideStart.plusHours(it + 1),
                            startHours = it.toFloat(),
                            endHours = (it + 1).toFloat(),
                            duration = 60.seconds,
                            name = context.getString(R.string.no_data),
                            subtitle = null,
                            seasonEpisode = null,
                            isRecording = false,
                            isSeriesRecording = false,
                            isRepeat = false,
                            category = ProgramCategory.FAKE,
                        )
                    }
                programsByChannel[channel.id] = fakePrograms
                fake.addAll(fakePrograms)
            }

            val channelProgramCount = programsByChannel.map { it.key to it.value.size }.toMap()

            val finalProgramList =
                (programsByChannel.values.flatten())
                    .sortedWith(
                        compareBy(
                            { channelsIdToIndex[it.channelId]!! },
                            { it.start },
                        ),
                    )
            Timber.d("Got ${fetchedPrograms.size} programs & ${finalProgramList.size} total programs")
            _state.update {
                it.copy(
                    channelProgramCount = channelProgramCount,
                    programs =
                        FetchedPrograms(
                            channelIndices,
                            finalProgramList,
                            programsByChannel,
                        ),
                )
            }
        }

        fun fetchProgramForDialog(programId: UUID) {
            _programDialogState.update { it.copy(loading = DataLoadingState.Loading) }
            viewModelScope.launchDefault {
                try {
                    val result =
                        api.liveTvApi
                            .getProgram(programId.toServerString())
                            .content
                            .let { BaseItem(it) }
                    _programDialogState.update { it.copy(loading = DataLoadingState.Success(result)) }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching program $programId")
                    _programDialogState.update { it.copy(loading = DataLoadingState.Error(ex)) }
                }
            }
        }

        fun cancelRecording(
            series: Boolean,
            timerId: String?,
        ) {
            if (timerId != null) {
                viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                    if (series) {
                        api.liveTvApi.cancelSeriesTimer(timerId)
                    } else {
                        api.liveTvApi.cancelTimer(timerId)
                    }
                    state.value.let {
                        refreshPrograms(it.channels, it.programs.range)
                    }
                }
            }
        }

        fun record(
            programId: UUID,
            series: Boolean,
        ) {
            viewModelScope.launchIO {
                val d by api.liveTvApi.getDefaultTimer(programId.toServerString())
                if (series) {
                    api.liveTvApi.createSeriesTimer(d)
                } else {
                    val payload =
                        TimerInfoDto(
                            id = d.id,
                            type = d.type,
                            serverId = d.serverId,
                            externalId = d.externalId,
                            channelId = d.channelId,
                            externalChannelId = d.externalChannelId,
                            channelName = d.channelName,
                            programId = d.programId,
                            externalProgramId = d.externalProgramId,
                            name = d.name,
                            overview = d.overview,
                            startDate = d.startDate,
                            endDate = d.endDate,
                            serviceName = d.serviceName,
                            priority = d.priority,
                            prePaddingSeconds = d.prePaddingSeconds,
                            postPaddingSeconds = d.postPaddingSeconds,
                            isPrePaddingRequired = d.isPrePaddingRequired,
                            isPostPaddingRequired = d.isPostPaddingRequired,
                            keepUntil = d.keepUntil,
                        )
                    api.liveTvApi.createTimer(payload)
                }
                state.value.let {
                    refreshPrograms(it.channels, it.programs.range)
                }
            }
        }

        private var focusLoadingJob: Job? = null

        /**
         * Callback when focusing on the EPG grid
         *
         * This determines if more programs/channels should be fetched based on the current position
         */
        fun onFocusChannel(position: RowColumn) {
            val state = state.value
            val channels = state.channels
            val fetchedRange = state.programs.range
            val quarter = range / 4
            var rangeStart = fetchedRange.start + quarter
            var rangeEnd = fetchedRange.last - quarter

            if (rangeEnd - rangeStart < range) {
                if (position.row < range / 2) {
                    // Close to beginning
                    rangeStart = 0
                } else if (position.row > (channels.size - range / 2)) {
                    // Close to the end
                    rangeEnd = channels.size
                }
            }
            val testRange = rangeStart..<rangeEnd

            Timber.v(
                "onFocusChannel: position=%s, fetchedRange=%s, testRange=%s",
                position,
                fetchedRange,
                testRange,
            )

            val fetchStart = (position.row - range).coerceAtLeast(0)
            val fetchEnd = (position.row + range).coerceAtMost(channels.size)
            val newFetchRange = fetchStart..<fetchEnd
            // If current channel  is not within +/- range
            // And the potential new fetch range is not wholly within the current (eg not near the top or bottom)
            // Fetch new data
            if (position.row !in testRange && !newFetchRange.within(fetchedRange)) {
                Timber.v("Loading more programs for channels $newFetchRange")
                focusLoadingJob?.cancel()
                focusLoadingJob =
                    viewModelScope.launchIO {
                        refreshPrograms(channels, newFetchRange)
                    }
            }
        }
    }

fun IntRange.within(other: IntRange): Boolean = this.first >= other.first && this.last <= other.last

/**
 * Returns the number of hours between two [LocalDateTime]
 */
fun hoursBetween(
    start: LocalDateTime,
    target: LocalDateTime,
): Float =
    java.time.Duration
        .between(start, target)
        .seconds / (60f * 60f)

data class LiveTvState(
    val loading: LoadingState = LoadingState.Pending,
    val refreshing: Boolean = false,
    val guideTimes: List<LocalDateTime> = emptyList(),
    val channels: List<TvChannel> = emptyList(),
    val channelProgramCount: Map<UUID, Int> = emptyMap(),
    val programs: FetchedPrograms = FetchedPrograms(),
)

data class ProgramDialogState(
    val loading: DataLoadingState<BaseItem> = DataLoadingState.Pending,
)

data class TvChannel(
    val id: UUID,
    val number: String?,
    val name: String?,
    val imageUrl: String?,
    val favorite: Boolean,
)

@Stable
data class TvProgram(
    val id: UUID,
    val channelId: UUID,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val startHours: Float,
    val endHours: Float,
    val duration: Duration,
    val name: String?,
    val subtitle: String?,
    val overview: String? = null,
    val seasonEpisode: SeasonEpisode?,
    val isRecording: Boolean,
    val isSeriesRecording: Boolean,
    val isRepeat: Boolean,
    val category: ProgramCategory?,
    val officialRating: String? = null,
    val imageUrl: String? = null,
) {
    val isFake = category == ProgramCategory.FAKE

    val quickDetails by lazy {
        val now = LocalDateTime.now()
        buildAnnotatedString {
            val differentDay = start.toLocalDate() != now.toLocalDate()
            val time =
                DateUtils.formatDateRange(
                    WholphinApplication.instance,
                    start
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .epochSecond * 1000,
                    end
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .epochSecond * 1000,
                    DateUtils.FORMAT_SHOW_TIME or if (differentDay) DateUtils.FORMAT_SHOW_WEEKDAY else 0,
                )
            append(time)

            if (!isFake) {
                dot()
                duration
                    .roundMinutes
                    .toString()
                    .let(::append)
                if (now.isAfter(start) && now.isBefore(end)) {
                    dot()
                    java.time.Duration
                        .between(now, end)
                        .toKotlinDuration()
                        .roundMinutes
                        .let { append("$it left") }
                }
                seasonEpisode?.let { "S${it.season} E${it.episode}" }?.let {
                    dot()
                    append(it)
                }
                officialRating?.let {
                    dot()
                    append(it)
                }
            }
        }
    }

    companion object {
        private val NO_DATA = WholphinApplication.instance.getString(R.string.no_data)

        fun fake(
            zeroHourStart: LocalDateTime,
            channelId: UUID,
            startHours: Float,
            endHours: Float,
        ) = TvProgram(
            id = UUID.randomUUID(),
            channelId = channelId,
            start = zeroHourStart.plusMinutes((startHours * 60).toLong()),
            end = zeroHourStart.plusMinutes((endHours * 60).toLong()),
            startHours = startHours,
            endHours = endHours,
            duration = ((endHours - startHours) * 60).toInt().minutes,
            name = NO_DATA,
            subtitle = null,
            overview = null,
            seasonEpisode = null,
            isRecording = false,
            isSeriesRecording = false,
            isRepeat = false,
            category = ProgramCategory.FAKE,
        )
    }
}

enum class ProgramCategory(
    val color: Color?,
) {
    KIDS(AppColors.DarkCyan),
    NEWS(AppColors.DarkGreen),
    MOVIE(AppColors.DarkPurple),
    SPORTS(AppColors.DarkRed),
    FAKE(null),
}

data class FetchedPrograms(
    val range: IntRange = 0..0,
    val programs: List<TvProgram> = emptyList(),
    val programsByChannel: Map<UUID, List<TvProgram>> = emptyMap(),
)

fun LocalDateTime.roundDownToHalfHour(): LocalDateTime {
    val min = minute % 30L
    return minusMinutes(min).truncatedTo(ChronoUnit.MINUTES)
}
