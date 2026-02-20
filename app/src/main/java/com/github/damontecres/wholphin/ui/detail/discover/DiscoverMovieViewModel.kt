package com.github.damontecres.wholphin.ui.detail.discover

import android.content.Context
import androidx.lifecycle.MutableLiveData
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
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber

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

        val loading = MutableLiveData<LoadingState>(LoadingState.Pending)
        val movie = MutableLiveData<MovieDetails?>(null)
        val rating = MutableLiveData<DiscoverRating?>(null)

        val trailers = MutableLiveData<List<Trailer>>(listOf())
        val people = MutableLiveData<List<DiscoverItem>>(listOf())
        val similar = MutableLiveData<List<DiscoverItem>>()
        val recommended = MutableLiveData<List<DiscoverItem>>()
        val canCancelRequest = MutableStateFlow(false)

        val userConfig = seerrServerRepository.current.map { it?.config }
        val request4kEnabled = seerrServerRepository.current.map { it?.request4kMovieEnabled ?: false }

        init {
            init()
        }

        private fun fetchAndSetItem(): Deferred<MovieDetails> =
            viewModelScope.async(
                Dispatchers.IO +
                    LoadingExceptionHandler(
                        loading,
                        "Error fetching movie",
                    ),
            ) {
                val movie = seerrService.api.moviesApi.movieMovieIdGet(movieId = item.id)
                this@DiscoverMovieViewModel.movie.setValueOnMain(movie)
                movie
            }

        fun init(): Job =
            viewModelScope.launch(
                Dispatchers.IO +
                    LoadingExceptionHandler(
                        loading,
                        "Error fetching movie",
                    ),
            ) {
                Timber.v("Init for movie %s", item.id)
                val movie = fetchAndSetItem().await()
                val discoveredItem = DiscoverItem(movie)
                backdropService.submit(discoveredItem)

                updateCanCancel()

                withContext(Dispatchers.Main) {
                    loading.value = LoadingState.Success
                }
                viewModelScope.launchIO {
                    val result = seerrService.api.moviesApi.movieMovieIdRatingsGet(movieId = item.id)
                    rating.setValueOnMain(DiscoverRating(result))
                }
                if (!similar.isInitialized) {
                    viewModelScope.launchIO {
                        val result =
                            seerrService.api.moviesApi
                                .movieMovieIdSimilarGet(movieId = item.id, page = 1)
                                .results
                                ?.map(::DiscoverItem)
                                .orEmpty()
                        similar.setValueOnMain(result)
                    }
                    viewModelScope.launchIO {
                        val result =
                            seerrService.api.moviesApi
                                .movieMovieIdRecommendationsGet(movieId = item.id, page = 1)
                                .results
                                ?.map(::DiscoverItem)
                                .orEmpty()
                        recommended.setValueOnMain(result)
                    }
                }
                val people =
                    movie.credits
                        ?.cast
                        ?.map(::DiscoverItem)
                        .orEmpty() +
                        movie.credits
                            ?.crew
                            ?.map(::DiscoverItem)
                            .orEmpty()
                this@DiscoverMovieViewModel.people.setValueOnMain(people)
                val trailers =
                    movie.relatedVideos
                        ?.filter { it.type == RelatedVideo.Type.TRAILER }
                        ?.filter { it.name.isNotNullOrBlank() && it.url.isNotNullOrBlank() }
                        ?.map {
                            RemoteTrailer(it.name!!, it.url!!, it.site)
                        }.orEmpty()
                this@DiscoverMovieViewModel.trailers.setValueOnMain(trailers)
            }

        private suspend fun updateCanCancel() {
            val user = userConfig.firstOrNull()
            val canCancel = canUserCancelRequest(user, movie.value?.mediaInfo?.requests)
            canCancelRequest.update { canCancel }
        }

        fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }

        fun request(
            id: Int,
            is4k: Boolean,
        ) {
            viewModelScope.launchIO {
                val request =
                    seerrService.api.requestApi.requestPost(
                        RequestPostRequest(
                            is4k = is4k,
                            mediaId = id,
                            mediaType = RequestPostRequest.MediaType.MOVIE,
                        ),
                    )
                fetchAndSetItem().await()
                updateCanCancel()
            }
        }

        fun cancelRequest(id: Int) {
            viewModelScope.launchIO {
                movie.value?.mediaInfo?.requests?.firstOrNull()?.let {
                    // TODO handle multiple requests? Or just delete self's request?
                    seerrService.api.requestApi.requestRequestIdDelete(it.id.toString())
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
