package com.github.damontecres.wholphin.util.profile

import androidx.core.net.toUri
import com.github.damontecres.wholphin.preferences.PlayerBackend
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType

fun DeviceProfile.withPostPlaybackInfoFixes(
    playerBackend: PlayerBackend,
    source: MediaSourceInfo,
    audioIndex: Int?,
): DeviceProfile =
    withEac3HlsPassthroughBugFix(
        playerBackend = playerBackend,
        source = source,
        audioIndex = audioIndex,
    )

private fun DeviceProfile.withEac3HlsPassthroughBugFix(
    playerBackend: PlayerBackend,
    source: MediaSourceInfo,
    audioIndex: Int?,
): DeviceProfile {
    if (playerBackend != PlayerBackend.EXO_PLAYER) {
        return this
    }

    val selectedAudioIndex = audioIndex ?: source.defaultAudioStreamIndex
    val selectedAudio =
        source.mediaStreams
            .orEmpty()
            .firstOrNull {
                it.type == MediaStreamType.AUDIO &&
                    (selectedAudioIndex == null || it.index == selectedAudioIndex)
            }

    return if (source.requiresEac3HlsAudioTranscoding(selectedAudio)) {
        withAacHlsAudio()
    } else {
        this
    }
}

private fun MediaSourceInfo.requiresEac3HlsAudioTranscoding(audioStream: MediaStream?): Boolean =
    !supportsDirectPlay &&
        KnownDefects.eac3HlsPassthroughBug &&
        audioStream?.codec.equals(Codec.Audio.EAC3, ignoreCase = true) &&
        (audioStream?.channels ?: 0) >= 8 &&
        transcodingUrl
            ?.toUri()
            ?.getQueryParameter("AudioCodec")
            ?.split(',')
            ?.any { it.equals(Codec.Audio.EAC3, ignoreCase = true) } == true &&
        transcodingSubProtocol == MediaStreamProtocol.HLS &&
        transcodingContainer.equals(Codec.Container.TS, ignoreCase = true)

private fun DeviceProfile.withAacHlsAudio(): DeviceProfile =
    copy(
        transcodingProfiles =
            transcodingProfiles.map { profile ->
                if (
                    profile.type == DlnaProfileType.VIDEO &&
                    profile.protocol == MediaStreamProtocol.HLS &&
                    profile.container.equals(Codec.Container.TS, ignoreCase = true)
                ) {
                    profile.copy(audioCodec = Codec.Audio.AAC)
                } else {
                    profile
                }
            },
    )
