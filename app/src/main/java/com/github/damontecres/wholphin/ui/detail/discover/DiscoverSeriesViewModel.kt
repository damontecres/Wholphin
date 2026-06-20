package com.github.damontecres.wholphin.ui.detail.discover

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.model.RelatedVideo
import com.github.damontecres.wholphin.api.seerr.model.RequestPostRequest
import com.github.damontecres.wholphin.api.seerr.model.RequestRequestIdPutRequest
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.RemoteTrailer
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.successValue
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber

@HiltViewModel(assistedFactory = DiscoverSeriesViewModel.Factory::class)
class DiscoverSeriesViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        @param:ApplicationContext private val context: Context,
        private val navigationManager: NavigationManager,
        private val backdropService: BackdropService,
        val serverRepository: ServerRepository,
        val seerrService: SeerrService,
        private val seerrServerRepository: SeerrServerRepository,
        @Assisted val item: DiscoverItem,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(item: DiscoverItem): DiscoverSeriesViewModel
        }

        private val _state = MutableStateFlow(DiscoverSeriesState())
        val state: StateFlow<DiscoverSeriesState> = _state

        val userConfig = seerrServerRepository.current.map { it?.config }
        val request4kEnabled = seerrServerRepository.current.map { it?.request4kTvEnabled ?: false }

        init {
            init()
        }

        private fun fetchAndSetItem(): Deferred<TvDetails?> =
            viewModelScope.async(WholphinDispatchers.IO) {
                try {
                    val tv = seerrService.api.tvApi.tvTvIdGet(tvId = item.id)
                    _state.update { it.copy(tvSeries = DataLoadingState.Success(tv)) }
                    tv
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error updating tv details")
                    null
                }
            }

        fun init(): Job =
            viewModelScope.launchIO {
                Timber.v("Init for tv %s", item.id)
                try {
                    val tv = seerrService.api.tvApi.tvTvIdGet(tvId = item.id)
                    _state.update { it.copy(tvSeries = DataLoadingState.Success(tv)) }
                    val discoveredItem = seerrService.createDiscoverItem(tv)
                    backdropService.submit(discoveredItem)

                    updateSeasonStatus(tv)
                    updateCanCancel()

                    viewModelScope.launchIO {
                        val result = seerrService.api.tvApi.tvTvIdRatingsGet(tvId = item.id)
                        _state.update { it.copy(rating = DiscoverRating(result)) }
                    }
                    if (state.value.similar.isEmpty()) {
                        viewModelScope.launchIO {
                            val result =
                                seerrService.api.tvApi
                                    .tvTvIdSimilarGet(tvId = item.id, page = 1)
                                    .results
                                    ?.map { seerrService.createDiscoverItem(it) }
                                    .orEmpty()
                            _state.update { it.copy(similar = result) }
                        }
                        viewModelScope.launchIO {
                            val result =
                                seerrService.api.tvApi
                                    .tvTvIdRecommendationsGet(tvId = item.id, page = 1)
                                    .results
                                    ?.map { seerrService.createDiscoverItem(it) }
                                    .orEmpty()
                            _state.update { it.copy(recommended = result) }
                        }
                    }
                    val people =
                        tv.credits
                            ?.cast
                            ?.map { seerrService.createDiscoverItem(it) }
                            .orEmpty() +
                            tv.credits
                                ?.crew
                                ?.map { seerrService.createDiscoverItem(it) }
                                .orEmpty()
                    _state.update { it.copy(people = people) }

                    val trailers =
                        tv.relatedVideos
                            ?.filter { it.type == RelatedVideo.Type.TRAILER }
                            ?.filter { it.name.isNotNullOrBlank() && it.url.isNotNullOrBlank() }
                            ?.map {
                                RemoteTrailer(it.name!!, it.url!!, it.site)
                            }.orEmpty()
                    _state.update { it.copy(trailers = trailers) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error getting tv details")
                    _state.update { it.copy(tvSeries = DataLoadingState.Error(ex)) }
                }
            }

        fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }

        private fun updateSeasonStatus(tv: TvDetails) {
            val seasonStatus = mutableMapOf<Int, SeerrAvailability>()
            tv.seasons?.forEach {
                it.seasonNumber?.let {
                    seasonStatus[it] = SeerrAvailability.UNKNOWN
                }
            }
            val tvStatus =
                SeerrAvailability.from(tv.mediaInfo?.status) ?: SeerrAvailability.UNKNOWN
            tv.mediaInfo
                ?.requests
                ?.forEach {
                    it.seasons?.mapNotNull { season ->
                        season.seasonNumber?.let {
                            val current = seasonStatus[season.seasonNumber]
                            val new =
                                SeerrAvailability
                                    .from(season.status)
                                    ?.takeIf { it != SeerrAvailability.UNKNOWN } ?: tvStatus
                            if (current == null || new.status > current.status) {
                                seasonStatus[season.seasonNumber] = new
                            }
                        }
                    }
                }
            Timber.v("seasonStatus=%s", seasonStatus)
            val requestSeasons =
                seasonStatus.mapNotNull { (seasonNumber, availability) ->
                    tv.seasons?.firstOrNull { it.seasonNumber == seasonNumber }?.let {
                        RequestSeason(it, availability)
                    }
                }
            _state.update { it.copy(seasons = requestSeasons) }
        }

        private suspend fun updateCanCancel() {
            val user = userConfig.firstOrNull()
            val canCancel =
                canUserCancelRequest(
                    user,
                    state.value.tvSeries.successValue
                        ?.mediaInfo
                        ?.requests,
                )
            _state.update { it.copy(canCancelRequest = canCancel) }
        }

        fun requestOnClick() {
            viewModelScope.launchIO {
                try {
                    val data = seerrService.getProfilesAndFolders(SeerrItemType.MOVIE)
                    _state.update {
                        it.copy(
                            profileLoading = LoadingState.Success,
                            requestData = data,
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error getting profiles & folders")
                    showToast(context, "Error getting profiles & folders: ${ex.localizedMessage}")
                    _state.update {
                        it.copy(
                            profileLoading = LoadingState.Success,
                        )
                    }
                }
            }
        }

        fun request(request: TvRequest) {
            viewModelScope.launchIO {
                state.value.tvSeries.successValue?.let { tv ->
                    val currentRequest =
                        tv.mediaInfo?.requests?.firstOrNull {
                            it.requestedBy?.id ==
                                seerrServerRepository.currentUserId.first()
                        }
                    try {
                        if (currentRequest != null) {
                            Timber.v("User has pending request, will update")
                            seerrService.api.requestApi.requestRequestIdPut(
                                requestId = currentRequest.id.toString(),
                                requestRequestIdPutRequest =
                                    RequestRequestIdPutRequest(
                                        is4k = request.is4k,
                                        mediaType = RequestRequestIdPutRequest.MediaType.TV,
                                        seasons = request.seasons,
                                        profileid = request.profileId,
                                        rootFolder = request.folder,
                                    ),
                            )
                        } else {
                            Timber.v("New request for %s seasons", request.seasons.size)
                            seerrService.api.requestApi.requestPost(
                                RequestPostRequest(
                                    is4k = request.is4k,
                                    mediaId = request.tvId,
                                    mediaType = RequestPostRequest.MediaType.TV,
                                    seasons = request.seasons,
                                    profileid = request.profileId,
                                    rootFolder = request.folder,
                                ),
                            )
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error requesting %s", request.tvId)
                        showToast(context, "An error occurred")
                    }

                    fetchAndSetItem().await()?.let {
                        updateSeasonStatus(it)
                        updateCanCancel()
                    }
                }
            }
        }

        fun cancelRequest(id: Int) {
            viewModelScope.launchIO {
                state.value.tvSeries.successValue?.mediaInfo?.requests?.firstOrNull()?.let {
                    // TODO handle multiple requests? Or just delete self's request?
                    try {
                        seerrService.api.requestApi.requestRequestIdDelete(it.id.toString())
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error requesting %s", id)
                        showToast(context, "An error occurred")
                    }

                    fetchAndSetItem().await()?.let {
                        updateSeasonStatus(it)
                        updateCanCancel()
                    }
                }
            }
        }
    }

data class DiscoverSeriesState(
    val tvSeries: DataLoadingState<TvDetails> = DataLoadingState.Pending,
    val rating: DiscoverRating? = null,
    val seasons: List<RequestSeason> = emptyList(),
    val trailers: List<Trailer> = emptyList(),
    val people: List<DiscoverItem> = emptyList(),
    val similar: List<DiscoverItem> = emptyList(),
    val recommended: List<DiscoverItem> = emptyList(),
    val canCancelRequest: Boolean = false,
    val profileLoading: LoadingState = LoadingState.Pending,
    val requestData: SeerrRequestData = SeerrRequestData(),
)
