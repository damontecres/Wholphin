package com.github.damontecres.dolphin.preferences

import org.jellyfin.sdk.model.api.SubtitlePlaybackMode
import org.jellyfin.sdk.model.api.UserConfiguration

data class UserPreferences(
    val appPreferences: AppPreferences,
    val userConfig: UserConfiguration,
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
