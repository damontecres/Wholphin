package com.github.damontecres.wholphin.ui.playback

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.test.TestTracks
import com.github.damontecres.wholphin.test.movie
import com.github.damontecres.wholphin.util.profile.Codec
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.deviceprofile.buildDeviceProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoveryTest {
    private val varintError = RuntimeException(IllegalStateException("No valid varint length mask found"))
    private val source =
        TestTracks
            .Builder()
            .addVideo()
            .addAudio()
            .addSubtitle()
            .buildMediaSourceInfo()
    private val subtitle =
        source.mediaStreams!!
            .first { it.type == MediaStreamType.SUBTITLE }
            .copy(codec = Codec.Subtitle.SUBRIP, supportsExternalStream = true)
    private val playback =
        CurrentPlayback(
            item = BaseItem(movie()),
            tracks = emptyList(),
            backend = PlayerBackend.EXO_PLAYER,
            playMethod = PlayMethod.DIRECT_PLAY,
            playSessionId = null,
            liveStreamId = null,
            mediaSourceInfo = source.copy(container = Codec.Container.MKV, mediaStreams = listOf(subtitle)),
            subtitleIndex = subtitle.index,
        )

    @Test
    fun `Wrapped Matroska varint failures can retry extractable SubRip subtitles`() {
        for (codec in listOf(Codec.Subtitle.SRT, Codec.Subtitle.SUBRIP)) {
            val current =
                playback.copy(
                    mediaSourceInfo = playback.mediaSourceInfo.copy(mediaStreams = listOf(subtitle.copy(codec = codec))),
                )
            assertTrue(current.canRetryWithExternalSubtitles(varintError))
        }
    }

    @Test
    fun `Other playback failures retain normal fallback`() {
        assertFalse(playback.canRetryWithExternalSubtitles(IllegalStateException("decoder failure")))
        assertFalse(playback.copy(subtitleIndex = -1).canRetryWithExternalSubtitles(varintError))
        assertFalse(playback.copy(backend = PlayerBackend.MPV).canRetryWithExternalSubtitles(varintError))
        assertFalse(playback.copy(playMethod = PlayMethod.TRANSCODE).canRetryWithExternalSubtitles(varintError))
        assertFalse(playback.copy(playMethod = PlayMethod.DIRECT_STREAM).canRetryWithExternalSubtitles(varintError))
        assertFalse(
            playback
                .copy(
                    mediaSourceInfo = playback.mediaSourceInfo.copy(container = Codec.Container.MP4),
                ).canRetryWithExternalSubtitles(varintError),
        )
        for (stream in listOf(
            subtitle.copy(supportsExternalStream = false),
            subtitle.copy(codec = Codec.Subtitle.ASS),
            subtitle.copy(deliveryMethod = SubtitleDeliveryMethod.EXTERNAL),
            subtitle.copy(isExternal = true),
        )) {
            assertFalse(
                playback
                    .copy(
                        mediaSourceInfo = playback.mediaSourceInfo.copy(mediaStreams = listOf(stream)),
                    ).canRetryWithExternalSubtitles(varintError),
            )
        }
    }

    @Test
    fun `Recovery excludes convertible embedded formats without changing cached profile`() {
        val original =
            buildDeviceProfile {
                subtitleProfile(Codec.Subtitle.SUBRIP, SubtitleDeliveryMethod.EMBED)
                subtitleProfile(Codec.Subtitle.SUBRIP, SubtitleDeliveryMethod.EXTERNAL)
                subtitleProfile(Codec.Subtitle.WEBVTT, SubtitleDeliveryMethod.EMBED)
                subtitleProfile(Codec.Subtitle.PGS, SubtitleDeliveryMethod.ENCODE)
            }
        val recovered = original.withExternalSubtitles()
        assertTrue(original.subtitleProfiles.any { it.method == SubtitleDeliveryMethod.EMBED })
        assertFalse(recovered.subtitleProfiles.any { it.method == SubtitleDeliveryMethod.EMBED })
        assertTrue(recovered.subtitleProfiles.any { it.method == SubtitleDeliveryMethod.EXTERNAL && it.format == Codec.Subtitle.SUBRIP })
        assertTrue(recovered.subtitleProfiles.any { it.method == SubtitleDeliveryMethod.ENCODE && it.format == Codec.Subtitle.PGS })
    }
}
