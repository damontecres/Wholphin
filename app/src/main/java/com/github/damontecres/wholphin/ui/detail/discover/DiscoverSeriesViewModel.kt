package com.github.damontecres.wholphin.ui.detail.discover

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.api.seerr.model.RelatedVideo
import com.github.damontecres.wholphin.api.seerr.model.RequestPostRequest
import com.github.damontecres.wholphin.api.seerr.model.RequestRequestIdPutRequest
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.RemoteTrailer
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchDefault
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
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
        val request4kEnabled =
            seerrServerRepository.current
                .map { it?.request4kTvEnabled ?: false }
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
                        val rating =
                            getDiscoverRating(item.id) {
                                DiscoverRating(seerrService.api.tvApi.tvTvIdRatingsGet(tvId = item.id))
                            }
                        _state.update { it.copy(rating = rating) }
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

        fun goTo(
            mediaInfo: MediaInfo?,
            type: BaseItemKind,
        ) {
            viewModelScope.launchDefault {
                goToButtonDiscover(mediaInfo, type, context, navigationManager)
            }
        }

        private suspend fun updateSeasonStatus(tv: TvDetails) {
            if (request4kEnabled.first()) {
                updateSeasonStatus(tv, true)
            }
            updateSeasonStatus(tv, false)
        }

        private suspend fun updateSeasonStatus(
            tv: TvDetails,
            is4k: Boolean,
        ) {
            val currentUserId = seerrServerRepository.currentUserId.first()
            val seasonStatus = mutableMapOf<Int, RequestStatus>()
            val seasonAvailability = mutableMapOf<Int, SeerrAvailability>()
            val editable = mutableMapOf<Int, Boolean>()
            tv.seasons?.forEach {
                it.seasonNumber?.let { seasonNumber ->
                    seasonStatus[seasonNumber] = RequestStatus.UNKNOWN
                    val status = if (is4k) it.status4k else it.status
                    val availability =
                        SeerrAvailability.from(status) ?: SeerrAvailability.UNKNOWN
                    seasonAvailability[seasonNumber] = availability
                }
            }
            tv.mediaInfo?.seasons?.forEach {
                it.seasonNumber?.let { seasonNumber ->
                    val status = if (is4k) it.status4k else it.status
                    val availability =
                        SeerrAvailability.from(status) ?: SeerrAvailability.UNKNOWN
                    val current =
                        seasonAvailability.getOrDefault(seasonNumber, SeerrAvailability.UNKNOWN)
                    if (availability > current) {
                        seasonAvailability[seasonNumber] = availability
                    }
                }
            }

            tv.mediaInfo
                ?.requests
                ?.filter { it.is4k == is4k }
                ?.forEach { req ->
                    req.seasons?.mapNotNull { season ->
                        season.seasonNumber?.let {
                            val current = seasonStatus[season.seasonNumber]
                            // Not status4k because the request itself is marked as is4k or not
                            val status = season.status
                            val new = RequestStatus.from(status)
                            if (current == null || new.status > current.status) {
                                seasonStatus[season.seasonNumber] = new
                            }
                            editable[season.seasonNumber] =
                                currentUserId == req.requestedBy?.id && req.status == RequestStatus.PENDING.status
                        }
                    }
                }
            Timber.v("seasonAvailability=%s", seasonAvailability)
            Timber.v("seasonStatus=%s", seasonStatus)
            val requestSeasons =
                seasonStatus.mapNotNull { (seasonNumber, status) ->
                    tv.seasons?.firstOrNull { it.seasonNumber == seasonNumber }?.let {
                        val availability =
                            when (status) {
                                RequestStatus.PENDING -> {
                                    SeerrAvailability.PENDING
                                }

                                RequestStatus.APPROVED -> {
                                    SeerrAvailability.PROCESSING
                                }

                                RequestStatus.DECLINED -> {
                                    SeerrAvailability.UNKNOWN
                                }

                                RequestStatus.FAILURE -> {
                                    SeerrAvailability.UNKNOWN
                                }

                                RequestStatus.UNKNOWN,
                                RequestStatus.COMPLETED,
                                -> {
                                    seasonAvailability.getOrDefault(
                                        seasonNumber,
                                        SeerrAvailability.UNKNOWN,
                                    )
                                }
                            }
                        val defaultEditable =
                            availability != SeerrAvailability.AVAILABLE &&
                                availability != SeerrAvailability.PARTIALLY_AVAILABLE &&
                                availability != SeerrAvailability.PROCESSING &&
                                availability != SeerrAvailability.BLOCKLISTED
                        RequestSeason(
                            season = it,
                            status = status,
                            availability = availability,
                            editable = editable.getOrDefault(seasonNumber, defaultEditable),
                        )
                    }
                }
            Timber.v("Got %s seasons, is4k=%s", requestSeasons.size, is4k)
//            requestSeasons.forEach {
//                Timber.v(
//                    "is4k=%s, season %s: availability=%s, status=%s, editable=%s",
//                    is4k,
//                    it.season.seasonNumber,
//                    it.availability,
//                    it.status,
//                    it.editable,
//                )
//            }
            _state.update {
                if (is4k) {
                    it.copy(seasons4k = requestSeasons)
                } else {
                    it.copy(seasons = requestSeasons)
                }
            }
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
                    val data = seerrService.getProfilesAndFolders(SeerrItemType.TV)
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
                    val currentUserId = seerrServerRepository.currentUserId.first()
                    val currentRequest =
                        tv.mediaInfo?.requests?.firstOrNull {
                            it.status == RequestStatus.PENDING.status &&
                                it.requestedBy?.id == currentUserId
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
                                        serverId =
                                            when {
                                                request.profileId == null && request.folder == null -> null
                                                request.is4k -> request.data.server4kId
                                                else -> request.data.serverId
                                            },
                                        profileId = request.profileId,
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
                                    serverId =
                                        when {
                                            request.profileId == null && request.folder == null -> null
                                            request.is4k -> request.data.server4kId
                                            else -> request.data.serverId
                                        },
                                    profileId = request.profileId,
                                    rootFolder = request.folder,
                                    tags = emptyList(),
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
    val seasons4k: List<RequestSeason> = emptyList(),
    val trailers: List<Trailer> = emptyList(),
    val people: List<DiscoverItem> = emptyList(),
    val similar: List<DiscoverItem> = emptyList(),
    val recommended: List<DiscoverItem> = emptyList(),
    val canCancelRequest: Boolean = false,
    val profileLoading: LoadingState = LoadingState.Pending,
    val requestData: SeerrRequestData = SeerrRequestData(),
)
