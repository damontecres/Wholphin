package com.github.damontecres.wholphin.ui.detail.movie

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.preferences.ThemeSongVolume
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.ExtrasService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PeopleFavorites
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.ThemeSongPlayer
import com.github.damontecres.wholphin.services.TrailerService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.util.ExceptionHandler
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import java.util.UUID

@HiltViewModel(assistedFactory = MovieViewModel.Factory::class)
class MovieViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        private val seerrService: SeerrService,
        @param:ApplicationContext private val context: Context,
        private val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val itemPlaybackRepository: ItemPlaybackRepository,
        val streamChoiceService: StreamChoiceService,
        val mediaReportService: MediaReportService,
        private val themeSongPlayer: ThemeSongPlayer,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val peopleFavorites: PeopleFavorites,
        private val trailerService: TrailerService,
        private val extrasService: ExtrasService,
        private val userPreferencesService: UserPreferencesService,
        private val backdropService: BackdropService,
        @Assisted val itemId: UUID,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(itemId: UUID): MovieViewModel
        }

        val loading = MutableLiveData<LoadingState>(LoadingState.Pending)
        val item = MutableLiveData<BaseItem?>(null)
        val trailers = MutableLiveData<List<Trailer>>(listOf())
        val people = MutableLiveData<List<Person>>(listOf())
        val chapters = MutableLiveData<List<Chapter>>(listOf())
        val extras = MutableLiveData<List<ExtrasItem>>(listOf())
        val similar = MutableLiveData<List<BaseItem>>()
        val chosenStreams = MutableLiveData<ChosenStreams?>(null)
        val discovered = MutableStateFlow<List<DiscoverItem>>(listOf())

        init {
            init()
        }

        private fun fetchAndSetItem(): Deferred<BaseItem> =
            viewModelScope.async(
                Dispatchers.IO +
                    LoadingExceptionHandler(
                        loading,
                        "Error fetching movie",
                    ),
            ) {
                val item =
                    api.userLibraryApi.getItem(itemId).content.let {
                        BaseItem.from(it, api)
                    }
                this@MovieViewModel.item.setValueOnMain(item)
                item
            }

        fun init(): Job =
            viewModelScope.launch(
                Dispatchers.IO +
                    LoadingExceptionHandler(
                        loading,
                        "Error fetching movie",
                    ),
            ) {
                val item = fetchAndSetItem().await()
                val result =
                    itemPlaybackRepository.getSelectedTracks(
                        item.id,
                        item,
                        userPreferencesService.getCurrent(),
                    )
                val remoteTrailers = trailerService.getRemoteTrailers(item)
                withContext(Dispatchers.Main) {
                    this@MovieViewModel.item.value = item
                    chosenStreams.value = result
                    this@MovieViewModel.trailers.value = remoteTrailers
                    loading.value = LoadingState.Success
                    backdropService.submit(item)
                }
                viewModelScope.launchIO {
                    trailerService.getLocalTrailers(item).letNotEmpty { localTrailers ->
                        withContext(Dispatchers.Main) {
                            this@MovieViewModel.trailers.value = localTrailers + remoteTrailers
                        }
                    }
                }
                viewModelScope.launchIO {
                    val people = peopleFavorites.getPeopleFor(item)
                    this@MovieViewModel.people.setValueOnMain(people)
                }
                viewModelScope.launchIO {
                    val extras = extrasService.getExtras(item.id)
                    this@MovieViewModel.extras.setValueOnMain(extras)
                }
                viewModelScope.launchIO {
                    val results = seerrService.similar(item).orEmpty()
                    discovered.update { results }
                }

                withContext(Dispatchers.Main) {
                    chapters.value = Chapter.fromDto(item.data, api)
                }
                if (!similar.isInitialized) {
                    val similar =
                        api.libraryApi
                            .getSimilarItems(
                                GetSimilarItemsRequest(
                                    userId = serverRepository.currentUser.value?.id,
                                    itemId = itemId,
                                    fields = SlimItemFields,
                                    limit = 25,
                                ),
                            ).content.items
                            .map { BaseItem.Companion.from(it, api) }
                    this@MovieViewModel.similar.setValueOnMain(similar)
                }
            }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            fetchAndSetItem()
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            val item = item.value
            fetchAndSetItem()
            if (item != null && itemId != item.id) {
                viewModelScope.launchIO {
                    val people = peopleFavorites.getPeopleFor(item)
                    this@MovieViewModel.people.setValueOnMain(people)
                }
            }
        }

        fun savePlayVersion(
            item: BaseItem,
            sourceId: UUID,
        ) {
            viewModelScope.launchIO {
                val prefs = userPreferencesService.getCurrent()
                val plc = streamChoiceService.getPlaybackLanguageChoice(item.data)
                val result = itemPlaybackRepository.savePlayVersion(item.id, sourceId)
                val chosen =
                    result?.let {
                        itemPlaybackRepository.getChosenItemFromPlayback(item, result, plc, prefs)
                    }
                withContext(Dispatchers.Main) {
                    chosenStreams.value = chosen
                }
            }
        }

        fun saveTrackSelection(
            item: BaseItem,
            itemPlayback: ItemPlayback?,
            trackIndex: Int,
            type: MediaStreamType,
        ) {
            viewModelScope.launchIO {
                val prefs = userPreferencesService.getCurrent()
                val plc = streamChoiceService.getPlaybackLanguageChoice(item.data)
                val result =
                    itemPlaybackRepository.saveTrackSelection(
                        item = item,
                        itemPlayback = itemPlayback,
                        trackIndex = trackIndex,
                        type = type,
                    )
                val chosen =
                    result?.let {
                        itemPlaybackRepository.getChosenItemFromPlayback(item, result, plc, prefs)
                    }
                withContext(Dispatchers.Main) {
                    chosenStreams.value = chosen
                }
            }
        }

        fun maybePlayThemeSong(
            seriesId: UUID,
            playThemeSongs: ThemeSongVolume,
        ) {
            viewModelScope.launchIO {
                themeSongPlayer.playThemeFor(seriesId, playThemeSongs)
                addCloseable {
                    themeSongPlayer.stop()
                }
            }
        }

        fun release() {
            themeSongPlayer.stop()
        }

        fun navigateTo(destination: Destination) {
            release()
            navigationManager.navigateTo(destination)
        }

        fun clearChosenStreams(chosenStreams: ChosenStreams?) {
            viewModelScope.launchIO {
                itemPlaybackRepository.deleteChosenStreams(chosenStreams)
                item.value?.let { item ->
                    val result =
                        itemPlaybackRepository.getSelectedTracks(
                            itemId,
                            item,
                            userPreferencesService.getCurrent(),
                        )
                    this@MovieViewModel.chosenStreams.setValueOnMain(result)
                }
            }
        }
    }
