package com.github.damontecres.dolphin.ui.detail

import android.content.Context
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
import com.github.damontecres.dolphin.data.model.Video
import com.github.damontecres.dolphin.hilt.AuthOkHttpClient
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
    ) : ItemViewModel<Video>(api) {
        private var player: Player? = null
        private lateinit var seriesId: UUID
        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val seasons = MutableLiveData<List<BaseItem?>>(listOf())
        val episodes = MutableLiveData<List<BaseItem?>>(listOf())

        fun init(
            itemId: UUID,
            potential: BaseItem?,
            season: Int?,
            episode: Int?,
        ) {
            this.seriesId = itemId
            viewModelScope.launch(
                LoadingExceptionHandler(
                    loading,
                    "Error loading series $seriesId",
                ) + Dispatchers.IO,
            ) {
                val item = fetchItem(seriesId, potential)
                if (item != null) {
                    val seasonPager = getSeasons(item)
                    val episodePager =
                        season?.let { seasonNum ->
                            // TODO map season number to index in list
                            loadEpisodesInternal(seasonNum)
                        }
                    withContext(Dispatchers.Main) {
                        seasons.value = seasonPager.orEmpty()
                        episodes.value = episodePager.orEmpty()
                        loading.value = LoadingState.Success
                    }
                    maybePlayThemeSong()
                } else {
                    withContext(Dispatchers.Main) {
                        seasons.value = listOf()
                        episodes.value = listOf()
                        loading.value = LoadingState.Error("Series $seriesId not found")
                    }
                }
            }
        }

        @OptIn(UnstableApi::class)
        private fun maybePlayThemeSong() {
            // TODO user preference to enable/disable this
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
                                        volume = .1f
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

        private suspend fun getSeasons(item: BaseItem): ApiRequestPager<GetItemsRequest>? {
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
            mapOf(*pairs.toTypedArray())
            mapOf(*pairs.map { Pair(it.second, it.first) }.toTypedArray())
            return pager
        }

        private suspend fun loadEpisodesInternal(season: Int): ApiRequestPager<GetEpisodesRequest> {
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
            return pager
        }

        fun loadEpisodes(season: Int) =
            viewModelScope.async(ExceptionHandler(true)) {
                val episodePager =
                    try {
                        loadEpisodesInternal(season)
                    } catch (e: Exception) {
                        Timber.Forest.e(e, "Error loading episodes for $seriesId for season $season")
                        // TODO show error in UI?
                        listOf()
                    }
                withContext(Dispatchers.Main) {
                    episodes.value = episodePager
                }
            }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
            listIndex: Int,
        ) = viewModelScope.launch(ExceptionHandler()) {
            if (played) {
                api.playStateApi.markPlayedItem(itemId)
            } else {
                api.playStateApi.markUnplayedItem(itemId)
            }
            refreshEpisode(itemId, listIndex)
        }

        fun refreshEpisode(
            itemId: UUID,
            listIndex: Int,
        ) = viewModelScope.launch(ExceptionHandler()) {
            val base = api.userLibraryApi.getItem(itemId).content
            val item = BaseItem.Companion.from(base, api)
            episodes.value =
                episodes.value!!.toMutableList().apply {
                    this[listIndex] = item
                }
        }
    }
