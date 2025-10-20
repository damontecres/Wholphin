package com.github.damontecres.wholphin.ui.detail.livetv

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisode
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.NavigationManager
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetProgramsDtoHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.GetProgramsDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetLiveTvChannelsRequest
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val MAX_HOURS = 48L

@HiltViewModel
class LiveTvViewModel
    @Inject
    constructor(
        val api: ApiClient,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        val loading = MutableLiveData<LoadingState>(LoadingState.Pending)

        val start =
            LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)

        val channels = MutableLiveData<List<TvChannel>>()
        val programs = MutableLiveData<List<TvProgram>>()
        val programsByChannel = MutableLiveData<Map<UUID, List<TvProgram>>>(mapOf())

        val fetchingItem = MutableLiveData<LoadingState>(LoadingState.Pending)
        val fetchedItem = MutableLiveData<BaseItem?>(null)

        fun init() {
            loading.value = LoadingState.Loading
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
                val channelsIdToIndex =
                    channels.withIndex().associateBy({ it.value.id }, { it.index })
                val maxStartDate = start.plusHours(MAX_HOURS).minusMinutes(1)
                val minEndDate = start.plusMinutes(1L)
                val request =
                    GetProgramsDto(
                        maxStartDate = maxStartDate,
                        minEndDate = minEndDate,
//                        maxEndDate = start.plusHours(25),
                        channelIds = channels.map { it.id },
                        sortBy = listOf(ItemSortBy.START_DATE),
                    )
//                val pager = ApiRequestPager(api, request, GetProgramsDtoHandler, viewModelScope).init()
//                (0..<pager.size).forEach { pager.getBlocking(it) }
                val programs =
                    GetProgramsDtoHandler
                        .execute(api, request)
                        .content.items
                        .map { dto ->
                            TvProgram(
                                id = dto.id,
                                channelId = dto.channelId!!,
                                start = dto.startDate!!,
                                end = dto.endDate!!,
                                startHours = hoursBetween(start, dto.startDate!!),
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
                            )
                        }.filter { it.startHours >= 0 && it.endHours >= 0 } // TODO shouldn't need to filter client side

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
                                    )
                                }
                            put(channel.id, fakePrograms)
                            fake.addAll(fakePrograms)
                        }
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
                    this@LiveTvViewModel.channels.value = channels
                    this@LiveTvViewModel.programs.value = finalProgramList
                    this@LiveTvViewModel.programsByChannel.value = finalProgramsByChannel
                    loading.value = LoadingState.Success
                }
            }
        }

        fun getItem(itemId: UUID) {
            fetchingItem.value = LoadingState.Loading
            viewModelScope.launchIO(LoadingExceptionHandler(fetchingItem, "Error")) {
                val result =
                    api.userLibraryApi
                        .getItem(itemId = itemId)
                        .content
                        .let { BaseItem.from(it, api) }
                withContext(Dispatchers.Main) {
                    fetchedItem.value = result
                    fetchingItem.value = LoadingState.Success
                }
            }
        }

        fun cancelRecording(
            series: Boolean,
            timerId: String?,
        ) {
            if (timerId != null) {
                viewModelScope.launch(ExceptionHandler(autoToast = true)) {
                    if (series) {
                        api.liveTvApi.cancelSeriesTimer(timerId)
                    } else {
                        api.liveTvApi.cancelTimer(timerId)
                    }
                }
            }
        }

        suspend fun refreshProgram(
            index: Int,
            timerId: String?,
            seriesTimerId: String?,
        ) {
            val newProgram =
                programs.value?.getOrNull(index)?.copy(
                    isRecording = timerId.isNotNullOrBlank(),
                    isSeriesRecording = seriesTimerId.isNotNullOrBlank(),
                )
            if (newProgram != null) {
                programs.value
                    ?.toMutableList()
                    ?.apply { set(index, newProgram) }
                    ?.let {
                        programs.setValueOnMain(it)
                    }
            }
        }
    }

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
)
