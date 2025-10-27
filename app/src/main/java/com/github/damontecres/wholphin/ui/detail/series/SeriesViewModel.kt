package com.github.damontecres.wholphin.ui.detail.series

import android.content.Context
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.preferences.ThemeSongVolume
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.detail.ItemViewModel
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.NavigationManager
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetEpisodesRequestHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.ThemeSongPlayer
import com.github.damontecres.wholphin.util.profile.Codec
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SeriesViewModel
    @Inject
    constructor(
        api: ApiClient,
        @param:ApplicationContext val context: Context,
        private val serverRepository: ServerRepository,
        private val navigationManager: NavigationManager,
        private val itemPlaybackRepository: ItemPlaybackRepository,
        private val themeSongPlayer: ThemeSongPlayer,
    ) : ItemViewModel(api) {
        private lateinit var seriesId: UUID
        private lateinit var prefs: UserPreferences
        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val seasons = MutableLiveData<ItemListAndMapping>(ItemListAndMapping.empty())
        val episodes = MutableLiveData<EpisodeList>(EpisodeList.Loading)
        val people = MutableLiveData<List<Person>>(listOf())
        val similar = MutableLiveData<List<BaseItem>>()

        fun init(
            prefs: UserPreferences,
            itemId: UUID,
            potential: BaseItem?,
            season: Int?,
            episode: Int?,
        ) {
            this.seriesId = itemId
            this.prefs = prefs
            viewModelScope.launch(
                LoadingExceptionHandler(
                    loading,
                    "Error loading series $seriesId",
                ) + Dispatchers.IO,
            ) {
                val item = fetchItem(seriesId, potential)
                val seasonsInfo = getSeasons(item)

                // If a particular season was requested, fetch those episodes, otherwise get the first season
                val episodeInfo =
                    (season ?: seasonsInfo.items.firstOrNull()?.indexNumber)
                        ?.let { seasonNum ->
                            loadEpisodesInternal(seasonNum)
                        } ?: EpisodeList.Error("Could not determine season")
                withContext(Dispatchers.Main) {
                    seasons.value = seasonsInfo
                    episodes.value = episodeInfo
                    loading.value = LoadingState.Success
                    people.value =
                        item.data.people
                            ?.letNotEmpty { people ->
                                people.map { Person.fromDto(it, api) }
                            }.orEmpty()
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
                            .map { BaseItem.from(it, api, true) }
                    this@SeriesViewModel.similar.setValueOnMain(similar)
                }
            }
        }

        /**
         * If the series has a theme song & app settings allow, play it
         */
        @OptIn(UnstableApi::class)
        fun maybePlayThemeSong(
            seriesId: UUID,
            playThemeSongs: ThemeSongVolume,
        ) {
            viewModelScope.launch(ExceptionHandler()) {
                val themeSongs = api.libraryApi.getThemeSongs(seriesId).content
                themeSongs.items.firstOrNull()?.let { theme ->
                    theme.mediaSources?.firstOrNull()?.let { source ->
                        val url =
                            api.universalAudioApi.getUniversalAudioStreamUrl(
                                theme.id,
                                container =
                                    listOf(
                                        Codec.Audio.OPUS,
                                        Codec.Audio.MP3,
                                        Codec.Audio.AAC,
                                        Codec.Audio.FLAC,
                                    ),
                            )
                        Timber.Forest.v("Found theme song for series $seriesId")
                        withContext(Dispatchers.Main) {
                            themeSongPlayer.play(playThemeSongs, url)
                            addCloseable {
                                themeSongPlayer.stop()
                            }
                        }
                    }
                }
            }
        }

        fun release() {
            themeSongPlayer.stop()
        }

        private suspend fun getSeasons(series: BaseItem): ItemListAndMapping {
            val request =
                GetItemsRequest(
                    parentId = series.id,
                    recursive = false,
                    includeItemTypes = listOf(BaseItemKind.SEASON),
                    sortBy = listOf(ItemSortBy.INDEX_NUMBER),
                    sortOrder = listOf(SortOrder.ASCENDING),
                    fields =
                        listOf(
                            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                            ItemFields.CHILD_COUNT,
                            ItemFields.SEASON_USER_DATA,
                        ),
                )
            val pager =
                ApiRequestPager(
                    api,
                    request,
                    GetItemsRequestHandler,
                    viewModelScope,
                )
            pager.init()
            Timber.Forest.v("Loaded ${pager.size} seasons for series ${series.id}")
            val pairs =
                pager.mapIndexed { index, _ ->
                    val season = pager.getBlocking(index)
                    Pair(season?.indexNumber!!, index)
                }
            val seasonNumToIndex = mapOf(*pairs.toTypedArray())
            val indexToSeasonNum = mapOf(*pairs.map { Pair(it.second, it.first) }.toTypedArray())
            return ItemListAndMapping(pager, seasonNumToIndex, indexToSeasonNum)
        }

        private suspend fun loadEpisodesInternal(season: Int): EpisodeList {
            val request =
                GetEpisodesRequest(
                    seriesId = item.value!!.id,
                    season = season,
                    sortBy = ItemSortBy.INDEX_NUMBER,
                    fields =
                        listOf(
                            ItemFields.MEDIA_SOURCES,
                            ItemFields.MEDIA_STREAMS,
                            ItemFields.OVERVIEW,
                            ItemFields.CUSTOM_RATING,
                            ItemFields.TRICKPLAY,
                            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                        ),
                )
            val pager = ApiRequestPager(api, request, GetEpisodesRequestHandler, viewModelScope)
            pager.init()
            Timber.Forest.v("Loaded ${pager.size} episodes for season $season")
            return EpisodeList.Success(convertPager(pager))
        }

        fun loadEpisodes(season: Int) {
            this@SeriesViewModel.episodes.value = EpisodeList.Loading
            viewModelScope.launch(ExceptionHandler(true)) {
                val episodes =
                    try {
                        loadEpisodesInternal(season)
                    } catch (e: Exception) {
                        Timber.e(e, "Error loading episodes for $seriesId for season $season")
                        EpisodeList.Error(e)
                    }
                withContext(Dispatchers.Main) {
                    this@SeriesViewModel.episodes.value = episodes
                }
            }
        }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
            listIndex: Int?,
        ) = viewModelScope.launch(Dispatchers.IO + ExceptionHandler()) {
            if (played) {
                api.playStateApi.markPlayedItem(itemId)
            } else {
                api.playStateApi.markUnplayedItem(itemId)
            }
            listIndex?.let {
                refreshEpisode(itemId, listIndex)
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
            listIndex: Int?,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            if (favorite) {
                api.userLibraryApi.markFavoriteItem(itemId)
            } else {
                api.userLibraryApi.unmarkFavoriteItem(itemId)
            }
            if (listIndex != null) {
                refreshEpisode(itemId, listIndex)
            } else {
                fetchItem(seriesId, null)
            }
        }

        fun setSeasonWatched(
            seasonId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(Dispatchers.IO + ExceptionHandler()) {
            setWatched(seasonId, played, null)
            val series = fetchItem(seriesId, null)
            val seasons = getSeasons(series)
            this@SeriesViewModel.seasons.setValueOnMain(seasons)
        }

        fun setWatchedSeries(played: Boolean) =
            viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                if (played) {
                    api.playStateApi.markPlayedItem(seriesId)
                } else {
                    api.playStateApi.markUnplayedItem(seriesId)
                }
                val series = fetchItem(seriesId, null)
                val seasons = getSeasons(series)
                this@SeriesViewModel.seasons.setValueOnMain(seasons)
            }

        fun refreshEpisode(
            itemId: UUID,
            listIndex: Int,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            val base = api.userLibraryApi.getItem(itemId).content
            val item = BaseItem.Companion.from(base, api)
            val eps = episodes.value!!
            if (eps is EpisodeList.Success) {
                val newItems =
                    eps.episodes.items.toMutableList().apply {
                        this[listIndex] = item
                    }
                val newValue = EpisodeList.Success(eps.episodes.copy(items = newItems))
                withContext(Dispatchers.Main) {
                    episodes.value = newValue
                }
            }
        }

        /**
         * Play whichever episode is next up for series or else the first episode
         */
        fun playNextUp() {
            viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                val result by api.tvShowsApi.getNextUp(seriesId = seriesId)
                val nextUp =
                    result.items.firstOrNull() ?: api.tvShowsApi
                        .getEpisodes(
                            seriesId,
                            limit = 1,
                        ).content.items
                        .firstOrNull()
                if (nextUp != null) {
                    navigateTo(Destination.Playback(nextUp.id, 0L))
                } else {
                    showToast(
                        context,
                        "Could not find an episode to play",
                        Toast.LENGTH_SHORT,
                    )
                }
            }
        }

        fun navigateTo(destination: Destination) {
            release()
            navigationManager.navigateTo(destination)
        }

        val chosenStreams = MutableLiveData<ChosenStreams?>(null)
        private var chosenStreamsJob: Job? = null

        fun lookUpChosenTracks(
            itemId: UUID,
            item: BaseItem,
        ) {
            chosenStreamsJob?.cancel()
            chosenStreamsJob =
                viewModelScope.launchIO {
                    val result = itemPlaybackRepository.getSelectedTracks(itemId, item)
                    withContext(Dispatchers.Main) {
                        chosenStreams.value = result
                    }
                }
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
    }

data class ItemListAndMapping(
    val items: List<BaseItem?>,
    val numberToIndex: Map<Int, Int>,
    val indexToNumber: Map<Int, Int>,
) {
    companion object {
        fun empty() = ItemListAndMapping(listOf(), mapOf(), mapOf())
    }
}

/**
 * Calculate the index<->season/ep number pairings
 *
 * This allows for handling of missing seasons
 */
private suspend fun convertPager(pager: ApiRequestPager<*>): ItemListAndMapping {
    val pairs =
        pager.mapIndexed { index, _ ->
            val item = pager.getBlocking(index)
            Pair(item?.indexNumber ?: index, index)
        }
    val seasonNumToIndex = mapOf(*pairs.toTypedArray())
    val indexToSeasonNum = mapOf(*pairs.map { Pair(it.second, it.first) }.toTypedArray())
    return ItemListAndMapping(pager, seasonNumToIndex, indexToSeasonNum)
}

sealed interface EpisodeList {
    data object Loading : EpisodeList

    data class Error(
        val message: String? = null,
        val exception: Throwable? = null,
    ) : EpisodeList {
        constructor(exception: Throwable) : this(null, exception)
    }

    data class Success(
        val episodes: ItemListAndMapping,
    ) : EpisodeList
}
