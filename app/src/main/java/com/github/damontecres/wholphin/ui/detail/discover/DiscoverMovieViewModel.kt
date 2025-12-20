package com.github.damontecres.wholphin.ui.detail.discover

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.api.seerr.model.RelatedVideo
import com.github.damontecres.wholphin.api.seerr.model.RequestPostRequest
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.data.model.RemoteTrailer
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.PersonKind

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
        val people = MutableLiveData<List<Person>>(listOf())
        val similar = MutableLiveData<List<DiscoverItem>>(listOf())
        val recommended = MutableLiveData<List<DiscoverItem>>(listOf())

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
                val movie = fetchAndSetItem().await()
                // TODO backdrop url = "https://image.tmdb.org/t/p/w1920_and_h800_multi_faces/$it"
//                backdropService.submit(movie)

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
                                .movieMovieIdSimilarGet(movieId = item.id, page = 2)
                                .results
                                ?.map(::DiscoverItem)
                                .orEmpty()
                        similar.setValueOnMain(result)
                    }
                    viewModelScope.launchIO {
                        val result =
                            seerrService.api.moviesApi
                                .movieMovieIdRecommendationsGet(movieId = item.id, page = 2)
                                .results
                                ?.map(::DiscoverItem)
                                .orEmpty()
                        similar.setValueOnMain(result)
                    }
                }
                val people =
                    movie.credits
                        ?.cast
                        ?.map {
                            Person(
                                id = UUID.randomUUID(),
                                name = it.name,
                                role = it.character,
                                type = PersonKind.UNKNOWN,
                                imageUrl = "https://image.tmdb.org/t/p/w600_and_h900_bestv2${it.profilePath}",
                                favorite = false,
                            )
                        }.orEmpty() +
                        movie.credits
                            ?.crew
                            ?.map {
                                Person(
                                    id = UUID.randomUUID(),
                                    name = it.name,
                                    role = it.job,
                                    type = PersonKind.UNKNOWN,
                                    imageUrl = "https://image.tmdb.org/t/p/w600_and_h900_bestv2${it.profilePath}",
                                    favorite = false,
                                )
                            }.orEmpty()
                this@DiscoverMovieViewModel.people.setValueOnMain(people)
                val trailers =
                    movie.relatedVideos
                        ?.filter { it.type == RelatedVideo.Type.TRAILER }
                        ?.filter { it.name.isNotNullOrBlank() && it.url.isNotNullOrBlank() }
                        ?.map {
                            RemoteTrailer(it.name!!, it.url!!)
                        }.orEmpty()
                this@DiscoverMovieViewModel.trailers.setValueOnMain(trailers)
            }

        fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }

        fun request(id: Int) {
            viewModelScope.launchIO {
                val request =
                    seerrService.api.requestApi.requestPost(
                        RequestPostRequest(
                            is4k = false,
                            mediaid = id,
                            mediaType = RequestPostRequest.MediaType.MOVIE,
                        ),
                    )
                fetchAndSetItem().await()
            }
        }

        fun cancelRequest(id: Int) {
            viewModelScope.launchIO {
                movie.value?.mediaInfo?.requests?.firstOrNull()?.let {
                    // TODO handle multiple requests? Or just delete self's request?
                    seerrService.api.requestApi.requestRequestIdDelete(it.id.toString())
                    fetchAndSetItem().await()
                }
            }
        }
    }
