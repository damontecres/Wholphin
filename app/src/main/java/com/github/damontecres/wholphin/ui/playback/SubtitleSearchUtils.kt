package com.github.damontecres.wholphin.ui.playback

import android.widget.Toast
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.PlaylistItem
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.services.getPreferredLanguage
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.subtitleApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.RemoteSubtitleInfo
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

sealed interface SubtitleSearchStatus {
    data object Inactive : SubtitleSearchStatus

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
fun PlaybackViewModel.searchForSubtitles(language: String? = null) {
    val language =
        language ?: getPreferredLanguage(
            MediaStreamType.SUBTITLE,
            preferences,
            serverRepository
                .currentUserDto
                ?.configuration,
        )?.takeIf { it.isNotNullOrBlank() }
            ?: Locale.current.language

    subtitleSearchState.update {
        it.copy(
            status = SubtitleSearchStatus.Searching,
            language = language,
        )
    }
    viewModelScope.launchIO {
        try {
            state.value.currentPlayback?.itemId?.let { itemId ->
                Timber.v("Searching for remote subtitles for %s", itemId)
                val results =
                    api.subtitleApi
                        .searchRemoteSubtitles(
                            itemId = itemId,
                            language = language,
                        ).content
                        .sortedWith(
                            compareByDescending<RemoteSubtitleInfo> { it.communityRating }
                                .thenByDescending { it.downloadCount },
                        )
                subtitleSearchState.update { it.copy(status = SubtitleSearchStatus.Success(results)) }
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while searching for subtitles")
            subtitleSearchState.update { it.copy(status = SubtitleSearchStatus.Error(null, ex)) }
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
        subtitleSearchState.update {
            it.copy(
                status =
                    SubtitleSearchStatus.Error(
                        "Subtitle has no ID",
                        null,
                    ),
            )
        }
    } else {
        subtitleSearchState.update { it.copy(status = SubtitleSearchStatus.Downloading) }
        viewModelScope.launchIO {
            try {
                state.value.currentPlayback?.let { currentPlayback ->
                    Timber.v(
                        "Downloading remote subtitles for itemId=%s, sourceId=%s: %s",
                        currentPlayback.itemId,
                        currentPlayback.sourceId,
                        subtitleId,
                    )
                    api.subtitleApi.downloadRemoteSubtitles(
                        itemId = currentPlayback.sourceId ?: currentPlayback.itemId,
                        subtitleId = subtitleId,
                    )
                    val currentSubtitleStreams =
                        currentPlayback.mediaSourceInfo.mediaStreams
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
                        delay(1500.milliseconds)
                        val base =
                            BaseItem(api.userLibraryApi.getItem(itemId = currentPlayback.itemId).content)
                        currentItem =
                            when (currentItem) {
                                is PlaylistItem.Intro -> PlaylistItem.Intro(base)
                                is PlaylistItem.Media -> PlaylistItem.Media(base)
                            }
                        mediaSource =
                            base.data.mediaSources?.firstOrNull { it.id?.toUUIDOrNull() == currentPlayback.sourceId }
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
                            var audioIndex = currentPlayback.audioIndex
                            if (audioIndex != TrackIndex.UNSPECIFIED) {
                                // User has picked a specific audio track
                                // Since, now adding a new external subtitle track, need to adjust the audio index as well
                                Timber.v("New external subtitle, audioIndex=$audioIndex, adding 1")
                                audioIndex += 1
                            }
                            updateCurrentMedia {
                                it.copy(
                                    subtitleStreams =
                                        subtitlesStreams.map {
                                            SimpleMediaStream.from(context.resources, it, true)
                                        },
                                )
                            }
                            this@downloadAndSwitchSubtitles.changeStreams(
                                item = currentItem.item,
                                sourceId = currentPlayback.mediaSourceInfo.id,
                                audioIndex = audioIndex,
                                subtitleIndex = newStream.index,
                                positionMs = onMain { player.currentPosition },
                                enableDirectPlay = true,
                            )
                        }
                    }
                    subtitleSearchState.update { it.copy(status = SubtitleSearchStatus.Inactive) }
                    withContext(WholphinDispatchers.Main) {
                        if (wasPlaying) {
                            player.play()
                        }
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Exception while downloading subtitles: $subtitleId")
                subtitleSearchState.update {
                    it.copy(
                        status =
                            SubtitleSearchStatus.Error(
                                null,
                                ex,
                            ),
                    )
                }
            }
        }
    }
}

fun PlaybackViewModel.cancelSubtitleSearch() {
    subtitleSearchState.update { it.copy(status = SubtitleSearchStatus.Inactive) }
}
