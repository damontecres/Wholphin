package com.github.damontecres.wholphin.ui.detail.series

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.ExtrasService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PeopleFavorites
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.ThemeSongPlayer
import com.github.damontecres.wholphin.services.TrailerService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.equalsNotNull
import com.github.damontecres.wholphin.ui.gt
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.lt
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.BlockingList
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetEpisodesRequestHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.successValue
import com.google.common.cache.CacheBuilder
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
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

@HiltViewModel(assistedFactory = SeriesViewModel.Factory::class)
class SeriesViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
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
        val mediaReportService: MediaReportService,
        private val userPreferencesService: UserPreferencesService,
        private val backdropService: BackdropService,
        private val seerrService: SeerrService,
        private val mediaManagementService: MediaManagementService,
        @Assisted val seriesId: UUID,
        @Assisted val seasonEpisodeIds: SeasonEpisodeIds?,
        @Assisted val seriesPageType: SeriesPageType,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                seriesId: UUID,
                seasonEpisodeIds: SeasonEpisodeIds?,
                seriesPageType: SeriesPageType,
            ): SeriesViewModel
        }

        private val _state = MutableStateFlow(SeriesState())
        val state: StateFlow<SeriesState> = _state

        val position = MutableStateFlow(SeriesOverviewPosition(0, 0))

        init {
            viewModelScope.launchIO {
                Timber.v("Start")
                addCloseable { themeSongPlayer.stop() }
                val series =
                    api.userLibraryApi
                        .getItem(seriesId)
                        .content
                        .let { BaseItem(it) }
                viewModelScope.launchDefault {
                    mediaManagementService.collectCanDelete(flowOf(series)) { canDelete ->
                        _state.update { it.copy(canDeleteSeries = canDelete) }
                    }
                }
                backdropService.submit(series)

                val seasonsDeferred = getSeasons(series, seasonEpisodeIds?.seasonNumber)

                val episodeListDeferred =
                    if (seriesPageType == SeriesPageType.OVERVIEW) {
                        viewModelScope.async(WholphinDispatchers.IO) {
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
                            it.copy(seasonTabIndex = index.coerceAtLeast(0))
                        }
                    }
                    viewModelScope.launchIO {
                        val extras = extrasService.getExtras(seasonEpisodeIds.seasonId)
                        _state.update { it.copy(extras = extras) }
                    }
                }
                val remoteTrailers = trailerService.getRemoteTrailers(series)
                this@SeriesViewModel.position.update {
                    it.copy(
                        episodeRowIndex =
                            (episodes as? EpisodeList.Success)?.initialEpisodeIndex ?: 0,
                    )
                }
                _state.update {
                    it.copy(
                        series = DataLoadingState.Success(series),
                        seasons = seasons,
                        episodes = episodes,
                        trailers = remoteTrailers,
                    )
                }

                if (seriesPageType == SeriesPageType.DETAILS) {
                    viewModelScope.launchIO {
                        trailerService.getLocalTrailers(series).letNotEmpty { localTrailers ->
                            _state.update { it.copy(trailers = localTrailers + remoteTrailers) }
                        }
                    }
                    viewModelScope.launchIO {
                        val people = peopleFavorites.getPeopleFor(series)
                        _state.update { it.copy(people = people) }
                    }
                    viewModelScope.launchIO {
                        val extras = extrasService.getExtras(series.id)
                        _state.update { it.copy(extras = extras) }
                    }
                    if (state.value.similar.isEmpty()) {
                        viewModelScope.launchIO {
                            val similar =
                                api.libraryApi
                                    .getSimilarItems(
                                        GetSimilarItemsRequest(
                                            userId = serverRepository.currentUser?.id,
                                            itemId = seriesId,
                                            fields = SlimItemFields,
                                            limit = 25,
                                        ),
                                    ).content.items
                                    .map { BaseItem(it, true) }
                            _state.update { it.copy(similar = similar) }
                        }
                    }
                    viewModelScope.launchIO {
                        val results = seerrService.similar(series).orEmpty()
                        _state.update { it.copy(discovered = results) }
                    }
                    viewModelScope.launchIO {
                        seerrService.active.collectLatest { active ->
                            val tv =
                                if (active) {
                                    try {
                                        seerrService
                                            .getTvSeries(series)
                                            ?.let { seerrService.createDiscoverItem(it) }
                                    } catch (ex: Exception) {
                                        Timber.e(ex)
                                        null
                                    }
                                } else {
                                    null
                                }
                            _state.update { it.copy(discoverSeries = tv) }
                        }
                    }
                }
                mediaManagementService.deletedItemFlow
                    .onEach { deletedItem ->
                        if (deletedItem.item.data.seriesId == seriesId) {
                            Timber.d(
                                "Item %s deleted from series %s",
                                deletedItem.item.id,
                                seriesId,
                            )
                            val seasons = getSeasons(series, seasonEpisodeIds?.seasonNumber).await()
                            _state.update { it.copy(seasons = seasons) }
                        }
                    }.catch { ex ->
                        Timber.e(ex, "Error refreshing after deleted item")
                    }.launchIn(viewModelScope)
            }
        }

        fun onResumePage() {
            state.value.series.successValue?.let { item ->
                viewModelScope.launchDefault { backdropService.submit(item) }
                viewModelScope.launchDefault {
                    themeSongPlayer.playThemeFor(seriesId)
                }
            }
        }

        fun refresh() {
            state.value.series.successValue?.let { item ->
                viewModelScope.launchIO {
                    (state.value.seasons as? ApiRequestPager<*>)?.refresh()
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
            viewModelScope.async(WholphinDispatchers.IO) {
                Timber.v("getSeasons for %s", series.id)
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
                                    ItemFields.CAN_DELETE,
                                )
                            } else {
                                listOf(
                                    ItemFields.CAN_DELETE,
                                )
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
                            ItemFields.MEDIA_SOURCE_COUNT,
                            ItemFields.OVERVIEW,
                            ItemFields.CUSTOM_RATING,
                            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                            ItemFields.CAN_DELETE,
                        ),
                )
            Timber.v(
                "loadEpisodesInternal: episodeId=%s, episodeNumber=%s",
                episodeId,
                episodeNumber,
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
            val currentEpisodes = (state.value.episodes as? EpisodeList.Success)
            if (currentEpisodes == null || currentEpisodes.seasonId != seasonId) {
                _state.update {
                    it.copy(
                        peopleInEpisode = PeopleInItem(),
                        episodes = EpisodeList.Loading,
                        extras = emptyList(),
                    )
                }
            }
            viewModelScope.launchIO(ExceptionHandler(true)) {
                val episodes =
                    try {
                        loadEpisodesInternal(seasonId, null, null)
                    } catch (e: Exception) {
                        Timber.e(e, "Error loading episodes for $seriesId for season $seasonId")
                        EpisodeList.Error(e)
                    }
                _state.update { it.copy(episodes = episodes) }
            }
            viewModelScope.launchIO {
                val extras = extrasService.getExtras(seasonId)
                _state.update { it.copy(extras = extras) }
            }
        }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
            listIndex: Int?,
        ) = viewModelScope.launch(WholphinDispatchers.IO + ExceptionHandler()) {
            favoriteWatchManager.setWatched(itemId, played)
            listIndex?.let {
                refreshEpisode(itemId, listIndex)
            }
        }

        private fun updateSeries() {
            viewModelScope.launchIO {
                try {
                    val series =
                        api.userLibraryApi
                            .getItem(seriesId)
                            .content
                            .let(::BaseItem)
                    _state.update { it.copy(series = DataLoadingState.Success(series)) }
                    viewModelScope.launchIO {
                        val people = peopleFavorites.getPeopleFor(series)
                        _state.update { it.copy(people = people) }
                    }
                    viewModelScope.launchIO {
                        val seasons = getSeasons(series, null).await()
                        _state.update { it.copy(seasons = seasons) }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error updating series")
                    showToast(context, "Error updating series")
                }
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
            listIndex: Int?,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            if (listIndex != null) {
                refreshEpisode(itemId, listIndex)
            } else {
                updateSeries()
            }
        }

        fun setSeasonWatched(
            seasonId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(WholphinDispatchers.IO + ExceptionHandler()) {
            setWatched(seasonId, played, null)
            updateSeries()
        }

        fun setWatchedSeries(played: Boolean) =
            viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
                favoriteWatchManager.setWatched(seriesId, played)
                updateSeries()
            }

        fun refreshEpisode(
            itemId: UUID,
            listIndex: Int,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            val eps = state.value.episodes
            if (eps is EpisodeList.Success) {
                eps.episodes.refreshItem(listIndex, itemId)
                _state.update { it.copy(episodes = eps) }
            }
            // Kind of hack to ensure the backdrop is reloaded if needed
            state.value.series.successValue
                ?.let { backdropService.submit(it) }
        }

        /**
         * Play whichever episode is next up for series or else the first episode
         */
        fun playNextUp() {
            viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
                val result by api.tvShowsApi.getNextUp(seriesId = seriesId)
                val nextUp =
                    result.items.firstOrNull() ?: api.tvShowsApi
                        .getEpisodes(
                            seriesId,
                            limit = 1,
                        ).content.items
                        .firstOrNull()
                if (nextUp != null) {
                    withContext(WholphinDispatchers.Main) {
                        navigateTo(Destination.Playback(BaseItem(nextUp)))
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
                    _state.update { it.copy(chosenStreams = result) }
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

        private var peopleInEpisodeJob: Job? = null
        private val peopleInEpisodeCache =
            CacheBuilder
                .newBuilder()
                .maximumSize(25)
                .build<UUID, Deferred<PeopleInItem>>()

        suspend fun lookupPeopleInEpisode(item: BaseItem) {
            peopleInEpisodeJob?.cancel()
            if (state.value.peopleInEpisode.itemId != item.id) {
                _state.update { it.copy(peopleInEpisode = PeopleInItem()) }
                val result =
                    peopleInEpisodeCache
                        .get(item.id) {
                            viewModelScope.async(WholphinDispatchers.IO) {
                                val list =
                                    api.userLibraryApi
                                        .getItem(item.id)
                                        .content.people
                                        ?.map { Person.fromDto(context, it, api) }
                                        .orEmpty()

                                PeopleInItem(item.id, list)
                            }
                        }
                peopleInEpisodeJob =
                    viewModelScope.launch(ExceptionHandler()) {
                        delay(250)
                        val peopleInEpisode = result.await()
                        _state.update { it.copy(peopleInEpisode = peopleInEpisode) }
                    }
            }
        }

        fun clearChosenStreams(
            item: BaseItem,
            chosenStreams: ChosenStreams?,
        ) {
            viewModelScope.launchIO {
                itemPlaybackRepository.deleteChosenStreams(chosenStreams)
                lookUpChosenTracks(item.id, item)
            }
        }

        fun deleteItem(item: BaseItem) {
            deleteItem(context, mediaManagementService, item) {
                viewModelScope.launchDefault {
                    if (item.type == BaseItemKind.SERIES) {
                        navigationManager.goBack()
                    } else if (seriesPageType == SeriesPageType.DETAILS) {
                        state.value.series.successValue?.let { series ->
                            val seasons = getSeasons(series, null).await()
                            if (seasons.isEmpty()) {
                                navigationManager.goBack()
                            } else {
                                _state.update { it.copy(seasons = seasons) }
                            }
                        }
                    } else {
                        position.value.let { (_, episodeIndex) ->
                            val eps = state.value.episodes as? EpisodeList.Success
                            if (eps != null) {
                                val pager = eps.episodes
                                val lastIndex = pager.lastIndex
                                pager.refreshPagesAfter(episodeIndex)
                                if (pager.isEmpty()) {
                                    navigationManager.goBack()
                                } else {
                                    if (episodeIndex == lastIndex) {
                                        // Deleted last episode, so need to move left
                                        _state.update {
                                            it.copy(
                                                episodes =
                                                    EpisodeList.Success(
                                                        eps.seasonId,
                                                        pager,
                                                        episodeIndex - 1,
                                                    ),
                                            )
                                        }
                                        position.update { it.copy(episodeRowIndex = episodeIndex - 1) }
                                    } else {
                                        _state.update {
                                            it.copy(
                                                episodes =
                                                    EpisodeList.Success(
                                                        eps.seasonId,
                                                        pager,
                                                        episodeIndex,
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        suspend fun canDelete(item: BaseItem): Boolean = mediaManagementService.canDelete(item)

        fun canDelete(
            item: BaseItem,
            appPreferences: AppPreferences,
        ): Boolean = mediaManagementService.canDelete(item, appPreferences)
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

/**
 * Find the index in the [list] where the item's `indexNumber`==[targetNum] or `id`==[targetId]
 *
 * This is necessary in cases where items are missing. E.g. if looking for episode 4 but
 * episodes 2 & 3 is missing, then the index in the list for episode 4 will be `1`.
 *
 * @param targetNum the 1-index season or episode number
 * @param targetId the season or episode ID
 *
 * @return the index within [list] that matches, or zero if no match is found
 */
private suspend fun findIndexOf(
    targetNum: Int?,
    targetId: UUID?,
    list: BlockingList<BaseItem?>,
): Int {
    // Adjust for 1-based numbers
    val listIndex = targetNum?.minus(1)?.coerceAtLeast(0)
    val index =
        if (targetId != null && (targetNum == null || listIndex !in list.indices)) {
            // No hint info, so have to check everything
            list.indexOfBlocking {
                equalsNotNull(it?.indexNumber, targetNum) ||
                    equalsNotNull(it?.id, targetId)
            }
        } else if (listIndex != null && listIndex in list.indices) {
            // Start searching from the target number and choose direction from there
            val num = list.getBlocking(listIndex)?.indexNumber
            if (num.lt(targetNum)) {
                for (i in listIndex + 1 until list.lastIndex) {
                    val season = list.getBlocking(i)
                    if (equalsNotNull(season?.indexNumber, targetNum) ||
                        equalsNotNull(season?.id, targetId)
                    ) {
                        return i
                    }
                }
                return 0
            } else if (num.gt(targetNum)) {
                for (i in listIndex - 1 downTo 0) {
                    val season = list.getBlocking(i)
                    if (equalsNotNull(season?.indexNumber, targetNum) ||
                        equalsNotNull(season?.id, targetId)
                    ) {
                        return i
                    }
                }
                return 0
            } else {
                listIndex
            }
        } else {
            0
        }
    return index
}

data class SeriesState(
    val series: DataLoadingState<BaseItem> = DataLoadingState.Pending,
    val seasons: List<BaseItem?> = emptyList(),
    val episodes: EpisodeList = EpisodeList.Loading,
    val trailers: List<Trailer> = emptyList(),
    val extras: List<ExtrasItem> = emptyList(),
    val people: List<Person> = emptyList(),
    val similar: List<BaseItem> = emptyList(),
    val canDeleteSeries: Boolean = false,
    val peopleInEpisode: PeopleInItem = PeopleInItem(),
    val discovered: List<DiscoverItem> = emptyList(),
    val discoverSeries: DiscoverItem? = null,
    val chosenStreams: ChosenStreams? = null,
)
