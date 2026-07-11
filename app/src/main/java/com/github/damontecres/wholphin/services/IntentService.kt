package com.github.damontecres.wholphin.services

import android.app.SearchManager
import android.content.Intent
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisodeIds
import com.github.damontecres.wholphin.ui.nav.Destination
import kotlinx.coroutines.flow.first
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntentService
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val userPreferencesService: UserPreferencesService,
    ) {
        suspend fun parseIntent(intent: Intent): IntentResult {
            Timber.v("Parsing intent %s", intent)
            Timber.v("Intent extras: %s", intent.extras)
            Timber.v("Intent data: %s", intent.data)
            if (intent.getStringParam("type") != null) {
                return getDestinationFromChannel(intent)?.let { IntentResult.Target(it) }
                    ?: IntentResult.Error("Invalid parameters")
            }
            val action = intent.action ?: intent.data?.host
            if (action == Intent.ACTION_MAIN || action.isNullOrBlank()) {
                return IntentResult.NoOp
            }

            val userId = intent.getStringParam("userId")?.toUUIDOrNull()
            val serverId = intent.getStringParam("serverId")?.toUUIDOrNull()
            if (userId != null && serverId != null) {
                Timber.v("Intent switches user")
                val user =
                    serverRepository.serverDao.getUser(serverId, userId)
                        ?: return IntentResult.Error("User not found")
                if (!user.isProtected) {
                    serverRepository.restoreSession(serverId, userId)
                } else {
                    return IntentResult.Error("Cannot switch to specified user")
                }
            } else if (serverRepository.currentUser == null) {
                Timber.v("No current user, so restoring last")
                val appPrefs = userPreferencesService.flow.first().appPreferences
                if (appPrefs.signInAutomatically) {
                    val userId = appPrefs.currentUserId.toUUIDOrNull()
                    val serverId = appPrefs.currentServerId.toUUIDOrNull()
                    if (userId != null && serverId != null) {
                        serverRepository.restoreSession(serverId, userId)
                            ?: return IntentResult.Error("Error restoring user")
                    } else {
                        return IntentResult.Error("Could not auto sign-in, specify a server & user")
                    }
                } else {
                    return IntentResult.Error("Auto sign-in not enabled, specify a server & user")
                }
            }

            if (action == Intent.ACTION_SEARCH || action == "search") {
                val query = intent.getStringParam(SearchManager.QUERY)
                // TODO add query
                return IntentResult.Target(Destination.Search)
            }
            val itemId =
                intent.getStringParam("itemId")?.toUUIDOrNull()
                    ?: return IntentResult.Error("No item id provided")

            val item =
                try {
                    api.userLibraryApi
                        .getItem(itemId)
                        .content
                        .let { BaseItem(it) }
                } catch (ex: Exception) {
                    Timber.w(ex, "Error fetching item %s", itemId)
                    return IntentResult.Error("Could not fetch item $itemId")
                }

            val destination =
                when (action) {
                    Intent.ACTION_VIEW, "view" -> {
                        item.destination()
                    }

                    "com.github.damontecres.wholphin.PLAYBACK", "play" -> {
                        val position = intent.getLongParam("position")?.coerceAtLeast(0)
                        Destination.Playback(
                            itemId = itemId,
                            positionMs = position ?: 0L,
                        )
                    }

                    else -> {
                        return IntentResult.Error("Invalid action: ${intent.action}")
                    }
                }
            return IntentResult.Target(destination)
        }

        private fun Intent.getStringParam(key: String) = getStringExtra(key) ?: data?.getQueryParameter(key)

        private fun Intent.getLongParam(key: String) =
            getLongExtra(key, -1).takeIf { it >= 0 } ?: data?.getQueryParameter(key)?.toLongOrNull()

        private fun getDestinationFromChannel(intent: Intent): Destination? =
            intent.let {
                val itemId =
                    it.getStringExtra(INTENT_ITEM_ID)?.toUUIDOrNull()
                val type =
                    it.getStringExtra(INTENT_ITEM_TYPE)?.let(BaseItemKind::fromNameOrNull)
                if (itemId != null && type != null) {
                    val seriesId = it.getStringExtra(INTENT_SERIES_ID)?.toUUIDOrNull()
                    val seasonId = it.getStringExtra(INTENT_SEASON_ID)?.toUUIDOrNull()
                    val episodeNumber = it.getIntExtra(INTENT_EPISODE_NUMBER, -1)
                    val seasonNumber = it.getIntExtra(INTENT_SEASON_NUMBER, -1)
                    if (seriesId != null && seasonId != null && episodeNumber >= 0 && seasonNumber >= 0) {
                        Destination.SeriesOverview(
                            itemId = seriesId,
                            type = BaseItemKind.SERIES,
                            seasonEpisode =
                                SeasonEpisodeIds(
                                    seasonId = seasonId,
                                    seasonNumber = seasonNumber,
                                    episodeId = itemId,
                                    episodeNumber = episodeNumber,
                                ),
                        )
                    } else {
                        Destination.MediaItem(itemId, type)
                    }
                } else {
                    null
                }
            }

        companion object {
            const val INTENT_ITEM_ID = "itemId"
            const val INTENT_ITEM_TYPE = "itemType"
            const val INTENT_SERIES_ID = "seriesId"
            const val INTENT_EPISODE_NUMBER = "epNum"
            const val INTENT_SEASON_NUMBER = "seaNum"
            const val INTENT_SEASON_ID = "seaId"
        }
    }

sealed interface IntentResult {
    data class Error(
        val message: String,
    ) : IntentResult

    data object NoOp : IntentResult

    data class Target(
        val destination: Destination,
    ) : IntentResult
}
