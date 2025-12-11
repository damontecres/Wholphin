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
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetEpisodesRequestHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import javax.inject.Inject

@HiltViewModel
class SeriesViewModel
    @Inject
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
    ) : ItemViewModel(api) {
        private lateinit var seriesId: UUID
        private lateinit var prefs: UserPreferences
        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val seasons = MutableLiveData<List<BaseItem>>(listOf())
        val episodes = MutableLiveData<EpisodeList>(EpisodeList.Loading)

        val trailers = MutableLiveData<List<Trailer>>(listOf())
        val extras = MutableLiveData<List<ExtrasItem>>(listOf())
        val people = MutableLiveData<List<Person>>(listOf())
        val similar = MutableLiveData<List<BaseItem>>()

        val peopleInEpisode = MutableLiveData<PeopleInItem>(PeopleInItem())

        fun init(
            prefs: UserPreferences,
            itemId: UUID,
            seasonEpisodeIds: SeasonEpisodeIds?,
            loadAdditionalDetails: Boolean,
        ) {
            this.seriesId = itemId
            this.prefs = prefs
            viewModelScope.launch(
                LoadingExceptionHandler(
                    loading,
                    "Error loading series $seriesId",
                ) + Dispatchers.IO,
            ) {
                val item = fetchItem(seriesId)
                val seasons = getSeasons(item)

                // If a particular season was requested, fetch those episodes, otherwise get the first season
                val initialSeason =
                    if (seasonEpisodeIds != null) {
                        seasons.firstOrNull {
                            equalsNotNull(it.id, seasonEpisodeIds.seasonId) ||
                                equalsNotNull(it.indexNumber, seasonEpisodeIds.seasonNumber)
                        }
                    } else {
                        seasons.firstOrNull()
                    }
                val episodeInfo =
                    initialSeason?.let {
                        loadEpisodesInternal(
                            it.id,
                            seasonEpisodeIds?.episodeId,
                            seasonEpisodeIds?.episodeNumber,
                        )
                    } ?: EpisodeList.Error("Could not determine season for selected episode")
                withContext(Dispatchers.Main) {
                    this@SeriesViewModel.seasons.value = seasons
                    episodes.value = episodeInfo
                    loading.value = LoadingState.Success
                }
                if (loadAdditionalDetails) {
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

        private suspend fun getSeasons(series: BaseItem): List<BaseItem> {
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
            val seasons =
                GetItemsRequestHandler.execute(api, request).content.items.map {
                    BaseItem.from(
                        it,
                        api,
                    )
                }
            Timber.v("Loaded ${seasons.size} seasons for series ${series.id}")
            return seasons
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
            pager.init()
            val initialIndex =
                if (episodeId != null || episodeNumber != null) {
                    pager
                        .indexOfBlocking {
                            equalsNotNull(it?.id, episodeId) ||
                                equalsNotNull(it?.indexNumber, episodeNumber)
                        }.coerceAtLeast(0)
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
                            it.episodes.getOrNull(it.initialIndex)
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
            val seasons = getSeasons(series)
            this@SeriesViewModel.seasons.setValueOnMain(seasons)
        }

        fun setWatchedSeries(played: Boolean) =
            viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                favoriteWatchManager.setWatched(seriesId, played)
                val series = fetchItem(seriesId)
                val seasons = getSeasons(series)
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
        val initialIndex: Int,
    ) : EpisodeList
}

data class PeopleInItem(
    val itemId: UUID? = null,
    val people: List<Person> = listOf(),
)
