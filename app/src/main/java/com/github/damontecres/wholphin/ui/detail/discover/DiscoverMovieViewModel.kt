package com.github.damontecres.wholphin.ui.detail.discover

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
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
        val chapters = MutableLiveData<List<Chapter>>(listOf())
        val extras = MutableLiveData<List<ExtrasItem>>(listOf())
        val similar = MutableLiveData<List<BaseItem>>()
        val chosenStreams = MutableLiveData<ChosenStreams?>(null)

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
                withContext(Dispatchers.Main) {
                    loading.value = LoadingState.Success
                }
                viewModelScope.launchIO {
                    val result = seerrService.api.moviesApi.movieMovieIdRatingsGet(movieId = item.id)
                    rating.setValueOnMain(DiscoverRating(result))
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
                if (!similar.isInitialized) {
//                    val similar =
//                        api.libraryApi
//                            .getSimilarItems(
//                                GetSimilarItemsRequest(
//                                    userId = serverRepository.currentUser.value?.id,
//                                    itemId = itemId,
//                                    fields = SlimItemFields,
//                                    limit = 25,
//                                ),
//                            ).content.items
//                            .map { BaseItem.Companion.from(it, api) }
//                    this@DiscoverMovieViewModel.similar.setValueOnMain(similar)
                }
            }

        fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }
    }
