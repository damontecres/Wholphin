package com.github.damontecres.wholphin.ui.playback

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ItemPlaybackDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PlaylistCreationResult
import com.github.damontecres.wholphin.services.PlaylistCreator
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.findActivity
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.getExternalPlayers
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.subtitleApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.extensions.inWholeTicks
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class PlayExternalViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val itemPlaybackDao: ItemPlaybackDao,
        private val playlistCreator: PlaylistCreator,
        private val streamChoiceService: StreamChoiceService,
        private val navigationManager: NavigationManager,
        private val userPreferencesService: UserPreferencesService,
    ) : ViewModel() {
        val launched = savedStateHandle.getMutableStateFlow("launched", false)
        val state = MutableStateFlow(PlayExternalState())

        fun init(destination: Destination) {
            Timber.v("init called: %s", destination)
            state.update { it.copy(loading = LoadingState.Loading) }
            viewModelScope.launchDefault {
                val prefs = userPreferencesService.getCurrent()
                val positionMs: Long
                val itemId =
                    when (val d = destination) {
                        is Destination.Playback -> {
                            positionMs = d.positionMs
                            d.itemId
                        }

                        is Destination.PlaybackList -> {
                            positionMs = 0
                            d.itemId
                        }

                        else -> {
                            throw IllegalArgumentException("Destination not supported: $destination")
                        }
                    }
                try {
                    val queriedItem = api.userLibraryApi.getItem(itemId).content
                    val base =
                        if (queriedItem.type.playable) {
                            queriedItem
                        } else if (destination is Destination.PlaybackList) {
                            val playlistResult =
                                playlistCreator.createFrom(
                                    item = queriedItem,
                                    startIndex = destination.startIndex ?: 0,
                                    sortAndDirection = destination.sortAndDirection,
                                    shuffled = destination.shuffle,
                                    recursive = destination.recursive,
                                    filter = destination.filter,
                                )
                            when (val r = playlistResult) {
                                is PlaylistCreationResult.Error -> {
                                    state.update {
                                        it.copy(
                                            loading = LoadingState.Error(r.message, r.ex),
                                        )
                                    }
                                    return@launchDefault
                                }

                                is PlaylistCreationResult.Success -> {
                                    if (r.playlist.items.isEmpty()) {
                                        showToast(context, "Playlist is empty", Toast.LENGTH_SHORT)
                                        navigationManager.goBack()
                                        return@launchDefault
                                    }
                                    r.playlist.items
                                        .first()
                                        .data
                                }
                            }
                        } else {
                            throw IllegalArgumentException("Item is not playable and not PlaybackList: ${queriedItem.type}")
                        }
                    val item = BaseItem(base, false)
                    val playbackConfig =
                        serverRepository.currentUser.value?.let { user ->
                            itemPlaybackDao.getItem(user, base.id)?.let {
                                Timber.v("Fetched itemPlayback from DB: %s", it)
                                if (it.sourceId != null) {
                                    it
                                } else {
                                    null
                                }
                            }
                        }
                    val mediaSource = streamChoiceService.chooseSource(base, playbackConfig)
                    val plc = streamChoiceService.getPlaybackLanguageChoice(base)
                    if (mediaSource == null) {
                        Timber.w("Media source is null")
                        return@launchDefault
                    }
                    savedStateHandle[KEY_ID] = base.id
                    savedStateHandle[KEY_MEDIA_ID] = mediaSource.id
                    val subtitleIndex =
                        streamChoiceService
                            .chooseSubtitleStream(
                                source = mediaSource,
                                audioStream = null,
                                seriesId = base.seriesId,
                                itemPlayback = playbackConfig,
                                plc = plc,
                                prefs = prefs,
                            )?.index
                    val externalSubtitles =
                        mediaSource.mediaStreams
                            ?.filter { it.isExternal }
                            ?.sortedWith(compareBy<MediaStream> { it.index == subtitleIndex }.thenBy { it.isDefault })
                            .orEmpty()
                    val subtitleUrls =
                        externalSubtitles.map {
                            val format = it.path?.let { File(it).extension } ?: "srt"
                            api.subtitleApi
                                .getSubtitleUrl(
                                    routeItemId = itemId,
                                    routeMediaSourceId = mediaSource.id!!,
                                    routeIndex = it.index,
                                    routeFormat = format,
                                ).toUri()
                        }

                    val uri =
                        api.videosApi
                            .getVideoStreamUrl(
                                itemId = item.id,
                                mediaSourceId = mediaSource.id,
                                static = true,
                            ).toUri()
                    val playerId = prefs.appPreferences.playbackPreferences.externalPlayer
                    // Make sure player is available, user could have uninstalled it
                    val foundPlayer =
                        getExternalPlayers(context).firstOrNull { it.identifier == playerId } != null
                    val component =
                        if (playerId.isNotNullOrBlank() && foundPlayer) {
                            ComponentName.unflattenFromString(playerId)
                        } else {
                            null
                        }
                    Timber.v("playerId=%s, component=%s", playerId, component)
                    val title = "${item.title} ${item.subtitleLong}"
                    val intent =
                        Intent(Intent.ACTION_VIEW).apply {
                            setComponent(component)
                            setDataAndTypeAndNormalize(uri, "video/*")
                            putExtra("title", title)
                            putExtra("position", positionMs.toInt())

                            // MX/mpv
                            putExtra("return_result", true)
                            putExtra("secure_uri", true)
                            putExtra("subs", subtitleUrls.toTypedArray())
                            putExtra(
                                "subs.name",
                                externalSubtitles
                                    .map { it.displayTitle ?: it.index.toString() }
                                    .toTypedArray(),
                            )
                            if (subtitleIndex != null) {
                                externalSubtitles
                                    .indexOfFirstOrNull { it.index == subtitleIndex }
                                    ?.let {
                                        putExtra("subs.enable", arrayOf(subtitleUrls[it]))
                                    }
                            }

                            // VLC
                            if (subtitleUrls.isNotEmpty()) {
                                putExtra("subtitles_location", subtitleUrls.first().toString())
                            }
                            mediaSource.runTimeTicks?.ticks?.inWholeMilliseconds?.let {
                                putExtra("extra_duration", it)
                            }

                            // Vimu - https://vimu.tv/player-api/
                            putExtra("startfrom", positionMs.toInt())
                            putExtra("forceresume", false)
                            putExtra("forcename", title)
                            externalSubtitles
                                .indexOfFirstOrNull { it.index == subtitleIndex && it.codec == "srt" }
                                ?.let {
                                    putExtra("forcedsrt", subtitleUrls[it])
                                }
                        }
                    api.playStateApi.reportPlaybackStart(
                        PlaybackStartInfo(
                            canSeek = false,
                            itemId = itemId,
                            isPaused = false,
                            playMethod = PlayMethod.DIRECT_PLAY,
                            repeatMode = RepeatMode.REPEAT_NONE,
                            playbackOrder = PlaybackOrder.DEFAULT,
                            isMuted = false,
                        ),
                    )
                    state.update {
                        PlayExternalState(
                            loading = LoadingState.Success,
                            intent = intent,
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error for destination %s", destination)
                    state.update {
                        it.copy(loading = LoadingState.Error(ex))
                    }
                }
            }
        }

        fun onResult(result: ActivityResult) {
            viewModelScope.launchDefault {
                val itemId = savedStateHandle.get<UUID?>(KEY_ID)
                try {
                    val mediaSourceId = savedStateHandle.get<String?>(KEY_MEDIA_ID)
                    if (itemId == null) {
                        Timber.w("itemId is null")
                        return@launchDefault
                    }
                    Timber.v(
                        "Result: result=%s, action=%s, itemId=%s",
                        result.resultCode,
                        result.data?.action,
                        itemId,
                    )
                    if (result.resultCode == Activity.RESULT_OK || result.resultCode == Activity.RESULT_CANCELED ||
                        // Vimu return 1 for video completion
                        (result.data?.action == "net.gtvbox.videoplayer.result" && result.resultCode == 1)
                    ) {
                        val position: Long?
                        val data = result.data
                        when (data?.action) {
                            // VLC: https://wiki.videolan.org/Android_Player_Intents/
                            "org.videolan.vlc.player.result" -> {
                                position =
                                    data
                                        .getLongExtra("extra_position", Long.MIN_VALUE)
                                        .takeIf { it > 0 }
                            }

                            // mpv-android: https://mpv-android.github.io/mpv-android/intent.html
                            "is.xyz.mpv.MPVActivity.result",
                            // MX player: https://mx.j2inter.com/api
                            "com.mxtech.intent.result.VIEW",
                            // VIMU: https://vimu.tv/player-api/
                            "net.gtvbox.videoplayer.result",
                            -> {
                                position =
                                    data
                                        .getIntExtra("position", Int.MIN_VALUE)
                                        .toLong()
                                        .takeIf { it >= 0 }
                            }

                            else -> {
                                // Unsupported app
                                val posInt =
                                    data
                                        ?.getIntExtra("position", Int.MIN_VALUE)
                                        ?.takeIf { it >= 0 }
                                        ?.toLong()
                                position =
                                    posInt ?: data
                                        ?.getLongExtra("position", -1L)
                                        ?.takeIf { it >= 0 }
                            }
                        }
                        Timber.v("Result position: %s", position?.milliseconds)
                        if (position != null || result.data?.action != null) {
                            api.playStateApi.reportPlaybackStopped(
                                PlaybackStopInfo(
                                    itemId = itemId,
                                    mediaSourceId = mediaSourceId,
                                    positionTicks = position?.milliseconds?.inWholeTicks,
                                    failed = false,
                                ),
                            )
                        }
                    } else {
                        Timber.w(
                            "Activity result: %s, action=%s",
                            result.resultCode,
                            result.data?.action,
                        )
                        showToast(context, "Unknown result from external player")
                    }
                    navigationManager.goBack()
                    state.update { PlayExternalState() }
                    launched.update { false }
                } catch (_: CancellationException) {
                } catch (ex: Exception) {
                    Timber.e(ex, "Error during external playback of %s", itemId)
                    state.update { it.copy(loading = LoadingState.Error(ex)) }
                }
            }
        }

        fun reportException(ex: Exception) {
            Timber.e(ex, "Error launching activity")
            state.update { it.copy(loading = LoadingState.Error(ex)) }
        }

        companion object {
            private const val KEY_ID = "itemId"
            private const val KEY_MEDIA_ID = "mediaId"
        }
    }

data class PlayExternalState(
    val loading: LoadingState = LoadingState.Pending,
    val intent: Intent = Intent(),
)

@Composable
fun PlayExternalPage(
    preferences: UserPreferences,
    destination: Destination,
    modifier: Modifier = Modifier,
    viewModel: PlayExternalViewModel =
        hiltViewModel(
            viewModelStoreOwner = LocalContext.current.findActivity() as AppCompatActivity,
        ),
) {
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
            onResult = viewModel::onResult,
        )

    val state by viewModel.state.collectAsState()
    val launched by viewModel.launched.collectAsState()
    LaunchedEffect(Unit) {
        if (!launched) {
            viewModel.init(destination)
        }
    }

    when (val l = state.loading) {
        LoadingState.Pending -> {
            LoadingPage(modifier, false)
        }

        LoadingState.Loading,
        -> {
            LoadingPage(modifier)
        }

        is LoadingState.Error -> {
            ErrorMessage(l, modifier)
        }

        LoadingState.Success -> {
            LoadingPage(modifier)
            if (!launched) {
                LifecycleStartEffect(Unit) {
                    Timber.i("Launching external playback")
                    viewModel.launched.update { true }
                    try {
                        launcher.launch(state.intent)
                    } catch (ex: Exception) {
                        viewModel.reportException(ex)
                    }
                    onStopOrDispose { }
                }
            }
        }
    }
}
