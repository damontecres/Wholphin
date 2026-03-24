package com.github.damontecres.wholphin.ui.detail.music

import androidx.compose.runtime.Stable
import com.github.damontecres.wholphin.services.BackdropResult
import com.github.damontecres.wholphin.services.MusicServiceState
import org.jellyfin.sdk.model.api.LyricDto

@Stable
data class NowPlayingState(
    val musicServiceState: MusicServiceState,
    val lyrics: LyricDto? = null,
    val currentLyricIndex: Int? = null,
    val visualizerPermissions: Boolean = false,
    val backdropResult: BackdropResult = BackdropResult.NONE,
) {
    val hasLyrics: Boolean get() = lyrics != null && lyrics.lyrics.isNotEmpty()
}
