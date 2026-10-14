package com.github.damontecres.wholphin.services

import android.content.Context
import android.widget.Toast
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.showToast
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetPlaylistItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves items to a private "Watch later" server playlist for the current user.
 *
 * The playlist is found by name (case-insensitive) and created on first use.
 * Episodes and seasons are saved as their series.
 */
@Singleton
class WatchLaterService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val playlistCreator: PlaylistCreator,
    ) {
        private val mutex = Mutex()

        suspend fun add(item: BaseItem) =
            mutex.withLock {
                try {
                    val target = targetFor(item)
                    if (target == null) {
                        showToast(context, "Can't save this item", Toast.LENGTH_SHORT)
                        return@withLock
                    }
                    val (itemId, title) = target
                    val playlistId = findPlaylist()
                    if (playlistId == null) {
                        playlistCreator.createServerPlaylist(PLAYLIST_NAME, listOf(itemId))
                        showToast(context, "✓ $title saved to $PLAYLIST_NAME", Toast.LENGTH_SHORT)
                    } else if (contains(playlistId, itemId)) {
                        showToast(context, "$title is already in $PLAYLIST_NAME", Toast.LENGTH_SHORT)
                    } else {
                        playlistCreator.addToServerPlaylist(playlistId, itemId)
                        showToast(context, "✓ $title saved to $PLAYLIST_NAME", Toast.LENGTH_SHORT)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error saving %s to watch later", item.id)
                    showToast(context, "Error: ${ex.localizedMessage}", Toast.LENGTH_SHORT)
                }
            }

        /** Episodes and seasons are saved as their series */
        private fun targetFor(item: BaseItem): Pair<UUID, String>? =
            when (item.type) {
                BaseItemKind.EPISODE, BaseItemKind.SEASON ->
                    item.data.seriesId?.let { it to (item.data.seriesName ?: item.title ?: "") }
                BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.VIDEO, BaseItemKind.MUSIC_VIDEO ->
                    item.id to (item.title ?: "")
                else -> null
            }

        private suspend fun findPlaylist(): UUID? =
            playlistCreator
                .getServerPlaylists(PLAYLIST_NAME, null)
                .firstOrNull { it.name.equals(PLAYLIST_NAME, ignoreCase = true) }
                ?.id

        private suspend fun contains(
            playlistId: UUID,
            itemId: UUID,
        ): Boolean {
            val userId = serverRepository.currentUser?.id
            val items =
                api.playlistsApi
                    .getPlaylistItems(GetPlaylistItemsRequest(playlistId = playlistId, userId = userId))
                    .content.items
            return items.any { it.id == itemId }
        }

        companion object {
            const val PLAYLIST_NAME = "Watch later"
        }
    }
