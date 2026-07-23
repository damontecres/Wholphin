package com.github.damontecres.wholphin.ui.detail.episode

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.StrmFileHandler
import com.github.damontecres.wholphin.services.ThemeSongPlayer
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = EpisodeViewModel.Factory::class)
class EpisodeViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        @param:ApplicationContext private val context: Context,
        private val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val itemPlaybackRepository: ItemPlaybackRepository,
        val streamChoiceService: StreamChoiceService,
        val mediaReportService: MediaReportService,
        private val themeSongPlayer: ThemeSongPlayer,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val userPreferencesService: UserPreferencesService,
        private val backdropService: BackdropService,
        private val mediaManagementService: MediaManagementService,
        private val strmFileHandler: StrmFileHandler,
        @Assisted val itemId: UUID,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(itemId: UUID): EpisodeViewModel
        }

        private val _state = MutableStateFlow(EpisodeState())
        val state: StateFlow<EpisodeState> = _state

        val canDelete = MutableStateFlow(false)

        init {
//            init()
            viewModelScope.launchDefault {
                mediaManagementService.collectCanDelete(
                    state.map { (it.episode as? DataLoadingState.Success<BaseItem?>)?.data },
                ) { canDelete ->
                    this@EpisodeViewModel.canDelete.update { canDelete }
                }
            }
        }

        private fun fetchAndSetItem() {
            viewModelScope.launchIO {
                try {
                    val item =
                        api.userLibraryApi.getItem(itemId).content.let {
                            BaseItem.from(it, api)
                        }
                    _state.update { it.copy(episode = DataLoadingState.Success(item)) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error getting episode %s", itemId)
                    showToast(context, "Error updating episode")
                }
            }
        }

        fun init(): Job =
            viewModelScope.launchIO {
                try {
                    val prefs = userPreferencesService.getCurrent()
                    val item =
                        api.userLibraryApi.getItem(itemId).content.let {
                            BaseItem(it)
                        }
                    val chosenStreams =
                        itemPlaybackRepository.getSelectedTracks(item.id, item, prefs)
                    _state.update {
                        it.copy(
                            episode = DataLoadingState.Success(item),
                            chosenStreams = chosenStreams,
                        )
                    }
                    backdropService.submit(item)
                    viewModelScope.launchIO {
                        try {
                            if (StrmFileHandler.shouldResolveStrm(item)) {
                                _state.update { it.copy(strmLoading = true) }
                                val result = strmFileHandler.resolveStrm(item)
                                if (result != null) {
                                    Timber.d("Got updated item")
                                    val chosenStreams =
                                        itemPlaybackRepository.getSelectedTracks(
                                            itemId,
                                            result,
                                            userPreferencesService.getCurrent(),
                                        )
                                    _state.update {
                                        it.copy(
                                            episode = DataLoadingState.Success(result),
                                            chosenStreams = chosenStreams,
                                        )
                                    }
                                }
                            }
                        } catch (ex: Exception) {
                            Timber.e(ex, "Error checking strm file for %s", item.id)
                        } finally {
                            _state.update { it.copy(strmLoading = false) }
                        }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error getting episode %s", itemId)
                    _state.update { it.copy(episode = DataLoadingState.Error(ex)) }
                }
            }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            fetchAndSetItem()
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            fetchAndSetItem()
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
                _state.update { it.copy(chosenStreams = chosen) }
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
                _state.update { it.copy(chosenStreams = chosen) }
            }
        }

        fun maybePlayThemeSong(seriesId: UUID) {
            viewModelScope.launchIO {
                themeSongPlayer.playThemeFor(seriesId)
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
                state.value.episode.let { item ->
                    if (item is DataLoadingState.Success<BaseItem>) {
                        val result =
                            itemPlaybackRepository.getSelectedTracks(
                                itemId,
                                item.data,
                                userPreferencesService.getCurrent(),
                            )
                        _state.update { it.copy(chosenStreams = result) }
                    }
                }
            }
        }

        fun deleteItem(item: BaseItem) {
            deleteItem(context, mediaManagementService, item) {
                navigationManager.goBack()
            }
        }
    }

data class EpisodeState(
    val episode: DataLoadingState<BaseItem> = DataLoadingState.Pending,
    val chosenStreams: ChosenStreams? = null,
    val strmLoading: Boolean = false,
)
