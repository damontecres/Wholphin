package com.github.damontecres.wholphin.ui.playback

import android.widget.Toast
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.PlaylistItem
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.subtitleApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.RemoteSubtitleInfo
import timber.log.Timber

sealed interface SubtitleSearchStatus {
    data object Searching : SubtitleSearchStatus

    data object Downloading : SubtitleSearchStatus

    data class Success(
        val options: List<RemoteSubtitleInfo>,
    ) : SubtitleSearchStatus

    data class Error(
        val message: String?,
        val ex: Exception?,
    ) : SubtitleSearchStatus
}

/**
 * Trigger a search for subtitles in the given language for the currently playing media
 */
fun PlaybackViewModel.searchForSubtitles(language: String = Locale.current.language) {
    subtitleSearchStatus.value = SubtitleSearchStatus.Searching
    subtitleSearchLanguage.value = language
    viewModelScope.launchIO {
        try {
            currentItemPlayback.value?.itemId?.let {
                Timber.v("Searching for remote subtitles for %s", it)
                val results =
                    api.subtitleApi
                        .searchRemoteSubtitles(
                            itemId = it,
                            language = language,
                        ).content
                        .sortedWith(
                            compareByDescending<RemoteSubtitleInfo> { it.communityRating }
                                .thenByDescending { it.downloadCount },
                        )
                subtitleSearchStatus.setValueOnMain(SubtitleSearchStatus.Success(results))
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while searching for subtitles")
            subtitleSearchStatus.setValueOnMain(SubtitleSearchStatus.Error(null, ex))
        }
    }
}

/**
 * Download the remote subtitles and attempt to activate them once complete
 */
fun PlaybackViewModel.downloadAndSwitchSubtitles(
    subtitleId: String?,
    wasPlaying: Boolean,
) {
    if (subtitleId == null) {
        subtitleSearchStatus.value = SubtitleSearchStatus.Error("Subtitle has no ID", null)
    } else {
        subtitleSearchStatus.value = SubtitleSearchStatus.Downloading
        viewModelScope.launchIO {
            try {
                currentItemPlayback.value?.let {
                    Timber.v(
                        "Downloading remote subtitles for itemId=%s, sourceId=%s: %s",
                        it.itemId,
                        it.sourceId,
                        subtitleId,
                    )
                    api.subtitleApi.downloadRemoteSubtitles(
                        itemId = it.sourceId ?: it.itemId,
                        subtitleId = subtitleId,
                    )
                    val currentSource =
                        this@downloadAndSwitchSubtitles.currentPlayback.value?.mediaSourceInfo
                    val currentSubtitleStreams =
                        currentSource
                            ?.mediaStreams
                            ?.filter { it.type == MediaStreamType.SUBTITLE }
                            .orEmpty()
                    val externalPaths = currentSubtitleStreams.map { it.path }

                    val subtitleCount = currentSubtitleStreams.size
                    var newCount = subtitleCount
                    var maxAttempts = 4

                    var mediaSource: MediaSourceInfo? = null
                    // The server triggers a refresh in the background, so query periodically for the item until its updated
                    while (maxAttempts > 0 && subtitleCount == newCount) {
                        maxAttempts--
                        delay(1500)
                        val base = BaseItem(api.userLibraryApi.getItem(itemId = it.itemId).content)
                        currentItem =
                            when (currentItem) {
                                is PlaylistItem.Intro -> PlaylistItem.Intro(base)
                                is PlaylistItem.Media -> PlaylistItem.Media(base)
                            }
                        mediaSource = streamChoiceService.chooseSource(currentItem.item.data, it)
                        if (mediaSource == null) {
                            // This shouldn't happen, but just in case
                            showToast(
                                context,
                                "Item is no longer playable...",
                                Toast.LENGTH_SHORT,
                            )
                            return@launchIO
                        }

                        val subtitleStreams =
                            mediaSource.mediaStreams
                                ?.filter { it.type == MediaStreamType.SUBTITLE }
                                .orEmpty()
                        newCount = subtitleStreams.size
                    }
                    if (maxAttempts == 0) {
                        showToast(
                            context,
                            context.getString(R.string.subtitle_download_too_long),
                        )
                    } else {
                        // Find the new subtitle stream
                        val subtitlesStreams =
                            mediaSource?.mediaStreams?.filter { it.type == MediaStreamType.SUBTITLE }
                        val newStream =
                            subtitlesStreams?.firstOrNull { stream ->
                                stream.isExternal && stream.path !in externalPaths
                            }
                        if (newStream != null) {
                            var audioIndex = currentItemPlayback.value?.audioIndex
                            if (audioIndex != null && audioIndex != TrackIndex.UNSPECIFIED) {
                                // User has picked a specific audio track
                                // Since, now adding a new external subtitle track, need to adjust the audio index as well
                                Timber.v("New external subtitle, audioIndex=$audioIndex, adding 1")
                                audioIndex += 1
                            }
                            updateCurrentMedia {
                                it.copy(
                                    subtitleStreams =
                                        subtitlesStreams.map {
                                            SimpleMediaStream.from(context, it, true)
                                        },
                                )
                            }
                            this@downloadAndSwitchSubtitles.changeStreams(
                                currentItem.item,
                                currentItemPlayback.value!!,
                                audioIndex,
                                newStream.index,
                                onMain { player.currentPosition },
                                true,
                            )
                        }
                    }
                    subtitleSearchStatus.setValueOnMain(null)
                    withContext(Dispatchers.Main) {
                        if (wasPlaying) {
                            player.play()
                        }
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Exception while downloading subtitles: $subtitleId")
                subtitleSearchStatus.setValueOnMain(SubtitleSearchStatus.Error(null, ex))
            }
        }
    }
}

fun PlaybackViewModel.cancelSubtitleSearch() {
    subtitleSearchStatus.value = null
}
