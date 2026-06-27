package com.github.damontecres.wholphin.ui.playback

import androidx.annotation.OptIn
import androidx.compose.ui.text.intl.Locale
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.Playlist
import com.github.damontecres.wholphin.data.model.PlaylistItem
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.ui.formatBitrate
import com.github.damontecres.wholphin.util.LoadingState
import io.github.peerless2012.ass.media.AssHandler
import org.jellyfin.sdk.model.api.MediaSegmentDto
import java.util.TreeSet
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

data class PlaybackState(
    val loading: LoadingState = LoadingState.Loading,
    val currentMediaInfo: CurrentMediaInfo = CurrentMediaInfo.EMPTY,
    val currentPlayback: CurrentPlayback? = null,
    val currentItemPlayback: ItemPlayback? = null,
    val currentSegment: MediaSegmentState? = null,
    val analyticsState: AnalyticsState = AnalyticsState(),
    val subtitleCues: List<Cue> = emptyList(),
    val nextUp: BaseItem? = null,
    val playlistIndex: Int = 0,
    val playlist: Playlist = Playlist(emptyList()),
    val externalCues: ExternalCues = ExternalCues(),
) {
    val hasNext: Boolean get() = (playlistIndex + 1) < playlist.items.size
    val hasPrevious: Boolean get() = playlistIndex > 0
    val upcomingItems: List<PlaylistItem>
        get() =
            playlist.items.subList(
                (playlistIndex + 1).coerceAtMost(playlist.items.size),
                playlist.items.size,
            )

    fun nextItem(): PlaylistItem? = playlist.items.getOrNull(playlistIndex + 1)
}

@OptIn(UnstableApi::class)
data class ExternalCues(
    val active: Boolean = false,
    val cues: List<CuesWithTiming> = emptyList(),
) {
    /**
     * Cues organized by each minute they are in
     */
    private val cueMap =
        buildMap {
            val comparator: Comparator<CuesWithTiming> =
                compareBy({ it.startTimeUs }, { it.endTimeUs })
            cues.forEach { cue ->
                val start = cue.startTimeUs.microseconds.inWholeMinutes
                val end = cue.endTimeUs.microseconds.inWholeMinutes
                (start..end).forEach {
                    getOrPut(it) { TreeSet(comparator) }.add(cue)
                }
            }
        }

    fun getCuesAt(positionUs: Long): List<CuesWithTiming> {
        val minute = positionUs.microseconds.inWholeMinutes
        val cues = cueMap[minute].orEmpty()
        return cues.filter {
            positionUs in it.startTimeUs..it.endTimeUs
        }
    }
}

data class PlayerInstance(
    val player: Player,
    val backend: PlayerBackend,
    val assHandler: AssHandler?,
)

data class MediaSegmentState(
    val segment: MediaSegmentDto,
    val interacted: Boolean,
)

data class AnalyticsState(
    val bitrate: String = formatBitrate(0),
    val bitrateEstimate: String = formatBitrate(0),
    val droppedFrames: Int = 0,
)

data class SubtitleSearchState(
    val status: SubtitleSearchStatus = SubtitleSearchStatus.Inactive,
    val language: String = Locale.current.language,
)

data class ExternalCue(
    val cues: List<Cue>,
    val originalStartUs: Long,
    val originalEndUs: Long,
    val startTimeUs: Long = originalStartUs,
    val endTimeUs: Long = originalEndUs,
) {
    @OptIn(UnstableApi::class)
    constructor(c: CuesWithTiming) : this(c.cues, c.startTimeUs, c.endTimeUs)

    fun withDelay(delay: Duration): ExternalCue {
        val us = delay.inWholeMicroseconds
        return copy(
            startTimeUs = originalStartUs + us,
            endTimeUs = originalEndUs + us,
        )
    }
}
