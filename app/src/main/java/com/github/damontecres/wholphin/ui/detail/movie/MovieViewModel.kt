package com.github.damontecres.wholphin.ui.detail.movie

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.LocalTrailer
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.data.model.RemoteTrailer
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.preferences.ThemeSongVolume
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.detail.LoadingItemViewModel
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.NavigationManager
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.ThemeSongPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MovieViewModel
    @Inject
    constructor(
        api: ApiClient,
        private val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val itemPlaybackRepository: ItemPlaybackRepository,
        private val themeSongPlayer: ThemeSongPlayer,
    ) : LoadingItemViewModel(api) {
        private lateinit var itemId: UUID

        val trailers = MutableLiveData<List<Trailer>>(listOf())
        val people = MutableLiveData<List<Person>>(listOf())
        val chapters = MutableLiveData<List<Chapter>>(listOf())
        val similar = MutableLiveData<List<BaseItem>>()
        val chosenStreams = MutableLiveData<ChosenStreams?>(null)

        fun init(itemId: UUID): Job? {
            this.itemId = itemId
            return viewModelScope.launch(
                Dispatchers.IO +
                    LoadingExceptionHandler(
                        loading,
                        "Error fetching movie",
                    ),
            ) {
                fetchAndSetItem(itemId)
                item.value?.let { item ->
                    val result = itemPlaybackRepository.getSelectedTracks(item.id, item)
                    withContext(Dispatchers.Main) {
                        chosenStreams.value = result
                        loading.value = LoadingState.Success
                    }
                    viewModelScope.launchIO {
                        val remoteTrailers =
                            item.data.remoteTrailers
                                ?.mapNotNull { t ->
                                    t.url?.let { url ->
                                        val name =
                                            t.name
                                                // TODO would be nice to clean up the trailer name
//                                                ?.replace(item.name ?: "", "")
//                                                ?.removePrefix(" - ")
                                                ?: "Trailer"
                                        RemoteTrailer(name, url)
                                    }
                                }.orEmpty()
                                .sortedBy { it.name }
                        val localTrailerCount = item.data.localTrailerCount ?: 0
                        val localTrailers =
                            if (localTrailerCount > 0) {
                                api.userLibraryApi.getLocalTrailers(itemId).content.map {
                                    LocalTrailer(BaseItem.Companion.from(it, api))
                                }
                            } else {
                                listOf()
                            }
                        withContext(Dispatchers.Main) {
                            this@MovieViewModel.trailers.value = localTrailers + remoteTrailers
                        }
                    }
                    withContext(Dispatchers.Main) {
                        people.value =
                            item.data.people
                                ?.letNotEmpty { people ->
                                    people.map { Person.fromDto(it, api) }
                                }.orEmpty()
                        chapters.value = Chapter.fromDto(item.data, api)
                    }
                    if (!similar.isInitialized) {
                        val similar =
                            api.libraryApi
                                .getSimilarItems(
                                    GetSimilarItemsRequest(
                                        userId = serverRepository.currentUser?.id,
                                        itemId = itemId,
                                        fields = SlimItemFields,
                                        limit = 25,
                                    ),
                                ).content.items
                                .map { BaseItem.Companion.from(it, api) }
                        this@MovieViewModel.similar.setValueOnMain(similar)
                    }
                }
            }
        }

        fun setWatched(played: Boolean) =
            viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                if (played) {
                    api.playStateApi.markPlayedItem(itemId)
                } else {
                    api.playStateApi.markUnplayedItem(itemId)
                }
                fetchAndSetItem(itemId)
            }

        fun setFavorite(favorite: Boolean) =
            viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                if (favorite) {
                    api.userLibraryApi.markFavoriteItem(itemId)
                } else {
                    api.userLibraryApi.unmarkFavoriteItem(itemId)
                }
                fetchAndSetItem(itemId)
            }

        fun savePlayVersion(
            item: BaseItem,
            sourceId: UUID,
        ) {
            viewModelScope.launchIO {
                val result = itemPlaybackRepository.savePlayVersion(item.id, sourceId)
                val chosen =
                    result?.let {
                        itemPlaybackRepository.getChosenItemFromPlayback(item, result)
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
                val result =
                    itemPlaybackRepository.saveTrackSelection(
                        item = item,
                        itemPlayback = itemPlayback,
                        trackIndex = trackIndex,
                        type = type,
                    )
                val chosen =
                    result?.let {
                        itemPlaybackRepository.getChosenItemFromPlayback(item, result)
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
    }
