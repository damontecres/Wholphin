package com.github.damontecres.wholphin.ui.detail.livetv

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.WholphinApplication
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisode
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
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
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
    ) : ViewModel() {
        val loading = MutableLiveData<LoadingState>(LoadingState.Pending)

        lateinit var guideStart: LocalDateTime
            private set
        private lateinit var channelsIdToIndex: Map<UUID, Int>
        private val mutex = Mutex()

        val channels = MutableLiveData<List<TvChannel>>()
        val channelProgramCount = mutableMapOf<UUID, Int>()
        val programs = MutableLiveData<FetchedPrograms>()

        val fetchingItem = MutableLiveData<LoadingState>(LoadingState.Pending)
        val fetchedItem = MutableLiveData<BaseItem?>(null)

        private val range = 100

        init {
            viewModelScope.launchIO {
                preferences.data
                    .map {
                        it.interfacePreferences.liveTvPreferences.let {
                            Pair(it.sortByRecentlyWatched, it.favoriteChannelsAtBeginning)
                        }
                    }.distinctUntilChanged()
                    .debounce { 500.milliseconds }
                    .collectLatest {
                        Timber.v("Init due to pref change")
                        loading.setValueOnMain(LoadingState.Pending)
                        init()
                    }
            }
        }

        fun init() {
            guideStart = LocalDateTime.now()
                .truncatedTo(ChronoUnit.HOURS)
            viewModelScope.launch(
                Dispatchers.IO +
                    LoadingExceptionHandler(
                        loading,
                        "Could not fetch channels",
                    ),
            ) {
                val prefs =
                    (preferences.data.firstOrNull() ?: AppPreferences.getDefaultInstance())
                        .interfacePreferences.liveTvPreferences
                val channelData by api.liveTvApi.getLiveTvChannels(
                    GetLiveTvChannelsRequest(
                        startIndex = 0,
                        userId = serverRepository.currentUser.value?.id,
                        enableFavoriteSorting = prefs.favoriteChannelsAtBeginning,
                        sortBy =
                            if (prefs.sortByRecentlyWatched) {
                                listOf(ItemSortBy.DATE_PLAYED)
                            } else {
                                null
                            },
                        sortOrder =
                            if (prefs.sortByRecentlyWatched) {
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
                                it.id,
                                it.channelNumber,
                                it.channelName,
                                api.imageApi.getItemImageUrl(it.id, ImageType.PRIMARY),
                            )
                        }
                Timber.d("Got ${channels.size} channels")
                channelsIdToIndex =
                    channels.withIndex().associateBy({ it.value.id }, { it.index })
                // Initially, quickly load the first 10 channels (only some are visible immediately), then below will load more
                // This makes the guide appear faster, and load more usable data in the background
                val initial = 10
                fetchPrograms(channels, 0..<initial.coerceAtMost(channels.size))

                withContext(Dispatchers.Main) {
                    this@LiveTvViewModel.channels.value = channels
                    loading.value = LoadingState.Success
                }
                // Now load the full range
                if (channels.size > initial) {
                    fetchPrograms(channels, 0..<range.coerceAtMost(channels.size))
                }
            }
        }

        private suspend fun fetchProgramsWithLoading(
            channels: List<TvChannel>,
            range: IntRange,
        ) {
            loading.setValueOnMain(LoadingState.Loading)
            fetchPrograms(channels, range)
            loading.setValueOnMain(LoadingState.Success)
        }

        private suspend fun fetchPrograms(
            channels: List<TvChannel>,
            range: IntRange,
        ) = mutex.withLock {
            val zone = ZoneId.systemDefault() // Get device's timezone
            val guideStartUtc = guideStart
                .atZone(zone) // This 17:00 is actually 17:00 CURRENT ZONE time
                .withZoneSameInstant(ZoneOffset.UTC) // Convert to UTC ( for example UTC+2 -> 15:00)
                .toLocalDateTime() // Strip the zone info

            val maxStartDate = guideStartUtc.plusHours(MAX_HOURS).minusMinutes(1)
            val minEndDate = guideStartUtc.plusMinutes(1L)

            val channelsToFetch = channels.subList(range.first, range.last + 1)
            Timber.v("Fetching programs for $range channels ${channelsToFetch.size}")
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
                                name = dto.seriesName ?: dto.name,
                                subtitle = dto.episodeTitle.takeIf { dto.isSeries ?: false },
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
                programsByChannel.put(channel.id, fakePrograms)
                fake.addAll(fakePrograms)
            }

            programsByChannel.forEach { (channelId, programs) ->
                channelProgramCount[channelId] = programs.size
            }
            val finalProgramList =
                (programsByChannel.values.flatten())
                    .sortedWith(
                        compareBy(
                            { channelsIdToIndex[it.channelId]!! },
                            { it.start },
                        ),
                    )
            Timber.d("Got ${fetchedPrograms.size} programs & ${finalProgramList.size} total programs")
            withContext(Dispatchers.Main) {
                this@LiveTvViewModel.programs.value =
                    FetchedPrograms(range, finalProgramList, programsByChannel)
            }
        }

        fun getItem(programId: UUID) {
            fetchingItem.value = LoadingState.Loading
            viewModelScope.launchIO(LoadingExceptionHandler(fetchingItem, "Error")) {
                val result =
                    api.liveTvApi
                        .getProgram(programId.toServerString())
                        .content
                        .let { BaseItem.from(it, api) }
                withContext(Dispatchers.Main) {
                    fetchedItem.value = result
                    fetchingItem.value = LoadingState.Success
                }
                if (result.data.seriesTimerId != null) {
                    val items =
                        api.liveTvApi
                            .getPrograms(GetProgramsDto(seriesTimerId = result.data.seriesTimerId))
                            .content.items
                    Timber.v("items=$items")
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
                    fetchProgramsWithLoading(channels.value.orEmpty(), programs.value!!.range)
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
                fetchProgramsWithLoading(channels.value.orEmpty(), programs.value!!.range)
            }
        }

        private var focusLoadingJob: Job? = null

        fun onFocusChannel(position: RowColumn) {
            channels.value?.let { channels ->
                val fetchedRange = programs.value!!.range
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
                            fetchProgramsWithLoading(channels, newFetchRange)
                        }
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

data class TvChannel(
    val id: UUID,
    val number: String?,
    val name: String?,
    val imageUrl: String?,
)

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
) {
    val isFake = category == ProgramCategory.FAKE

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
    val range: IntRange,
    val programs: List<TvProgram>,
    val programsByChannel: Map<UUID, List<TvProgram>>,
)
