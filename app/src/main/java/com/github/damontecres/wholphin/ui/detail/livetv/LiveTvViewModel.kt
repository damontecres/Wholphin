package com.github.damontecres.wholphin.ui.detail.livetv

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisode
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetProgramsDtoHandler
import com.github.damontecres.wholphin.util.LazyList
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.model.api.GetProgramsDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.request.GetLiveTvChannelsRequest
import org.jellyfin.sdk.model.extensions.ticks
import java.time.LocalDateTime
import java.util.AbstractList
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@HiltViewModel
class LiveTvViewModel
    @Inject
    constructor(
        val api: ApiClient,
    ) : ViewModel() {
        val loading = MutableLiveData<LoadingState>(LoadingState.Pending)

        val start = LocalDateTime.now()
        val channels = MutableLiveData<List<TvChannel>>()
        val programs = MutableLiveData<List<TvProgram?>>()

        private val programMap = mutableMapOf<UUID, MutableLiveData<List<TvProgram?>>>()

        fun init() {
            loading.value = LoadingState.Loading
            viewModelScope.launch(Dispatchers.IO + LoadingExceptionHandler(loading, "Could not fetch channels")) {
                val channelData by api.liveTvApi.getLiveTvChannels(
                    GetLiveTvChannelsRequest(
                        startIndex = 8,
                    ),
                )
                val channels =
                    LazyList(channelData.items) {
                        TvChannel(
                            it.id,
                            it.channelNumber,
                            it.channelName,
                            api.imageApi.getItemImageUrl(it.id, ImageType.PRIMARY),
                        )
                    }
                val request =
                    GetProgramsDto(
                        minStartDate = start,
                        channelIds = channels.map { it.id },
                    )
                val pager = ApiRequestPager(api, request, GetProgramsDtoHandler, viewModelScope).init()
                (0..<pager.size).forEach { pager.getBlocking(it) }
                val programs =
                    LazyList(pager) { item ->
                        item?.data?.let { dto ->
                            TvProgram(
                                id = dto.id,
                                channelId = dto.channelId!!,
                                start = dto.startDate!!,
                                end = dto.endDate!!,
                                duration = dto.runTimeTicks!!.ticks,
                                name = dto.seriesName ?: dto.name,
                                subtitle = dto.name.takeIf { dto.seriesName.isNullOrBlank() },
                                seasonEpisode = null, // TODO
                            )
                        }
                    }
                withContext(Dispatchers.Main) {
                    this@LiveTvViewModel.channels.value = channels
                    this@LiveTvViewModel.programs.value = programs
                    loading.value = LoadingState.Success
                }
            }
        }

        fun getPrograms(channelId: UUID): MutableLiveData<List<TvProgram?>> =
            programMap.getOrPut(channelId) {
                val data = MutableLiveData<List<TvProgram?>>(listOf())
                viewModelScope.launch(Dispatchers.IO + ExceptionHandler()) {
                    val request =
                        GetProgramsDto(
                            minStartDate = start,
                            channelIds = listOf(channelId),
                        )
                    val pager = ApiRequestPager(api, request, GetProgramsDtoHandler, viewModelScope).init()
                    val programList =
                        if (pager.isEmpty()) {
                            object : AbstractList<TvProgram?>() {
                                override fun get(index: Int): TvProgram? {
                                    val start = start.plusHours(index.toLong())
                                    val end = start.plusHours(1L)
                                    return TvProgram(
                                        id = UUID.randomUUID(),
                                        channelId = channelId,
                                        start = start,
                                        end = start,
                                        duration = 60.minutes,
                                        name = "Unknown",
                                        subtitle = null,
                                        seasonEpisode = null, // TODO
                                    )
                                }

                                override val size: Int
                                    get() = Int.MAX_VALUE
                            }
                        } else {
                            LazyList(pager) { item ->
                                item?.data?.let { dto ->
                                    TvProgram(
                                        id = dto.id,
                                        channelId = channelId,
                                        start = dto.startDate!!,
                                        end = dto.endDate!!,
                                        duration = dto.runTimeTicks!!.ticks,
                                        name = dto.seriesName ?: dto.name,
                                        subtitle = dto.name.takeIf { dto.seriesName.isNullOrBlank() },
                                        seasonEpisode = null, // TODO
                                    )
                                }
                            }
                        }
                    withContext(Dispatchers.Main) {
                        data.value = programList
                    }
                }
                data
            }
    }

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
    val duration: Duration,
    val name: String?,
    val subtitle: String?,
    val seasonEpisode: SeasonEpisode?,
)
