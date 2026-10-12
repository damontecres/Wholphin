package com.github.damontecres.wholphin.preferences

import com.github.damontecres.wholphin.data.model.JellyfinUserPreferences
import org.jellyfin.sdk.model.api.SubtitlePlaybackMode
import org.jellyfin.sdk.model.api.UserConfiguration

/**
 * A combination of the app-specific preferences and server-side user configuration
 */
data class UserPreferences(
    val appPreferences: AppPreferences,
    val userPreferences: JellyfinUserPreferences?,
)

val DefaultUserConfiguration =
    UserConfiguration(
        playDefaultAudioTrack = true,
        displayMissingEpisodes = false,
        groupedFolders = listOf(),
        subtitleMode = SubtitlePlaybackMode.DEFAULT,
        displayCollectionsView = false,
        enableLocalPassword = false,
        orderedViews = listOf(),
        latestItemsExcludes = listOf(),
        myMediaExcludes = listOf(),
        hidePlayedInLatest = true,
        rememberAudioSelections = true,
        rememberSubtitleSelections = true,
        enableNextEpisodeAutoPlay = true,
    )
