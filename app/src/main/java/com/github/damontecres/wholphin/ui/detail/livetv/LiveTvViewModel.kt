package com.github.damontecres.wholphin.ui.detail.livetv

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisode
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.NavigationManager
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.model.api.GetProgramsDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.TimerInfoDto
import org.jellyfin.sdk.model.api.request.GetLiveTvChannelsRequest
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val MAX_HOURS = 3L

@HiltViewModel
class LiveTvViewModel
    @Inject
    constructor(
        val api: ApiClient,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        val loading = MutableLiveData<LoadingState>(LoadingState.Pending)

        lateinit var start: LocalDateTime
            private set
        private lateinit var channelsIdToIndex: Map<UUID, Int>
        private val mutex = Mutex()

        val channels = MutableLiveData<List<TvChannel>>()
        val channelProgramCount = mutableMapOf<UUID, Int>()
        val programs = MutableLiveData<List<TvProgram>>()
        val programsByChannel = MutableLiveData<Map<UUID, List<TvProgram>>>(mapOf())

        val fetchingItem = MutableLiveData<LoadingState>(LoadingState.Pending)
        val fetchedItem = MutableLiveData<BaseItem?>(null)

        val offset = MutableLiveData(0)
        val programOffset = MutableLiveData(0)
        private val range = 8
        private var currentIndex = 0
        val fetchedRange = MutableLiveData<IntRange>(0..<range)

        fun init(firstLoad: Boolean) {
            start = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
            if (!firstLoad) {
                loading.value = LoadingState.Loading
            }
            viewModelScope.launch(Dispatchers.IO + LoadingExceptionHandler(loading, "Could not fetch channels")) {
                val channelData by api.liveTvApi.getLiveTvChannels(
                    GetLiveTvChannelsRequest(
                        startIndex = 0,
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
                fetchPrograms(channels, 0..<range.coerceAtMost(channels.size))

                withContext(Dispatchers.Main) {
                    this@LiveTvViewModel.channels.value = channels
                    loading.value = LoadingState.Success
                }
            }
        }

        private suspend fun fetchProgramsWithLoading(channels: List<TvChannel>) {
            loading.setValueOnMain(LoadingState.Loading)
            fetchPrograms(channels, fetchedRange.value!!)
            loading.setValueOnMain(LoadingState.Success)
        }

        private suspend fun fetchPrograms(
            channels: List<TvChannel>,
            range: IntRange,
        ) = mutex.withLock {
            val maxStartDate = start.plusHours(MAX_HOURS).minusMinutes(1)
            val minEndDate = start.plusMinutes(1L)
            Timber.v("Fetching programs for ${channels.size} channels")
            val request =
                GetProgramsDto(
                    maxStartDate = maxStartDate,
                    minEndDate = minEndDate,
                    channelIds = channels.map { it.id },
                    sortBy = listOf(ItemSortBy.START_DATE),
                )
            val programs =
                api.liveTvApi
                    .getPrograms(request)
                    .content.items
                    .map { dto ->
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
                        TvProgram(
                            id = dto.id,
                            channelId = dto.channelId!!,
                            start = dto.startDate!!,
                            end = dto.endDate!!,
                            startHours = hoursBetween(start, dto.startDate!!).coerceAtLeast(0f),
                            endHours = hoursBetween(start, dto.endDate!!),
                            duration = dto.runTimeTicks!!.ticks,
                            name = dto.seriesName ?: dto.name,
                            subtitle = dto.episodeTitle.takeIf { dto.isSeries ?: false },
                            seasonEpisode =
                                if (dto.indexNumber != null && dto.parentIndexNumber != null) {
                                    SeasonEpisode(dto.parentIndexNumber!!, dto.indexNumber!!)
                                } else {
                                    null
                                },
                            isRecording = dto.timerId.isNotNullOrBlank(),
                            isSeriesRecording = dto.seriesTimerId.isNotNullOrBlank(),
                            isRepeat = dto.isRepeat ?: false,
                            category = category,
                        )
                    }

            val programsByChannel = programs.groupBy { it.channelId }
            val emptyChannels = channels.filter { programsByChannel[it.id].orEmpty().isEmpty() }
            val fake = mutableListOf<TvProgram>()
            val finalProgramsByChannel =
                programsByChannel.toMutableMap().apply {
                    emptyChannels.forEach { channel ->
                        val fakePrograms =
                            (0..<MAX_HOURS).map {
                                TvProgram(
                                    id = UUID.randomUUID(), // TODO
                                    channelId = channel.id,
                                    start = start.plusHours(it),
                                    end = start.plusHours(it + 1),
                                    startHours = it.toFloat(),
                                    endHours = (it + 1).toFloat(),
                                    duration = 60.seconds,
                                    name = "No data",
                                    subtitle = null,
                                    seasonEpisode = null,
                                    isRecording = false,
                                    isSeriesRecording = false,
                                    isRepeat = false,
                                    category = ProgramCategory.FAKE,
                                )
                            }
                        put(channel.id, fakePrograms)
                        fake.addAll(fakePrograms)
                    }
                }
            finalProgramsByChannel.forEach { (channelId, programs) ->
                channelProgramCount[channelId] = programs.size
            }
            val finalProgramList =
                (programs + fake).sortedWith(
                    compareBy(
                        { channelsIdToIndex[it.channelId]!! },
                        { it.start },
                    ),
                )
            Timber.d("Got ${programs.size} programs & ${fake.size} fake programs")
            withContext(Dispatchers.Main) {
                this@LiveTvViewModel.programs.value = finalProgramList
                this@LiveTvViewModel.programsByChannel.value = finalProgramsByChannel
                this@LiveTvViewModel.fetchedRange.value = range
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
            programIndex: Int,
            programId: UUID,
            series: Boolean,
            timerId: String?,
        ) {
            if (timerId != null) {
                viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                    if (series) {
                        api.liveTvApi.cancelSeriesTimer(timerId)
                        fetchProgramsWithLoading(channels.value.orEmpty())
                    } else {
                        api.liveTvApi.cancelTimer(timerId)
                        refreshProgram(programIndex, programId)
                    }
                }
            }
        }

        fun record(
            programIndex: Int,
            programId: UUID,
            series: Boolean,
        ) {
            viewModelScope.launchIO {
                val d by api.liveTvApi.getDefaultTimer(programId.toServerString())
                if (series) {
                    api.liveTvApi.createSeriesTimer(d)
                    fetchProgramsWithLoading(channels.value.orEmpty())
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
                    refreshProgram(programIndex, programId)
                }
            }
        }

        suspend fun refreshProgram(
            programIndex: Int,
            programId: UUID,
        ) = mutex.withLock {
            loading.setValueOnMain(LoadingState.Loading)
            val program by api.liveTvApi.getProgram(programId.toServerString())
            val newProgram =
                programs.value?.getOrNull(programIndex)?.copy(
                    isRecording = program.timerId.isNotNullOrBlank(),
                    isSeriesRecording = program.seriesTimerId.isNotNullOrBlank(),
                )
            Timber.v("new program %s", newProgram)
            if (newProgram != null) {
                programs.value
                    ?.toMutableList()
                    ?.apply {
                        this[programIndex] = newProgram
                    }?.let {
                        this@LiveTvViewModel.programs.setValueOnMain(it)
                    }
            }
            loading.setValueOnMain(LoadingState.Success)
        }

        fun onFocusChannel(position: RowColumn): Job? {
            return channels.value?.let { channels ->
                val fetchedRange = fetchedRange.value!!
                val quarter = (fetchedRange.last - fetchedRange.start) / 4
                val rangeStart = fetchedRange.start + quarter
                val rangeEnd = fetchedRange.last - quarter
                val testRange = rangeStart..<rangeEnd

                Timber.v(
                    "onFocusChannel: position=$position, fetchedRange=$fetchedRange, testRange=$testRange",
                )
                val fetchStart = (position.row - range).coerceAtLeast(0)
                val fetchEnd = (position.row + range).coerceAtMost(channels.size)
                val newFetchRange = fetchStart..<fetchEnd
                // If current channel  is not within +/- range
                // And the potential new fetch range is not wholly within the current (eg not near the top or bottom)
                // Fetch new data
                if (position.row !in testRange && !newFetchRange.within(fetchedRange)) {
                    Timber.v("Loading more programs for channels $newFetchRange")
                    return viewModelScope.launchIO {
                        fetchPrograms(channels, newFetchRange)
//                        withContext(Dispatchers.Main) {
//                            this@LiveTvViewModel.offset.value = fetchStart
//                            currentIndex = index
//                            this@LiveTvViewModel.programOffset.value = programOffset
//                        }
                    }
                }
                return null
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
    val seasonEpisode: SeasonEpisode?,
    val isRecording: Boolean,
    val isSeriesRecording: Boolean,
    val isRepeat: Boolean,
    val category: ProgramCategory?,
) {
    val isFake = category == ProgramCategory.FAKE
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
