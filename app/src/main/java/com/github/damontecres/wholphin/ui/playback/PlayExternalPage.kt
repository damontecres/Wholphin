package com.github.damontecres.wholphin.ui.playback

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PlaylistCreationResult
import com.github.damontecres.wholphin.services.PlaylistCreator
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.extensions.inWholeTicks
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel(assistedFactory = PlayExternalViewModel.Factory::class)
class PlayExternalViewModel
    @AssistedInject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val playlistCreator: PlaylistCreator,
        private val navigationManager: NavigationManager,
        @Assisted val destination: Destination,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(destination: Destination): PlayExternalViewModel
        }

        private lateinit var item: BaseItem

        val state = MutableStateFlow(PlayExternalState())

        init {
            viewModelScope.launchDefault {
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
                    savedStateHandle["itemId"] = base.id
                    this@PlayExternalViewModel.item = BaseItem(base, false)
                    val uri =
                        api.videosApi
                            .getVideoStreamUrl(
                                itemId = item.id,
                                mediaSourceId = null, // TODO
                                static = true,
                            ).toUri()
                    val intent =
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "video/*")
                            putExtra("title", "${item.title} ${item.subtitleLong}")

                            // VLC intents: https://wiki.videolan.org/Android_Player_Intents/
                            // mxplayer intents: https://mx.j2inter.com/api
                            // mpv-android intents: https://mpv-android.github.io/mpv-android/intent.html
//                    if (externalSubtitles) {
//                        // VLC
//                        // TODO doesn't work?
//                        putExtra("subtitles_location", subUrl)
//
//                        // MX
//                        // TODO arrays of strings
//                        putExtra("subs", subs)
//                        putExtra("subs.name", subNames)
//                    }
                            // VLC
                            // TODO
                            // putExtra("extra_duration", duration)

                            // TODO starting position?
                        }

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
                val itemId = savedStateHandle.get<UUID?>("itemId")
                Timber.v("Result: itemId=%s action=%s", itemId, result.data?.action)
                if (result.resultCode == Activity.RESULT_OK) {
                    Timber.i("Activity result OK")
                    val position: Long
                    val data = result.data
                    when (data?.action) {
                        "org.videolan.vlc.player.result" -> {
                            // VLC: https://wiki.videolan.org/Android_Player_Intents/
                            position = data.getLongExtra("extra_position", -1)
                        }

                        "is.xyz.mpv.MPVActivity.result" -> {
                            // mpv-android: https://mpv-android.github.io/mpv-android/intent.html
                            position = data.getIntExtra("position", -2).toLong()
                        }

                        "com.mxtech.intent.result.VIEW" -> {
                            // MX player: https://mx.j2inter.com/api
                            position = data.getIntExtra("position", -2).toLong()
                        }

                        else -> {
                            // Unsupported app
                            val posInt =
                                data
                                    ?.getIntExtra("position", Int.MIN_VALUE)
                                    ?.takeIf { it != Int.MIN_VALUE }
                                    ?.toLong()
                            position =
                                posInt ?: (data?.getLongExtra("position", -1L) ?: -1L)
                        }
                    }
                    Timber.v("Result position: %s", position.milliseconds)
                    if (position == -2L) {
                        // TODO, check if watched
                    } else if (position >= 0 && itemId != null) {
                        api.playStateApi.reportPlaybackStopped(
                            PlaybackStopInfo(
                                itemId = itemId,
                                mediaSourceId = null, // TODO
                                positionTicks = position.milliseconds.inWholeTicks,
                                failed = false,
                            ),
                        )
                    }
                } else {
                    Timber.w("Activity result: %s", result.resultCode)
                    showToast(context, "Unknown result from external player")
                }
                navigationManager.goBack()
            }
        }
    }

data class PlayExternalState(
    val loading: LoadingState = LoadingState.Loading,
    val intent: Intent = Intent(),
)

@Composable
fun PlayExternalPage(
    preferences: UserPreferences,
    destination: Destination,
    modifier: Modifier = Modifier,
    viewModel: PlayExternalViewModel =
        hiltViewModel<PlayExternalViewModel, PlayExternalViewModel.Factory>(
            creationCallback = { it.create(destination) },
        ),
) {
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
            onResult = viewModel::onResult,
        )

    val state by viewModel.state.collectAsState()
    var launched by rememberSaveable { mutableStateOf(false) }

    when (val l = state.loading) {
        LoadingState.Pending,
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
                    launched = true
                    launcher.launch(state.intent)
                    onStopOrDispose { }
                }
            }
        }
    }
}
