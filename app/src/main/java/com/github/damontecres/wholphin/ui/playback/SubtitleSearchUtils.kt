package com.github.damontecres.wholphin.ui.playback

import android.widget.Toast
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.ui.formatMediaStream
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

sealed interface SubtitleSearch {
    data object Searching : SubtitleSearch

    data object Downloading : SubtitleSearch

    data class Success(
        val options: List<RemoteSubtitleInfo>,
    ) : SubtitleSearch

    data class Error(
        val message: String?,
        val ex: Exception?,
    ) : SubtitleSearch
}

fun PlaybackViewModel.searchForSubtitles(language: String = Locale.current.language) {
    subtitleSearch.value = SubtitleSearch.Searching
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
                subtitleSearch.setValueOnMain(SubtitleSearch.Success(results))
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while searching for subtitles")
            subtitleSearch.setValueOnMain(SubtitleSearch.Error(null, ex))
        }
    }
}

fun PlaybackViewModel.downloadAndSwitchSubtitles(
    subtitleId: String?,
    wasPlaying: Boolean,
) {
    if (subtitleId == null) {
        subtitleSearch.value = SubtitleSearch.Error("Subtitle has no ID", null)
    } else {
        subtitleSearch.value = SubtitleSearch.Downloading
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
                        item =
                            BaseItem.from(
                                api.userLibraryApi.getItem(itemId = it.itemId).content,
                                api,
                            )
                        mediaSource = streamChoiceService.chooseSource(item.data, it)
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
                                            SimpleMediaStream(
                                                it.index,
                                                it.title,
                                                formatMediaStream(context, it, true),
                                            )
                                        },
                                )
                            }
                            this@downloadAndSwitchSubtitles.changeStreams(
                                item,
                                currentItemPlayback.value!!,
                                audioIndex,
                                newStream.index,
                                onMain { player.currentPosition },
                                true,
                            )
                        }
                    }
                    subtitleSearch.setValueOnMain(null)
                    withContext(Dispatchers.Main) {
                        if (wasPlaying) {
                            player.play()
                        }
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Exception while downloading subtitles: $subtitleId")
                subtitleSearch.setValueOnMain(SubtitleSearch.Error(null, ex))
            }
        }
    }
}

fun PlaybackViewModel.cancelSubtitleSearch() {
    subtitleSearch.value = null
}
