package com.github.damontecres.wholphin.ui.detail.discover

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.api.seerr.model.RelatedVideo
import com.github.damontecres.wholphin.api.seerr.model.RequestPostRequest
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.RemoteTrailer
import com.github.damontecres.wholphin.data.model.SeerrPermission
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.data.model.hasPermission
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.SeerrUserConfig
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.successValue
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel(assistedFactory = DiscoverMovieViewModel.Factory::class)
class DiscoverMovieViewModel
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
            fun create(item: DiscoverItem): DiscoverMovieViewModel
        }

        private val _state = MutableStateFlow(DiscoverMovieState())
        val state: StateFlow<DiscoverMovieState> = _state

        val userConfig = seerrServerRepository.current.map { it?.config }
        val request4kEnabled =
            seerrServerRepository.current.map { it?.request4kMovieEnabled ?: false }

        init {
            init()
        }

        private fun fetchAndSetItem(): Deferred<MovieDetails?> =
            viewModelScope.async(WholphinDispatchers.IO) {
                try {
                    val movie = seerrService.api.moviesApi.movieMovieIdGet(movieId = item.id)
                    _state.update { it.copy(movie = DataLoadingState.Success(movie)) }
                    movie
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error updating movie details")
                    null
                }
            }

        fun init(): Job =
            viewModelScope.launchIO {
                Timber.v("Init for movie %s", item.id)
                try {
                    val movie = seerrService.api.moviesApi.movieMovieIdGet(movieId = item.id)
                    _state.update { it.copy(movie = DataLoadingState.Success(movie)) }
                    val discoveredItem = seerrService.createDiscoverItem(movie)
                    backdropService.submit(discoveredItem)

                    updateCanCancel()

                    viewModelScope.launchIO {
                        val result =
                            seerrService.api.moviesApi.movieMovieIdRatingsGet(movieId = item.id)
                        _state.update { it.copy(rating = DiscoverRating(result)) }
                    }
                    if (state.value.similar.isEmpty()) {
                        viewModelScope.launchIO {
                            val result =
                                seerrService.api.moviesApi
                                    .movieMovieIdSimilarGet(movieId = item.id, page = 1)
                                    .results
                                    ?.map { seerrService.createDiscoverItem(it) }
                                    .orEmpty()
                            _state.update { it.copy(similar = result) }
                        }
                        viewModelScope.launchIO {
                            val result =
                                seerrService.api.moviesApi
                                    .movieMovieIdRecommendationsGet(movieId = item.id, page = 1)
                                    .results
                                    ?.map { seerrService.createDiscoverItem(it) }
                                    .orEmpty()
                            _state.update { it.copy(recommended = result) }
                        }
                    }
                    val people =
                        movie.credits
                            ?.cast
                            ?.map { seerrService.createDiscoverItem(it) }
                            .orEmpty() +
                            movie.credits
                                ?.crew
                                ?.map { seerrService.createDiscoverItem(it) }
                                .orEmpty()
                    _state.update { it.copy(people = people) }
                    val trailers =
                        movie.relatedVideos
                            ?.filter { it.type == RelatedVideo.Type.TRAILER }
                            ?.filter { it.name.isNotNullOrBlank() && it.url.isNotNullOrBlank() }
                            ?.map {
                                RemoteTrailer(it.name!!, it.url!!, it.site)
                            }.orEmpty()
                    _state.update { it.copy(trailers = trailers) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error updating movie details")
                    _state.update { it.copy(movie = DataLoadingState.Error(ex)) }
                }
            }

        private suspend fun updateCanCancel() {
            val user = userConfig.firstOrNull()
            val canCancel =
                canUserCancelRequest(
                    user,
                    state.value.movie.successValue
                        ?.mediaInfo
                        ?.requests,
                )
            _state.update { it.copy(canCancelRequest = canCancel) }
        }

        fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }

        fun request(
            id: Int,
            is4k: Boolean,
        ) {
            viewModelScope.launchIO {
                try {
                    seerrService.api.requestApi.requestPost(
                        RequestPostRequest(
                            is4k = is4k,
                            mediaId = id,
                            mediaType = RequestPostRequest.MediaType.MOVIE,
                        ),
                    )
                } catch (ex: kotlinx.coroutines.CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error requesting %s", id)
                    showToast(context, "An error occurred")
                }
                fetchAndSetItem().await()
                updateCanCancel()
            }
        }

        fun cancelRequest(id: Int) {
            viewModelScope.launchIO {
                state.value.movie.successValue?.mediaInfo?.requests?.firstOrNull()?.let {
                    // TODO handle multiple requests? Or just delete self's request?
                    try {
                        seerrService.api.requestApi.requestRequestIdDelete(it.id.toString())
                    } catch (ex: kotlinx.coroutines.CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error requesting %s", id)
                        showToast(context, "An error occurred")
                    }
                    fetchAndSetItem().await()
                    updateCanCancel()
                }
            }
        }
    }

fun canUserCancelRequest(
    user: SeerrUserConfig?,
    requests: List<MediaRequest>?,
) = (user.hasPermission(SeerrPermission.MANAGE_REQUESTS) && requests?.isNotEmpty() == true) ||
    (
        // User requested this
        user.hasPermission(SeerrPermission.REQUEST) &&
            requests?.any { it.requestedBy?.id == user?.id } == true
    )

data class DiscoverMovieState(
    val movie: DataLoadingState<MovieDetails> = DataLoadingState.Pending,
    val rating: DiscoverRating? = null,
    val seasons: List<RequestSeason> = emptyList(),
    val trailers: List<Trailer> = emptyList(),
    val people: List<DiscoverItem> = emptyList(),
    val similar: List<DiscoverItem> = emptyList(),
    val recommended: List<DiscoverItem> = emptyList(),
    val canCancelRequest: Boolean = false,
)
