package com.github.damontecres.wholphin.ui.detail.series

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.preferences.ThemeSongVolume
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.ExtrasService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PeopleFavorites
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.ThemeSongPlayer
import com.github.damontecres.wholphin.services.TrailerService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.detail.ItemViewModel
import com.github.damontecres.wholphin.ui.equalsNotNull
import com.github.damontecres.wholphin.ui.gt
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.lt
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetEpisodesRequestHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
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

@HiltViewModel(assistedFactory = SeriesViewModel.Factory::class)
class SeriesViewModel
    @AssistedInject
    constructor(
        api: ApiClient,
        @param:ApplicationContext val context: Context,
        val serverRepository: ServerRepository,
        private val navigationManager: NavigationManager,
        private val itemPlaybackRepository: ItemPlaybackRepository,
        private val themeSongPlayer: ThemeSongPlayer,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val peopleFavorites: PeopleFavorites,
        private val trailerService: TrailerService,
        private val extrasService: ExtrasService,
        val streamChoiceService: StreamChoiceService,
        private val userPreferencesService: UserPreferencesService,
        private val backdropService: BackdropService,
        @Assisted val seriesId: UUID,
        @Assisted val seasonEpisodeIds: SeasonEpisodeIds?,
        @Assisted val seriesPageType: SeriesPageType,
    ) : ItemViewModel(api) {
        @AssistedFactory
        interface Factory {
            fun create(
                seriesId: UUID,
                seasonEpisodeIds: SeasonEpisodeIds?,
                seriesPageType: SeriesPageType,
            ): SeriesViewModel
        }

        private lateinit var prefs: UserPreferences
        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val seasons = MutableLiveData<List<BaseItem?>>(listOf())
        val episodes = MutableLiveData<EpisodeList>(EpisodeList.Loading)

        val trailers = MutableLiveData<List<Trailer>>(listOf())
        val extras = MutableLiveData<List<ExtrasItem>>(listOf())
        val people = MutableLiveData<List<Person>>(listOf())
        val similar = MutableLiveData<List<BaseItem>>()

        val peopleInEpisode = MutableLiveData<PeopleInItem>(PeopleInItem())

        val position = MutableStateFlow(SeriesOverviewPosition(0, 0))

        init {
            viewModelScope.launch(
                LoadingExceptionHandler(
                    loading,
                    "Error loading series $seriesId",
                ) + Dispatchers.IO,
            ) {
                this@SeriesViewModel.prefs = userPreferencesService.getCurrent()
                Timber.v("Start")
                val item = fetchItem(seriesId)
                backdropService.submit(item)

                val seasonsDeferred = getSeasons(item, seasonEpisodeIds?.seasonNumber)

                val episodeListDeferred =
                    if (seriesPageType == SeriesPageType.OVERVIEW) {
                        viewModelScope.async(Dispatchers.IO) {
                            if (seasonEpisodeIds != null) {
                                loadEpisodesInternal(
                                    seasonEpisodeIds.seasonId,
                                    seasonEpisodeIds.episodeId,
                                    seasonEpisodeIds.episodeNumber,
                                )
                            } else {
                                seasonsDeferred.await().firstOrNull()?.let {
                                    loadEpisodesInternal(
                                        it.id,
                                        null,
                                        null,
                                    )
                                } ?: EpisodeList.Error(message = "Could not determine season")
                            }
                        }
                    } else {
                        CompletableDeferred(value = EpisodeList.Loading)
                    }
                val seasons = seasonsDeferred.await()
                val episodes = episodeListDeferred.await()
                Timber.v("Done")

                if (seriesPageType == SeriesPageType.OVERVIEW && seasonEpisodeIds != null) {
                    viewModelScope.launchIO {
                        val index =
                            (seasons as? ApiRequestPager<*>)?.let {
                                findIndexOf(
                                    seasonEpisodeIds.seasonNumber,
                                    seasonEpisodeIds.seasonId,
                                    it,
                                )
                            } ?: 0
                        Timber.v("Got initial season index: $index")
                        position.update {
                            it.copy(seasonTabIndex = index)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    this@SeriesViewModel.position.update {
                        it.copy(
                            episodeRowIndex =
                                (episodes as? EpisodeList.Success)?.initialEpisodeIndex ?: 0,
                        )
                    }
                    this@SeriesViewModel.seasons.value = seasons
                    this@SeriesViewModel.episodes.value = episodes
                    loading.value = LoadingState.Success
                }
                if (seriesPageType == SeriesPageType.DETAILS) {
                    viewModelScope.launchIO {
                        val trailers = trailerService.getTrailers(item)
                        withContext(Dispatchers.Main) {
                            this@SeriesViewModel.trailers.value = trailers
                        }
                    }
                    viewModelScope.launchIO {
                        val people = peopleFavorites.getPeopleFor(item)
                        this@SeriesViewModel.people.setValueOnMain(people)
                    }
                    viewModelScope.launchIO {
                        val extras = extrasService.getExtras(item.id)
                        this@SeriesViewModel.extras.setValueOnMain(extras)
                    }
                    if (!similar.isInitialized) {
                        viewModelScope.launchIO {
                            val similar =
                                api.libraryApi
                                    .getSimilarItems(
                                        GetSimilarItemsRequest(
                                            userId = serverRepository.currentUser.value?.id,
                                            itemId = seriesId,
                                            fields = SlimItemFields,
                                            limit = 25,
                                        ),
                                    ).content.items
                                    .map { BaseItem.from(it, api, true) }
                            this@SeriesViewModel.similar.setValueOnMain(similar)
                        }
                    }
                }
            }
        }

        /**
         * If the series has a theme song & app settings allow, play it
         */
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

        private fun getSeasons(
            series: BaseItem,
            seasonNum: Int?,
        ): Deferred<List<BaseItem?>> =
            viewModelScope.async(Dispatchers.IO) {
                val request =
                    GetItemsRequest(
                        parentId = series.id,
                        recursive = false,
                        includeItemTypes = listOf(BaseItemKind.SEASON),
                        sortBy = listOf(ItemSortBy.INDEX_NUMBER),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        fields =
                            if (seriesPageType == SeriesPageType.DETAILS) {
                                listOf(
                                    ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                                )
                            } else {
                                null
                            },
                    )
                val pager =
                    ApiRequestPager(
                        api,
                        request,
                        GetItemsRequestHandler,
                        viewModelScope,
                        pageSize = 10,
                    ).init(seasonNum ?: 0)
//                val seasons =
//                    GetItemsRequestHandler.execute(api, request).content.items.map {
//                        BaseItem.from(
//                            it,
//                            api,
//                        )
//                    }
//                Timber.v("Loaded ${seasons.size} seasons for series ${series.id}")
                pager
            }

        private suspend fun loadEpisodesInternal(
            seasonId: UUID,
            episodeId: UUID?,
            episodeNumber: Int?,
        ): EpisodeList {
            val request =
                GetEpisodesRequest(
                    seriesId = seriesId,
                    seasonId = seasonId,
                    sortBy = ItemSortBy.INDEX_NUMBER,
                    fields =
                        listOf(
                            ItemFields.MEDIA_SOURCES,
                            ItemFields.MEDIA_STREAMS,
                            ItemFields.OVERVIEW,
                            ItemFields.CUSTOM_RATING,
                            ItemFields.TRICKPLAY,
                            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                            ItemFields.PEOPLE,
                        ),
                )
            val pager = ApiRequestPager(api, request, GetEpisodesRequestHandler, viewModelScope)
            pager.init(episodeNumber ?: 0)
            val initialIndex =
                if (episodeId != null || episodeNumber != null) {
                    findIndexOf(episodeNumber, episodeId, pager)
                        .coerceAtLeast(0)
                } else {
                    // Force the first page to to be fetched
                    if (pager.isNotEmpty()) {
                        pager.getBlocking(0)
                    }
                    0
                }
            Timber.v("Loaded ${pager.size} episodes for season $seasonId, initialIndex=$initialIndex")
            return EpisodeList.Success(seasonId, pager, initialIndex)
        }

        fun loadEpisodes(seasonId: UUID) {
            val currentEpisodes = (this@SeriesViewModel.episodes.value as? EpisodeList.Success)
            if (currentEpisodes == null || currentEpisodes.seasonId != seasonId) {
                this@SeriesViewModel.peopleInEpisode.value = PeopleInItem()
                this@SeriesViewModel.episodes.value = EpisodeList.Loading
            }
            viewModelScope.launchIO(ExceptionHandler(true)) {
                val episodes =
                    try {
                        loadEpisodesInternal(seasonId, null, null)
                    } catch (e: Exception) {
                        Timber.e(e, "Error loading episodes for $seriesId for season $seasonId")
                        EpisodeList.Error(e)
                    }
                withContext(Dispatchers.Main) {
                    this@SeriesViewModel.episodes.value = episodes
                }
                if (currentEpisodes == null || currentEpisodes.seasonId != seasonId) {
                    (episodes as? EpisodeList.Success)
                        ?.let {
                            it.episodes.getOrNull(it.initialEpisodeIndex)
                        }?.let { lookupPeopleInEpisode(it) }
                }
            }
        }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
            listIndex: Int?,
        ) = viewModelScope.launch(Dispatchers.IO + ExceptionHandler()) {
            favoriteWatchManager.setWatched(itemId, played)
            listIndex?.let {
                refreshEpisode(itemId, listIndex)
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
            listIndex: Int?,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            if (listIndex != null) {
                refreshEpisode(itemId, listIndex)
            } else {
                val item = fetchItem(seriesId)
                viewModelScope.launchIO {
                    val people = peopleFavorites.getPeopleFor(item)
                    this@SeriesViewModel.people.setValueOnMain(people)
                }
            }
        }

        fun setSeasonWatched(
            seasonId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(Dispatchers.IO + ExceptionHandler()) {
            setWatched(seasonId, played, null)
            val series = fetchItem(seriesId)
            val seasons = getSeasons(series, null).await()
            this@SeriesViewModel.seasons.setValueOnMain(seasons)
        }

        fun setWatchedSeries(played: Boolean) =
            viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                favoriteWatchManager.setWatched(seriesId, played)
                val series = fetchItem(seriesId)
                val seasons = getSeasons(series, null).await()
                this@SeriesViewModel.seasons.setValueOnMain(seasons)
            }

        fun refreshEpisode(
            itemId: UUID,
            listIndex: Int,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            val eps = episodes.value
            if (eps is EpisodeList.Success) {
                eps.episodes.refreshItem(listIndex, itemId)
                withContext(Dispatchers.Main) {
                    episodes.value = eps
                }
            }
            // Kind of hack to ensure the backdrop is reloaded if needed
            item.value?.let { backdropService.submit(it) }
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
                    withContext(Dispatchers.Main) {
                        navigateTo(Destination.Playback(nextUp.id, 0L))
                    }
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
                    val result =
                        itemPlaybackRepository.getSelectedTracks(
                            itemId,
                            item,
                            userPreferencesService.getCurrent(),
                        )
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

        private var peopleInEpisodeJob: Job? = null

        suspend fun lookupPeopleInEpisode(item: BaseItem) {
            peopleInEpisodeJob?.cancel()
            if (peopleInEpisode.value?.itemId != item.id) {
                peopleInEpisode.setValueOnMain(PeopleInItem())
                peopleInEpisodeJob =
                    viewModelScope.launch(ExceptionHandler()) {
                        delay(250)
                        val people =
                            item.data.people
                                ?.letNotEmpty { it.map { Person.fromDto(it, api) } }
                                .orEmpty()
                        peopleInEpisode.setValueOnMain(PeopleInItem(item.id, people))
                    }
            }
        }
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
        val seasonId: UUID,
        val episodes: ApiRequestPager<GetEpisodesRequest>,
        val initialEpisodeIndex: Int,
    ) : EpisodeList
}

data class PeopleInItem(
    val itemId: UUID? = null,
    val people: List<Person> = listOf(),
)

enum class SeriesPageType {
    DETAILS,
    OVERVIEW,
}

private suspend fun findIndexOf(
    targetNum: Int?,
    targetId: UUID?,
    pager: ApiRequestPager<*>,
): Int {
    val index =
        if (targetId != null && (targetNum == null || targetNum !in pager.indices)) {
            // No hint info, so have to check everything
            pager.indexOfBlocking { equalsNotNull(it?.id, targetId) }
        } else if (targetNum != null && targetNum in pager.indices) {
            // Start searching from the season number and choose direction from there
            val num = pager.getBlocking(targetNum)?.indexNumber
            if (num.lt(targetNum)) {
                for (i in targetNum + 1 until pager.lastIndex) {
                    val season = pager.getBlocking(i)
                    if (equalsNotNull(season?.indexNumber, targetNum) ||
                        equalsNotNull(season?.id, targetId)
                    ) {
                        return i
                    }
                }
                return 0
            } else if (num.gt(targetNum)) {
                for (i in targetNum - 1 downTo 0) {
                    val season = pager.getBlocking(i)
                    if (equalsNotNull(season?.indexNumber, targetNum) ||
                        equalsNotNull(season?.id, targetId)
                    ) {
                        return i
                    }
                }
                return 0
            } else {
                targetNum
            }
        } else {
            0
        }
    return index
}
