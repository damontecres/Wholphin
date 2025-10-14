package com.github.damontecres.dolphin.ui.detail

import android.content.Context
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.data.model.Person
import com.github.damontecres.dolphin.hilt.AuthOkHttpClient
import com.github.damontecres.dolphin.preferences.ThemeSongVolume
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.letNotEmpty
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.ui.showToast
import com.github.damontecres.dolphin.util.ApiRequestPager
import com.github.damontecres.dolphin.util.ExceptionHandler
import com.github.damontecres.dolphin.util.GetEpisodesRequestHandler
import com.github.damontecres.dolphin.util.GetItemsRequestHandler
import com.github.damontecres.dolphin.util.LoadingExceptionHandler
import com.github.damontecres.dolphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SeriesViewModel
    @Inject
    constructor(
        api: ApiClient,
        @param:ApplicationContext val context: Context,
        @param:AuthOkHttpClient private val okHttpClient: OkHttpClient,
        private val navigationManager: NavigationManager,
    ) : ItemViewModel(api) {
        private var player: Player? = null
        private lateinit var seriesId: UUID
        private lateinit var prefs: UserPreferences
        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val seasons = MutableLiveData<ItemListAndMapping>(ItemListAndMapping.empty())
        val episodes = MutableLiveData<ItemListAndMapping>(ItemListAndMapping.empty())
        val people = MutableLiveData<List<Person>>(listOf())

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
                if (item != null) {
                    val seasonsInfo = getSeasons(item)

                    // If a particular season was requested, fetch those episodes, otherwise get the first season
                    val episodeInfo =
                        (season ?: seasonsInfo.items.firstOrNull()?.indexNumber)
                            ?.let { seasonNum ->
                                loadEpisodesInternal(seasonNum)
                            } ?: ItemListAndMapping.empty()
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
                    maybePlayThemeSong(prefs.appPreferences.interfacePreferences.playThemeSongs)
                } else {
                    withContext(Dispatchers.Main) {
                        seasons.value = ItemListAndMapping.empty()
                        episodes.value = ItemListAndMapping.empty()
                        loading.value = LoadingState.Error("Series $seriesId not found")
                    }
                }
            }
        }

        /**
         * If the series has a theme song & app settings allow, play it
         */
        @OptIn(UnstableApi::class)
        private fun maybePlayThemeSong(playThemeSongs: ThemeSongVolume) {
            val volume =
                when (playThemeSongs) {
                    ThemeSongVolume.UNRECOGNIZED,
                    ThemeSongVolume.DISABLED,
                    -> return

                    ThemeSongVolume.LOWEST -> .05f
                    ThemeSongVolume.LOW -> .1f
                    ThemeSongVolume.MEDIUM -> .25f
                    ThemeSongVolume.HIGH -> .5f
                    ThemeSongVolume.HIGHEST -> 75f
                }
            viewModelScope.launch(ExceptionHandler()) {
                val themeSongs = api.libraryApi.getThemeSongs(seriesId).content
                themeSongs.items.firstOrNull()?.let { theme ->
                    theme.mediaSources?.firstOrNull()?.let { source ->
                        val url =
                            api.universalAudioApi.getUniversalAudioStreamUrl(
                                theme.id,
                                container = listOf("opus", "mp3", "aaa", "flac"),
                            )
                        Timber.Forest.v("Found theme song for series $seriesId")
                        withContext(Dispatchers.Main) {
                            val player =
                                ExoPlayer
                                    .Builder(context)
                                    .setMediaSourceFactory(
                                        DefaultMediaSourceFactory(
                                            OkHttpDataSource.Factory(okHttpClient),
                                        ),
                                    ).build()
                                    .apply {
                                        this.volume = volume
                                        playWhenReady = true
                                        this@SeriesViewModel.player = this
                                    }
                            addCloseable {
                                player.release()
                            }
                            player.setMediaItem(MediaItem.fromUri(url))
                            player.prepare()
                        }
                    }
                }
            }
        }

        fun release() {
            player?.release()
            player = null
        }

        private suspend fun getSeasons(item: BaseItem): ItemListAndMapping {
            val request =
                GetItemsRequest(
                    parentId = item.id,
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
            Timber.Forest.v("Loaded ${pager.size} seasons for series ${item.id}")
            val pairs =
                pager.mapIndexed { index, _ ->
                    val season = pager.getBlocking(index)
                    Pair(season?.indexNumber!!, index)
                }
            val seasonNumToIndex = mapOf(*pairs.toTypedArray())
            val indexToSeasonNum = mapOf(*pairs.map { Pair(it.second, it.first) }.toTypedArray())
            return ItemListAndMapping(pager, seasonNumToIndex, indexToSeasonNum)
        }

        private suspend fun loadEpisodesInternal(season: Int): ItemListAndMapping {
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
            return convertPager(pager)
        }

        fun loadEpisodes(season: Int) =
            viewModelScope.async(ExceptionHandler(true)) {
                val episodes =
                    try {
                        loadEpisodesInternal(season)
                    } catch (e: Exception) {
                        Timber.e(e, "Error loading episodes for $seriesId for season $season")
                        // TODO show error in UI?
                        ItemListAndMapping.empty()
                    }
                withContext(Dispatchers.Main) {
                    this@SeriesViewModel.episodes.value = episodes
                }
            }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
            listIndex: Int?,
        ) = viewModelScope.launch(ExceptionHandler()) {
            if (played) {
                api.playStateApi.markPlayedItem(itemId)
            } else {
                api.playStateApi.markUnplayedItem(itemId)
            }
            listIndex?.let {
                refreshEpisode(itemId, listIndex)
            }
        }

        fun setWatchedSeries(played: Boolean) =
            viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                if (played) {
                    api.playStateApi.markPlayedItem(seriesId)
                } else {
                    api.playStateApi.markUnplayedItem(seriesId)
                }
                init(prefs, seriesId, null, null, null)
            }

        fun refreshEpisode(
            itemId: UUID,
            listIndex: Int,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            val base = api.userLibraryApi.getItem(itemId).content
            val item = BaseItem.Companion.from(base, api)
            val eps = episodes.value!!
            withContext(Dispatchers.Main) {
                episodes.value =
                    eps.copy(
                        items =
                            eps.items.toMutableList().apply {
                                this[listIndex] = item
                            },
                    )
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
 * Calculate the index<->season number pairings
 *
 * This allows for handling of missing seasons
 */
private suspend fun convertPager(pager: ApiRequestPager<*>): ItemListAndMapping {
    val pairs =
        pager.mapIndexed { index, _ ->
            val season = pager.getBlocking(index)
            Pair(season?.indexNumber!!, index)
        }
    val seasonNumToIndex = mapOf(*pairs.toTypedArray())
    val indexToSeasonNum = mapOf(*pairs.map { Pair(it.second, it.first) }.toTypedArray())
    return ItemListAndMapping(pager, seasonNumToIndex, indexToSeasonNum)
}
