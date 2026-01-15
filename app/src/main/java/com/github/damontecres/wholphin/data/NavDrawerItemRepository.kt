package com.github.damontecres.wholphin.data

import android.content.Context
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.NavDrawerPinnedItem
import com.github.damontecres.wholphin.data.model.NavPinType
import com.github.damontecres.wholphin.services.WholphinPluginService
import com.github.damontecres.wholphin.services.NavDrawerItemType
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.ui.nav.CustomNavDrawerItem
import com.github.damontecres.wholphin.ui.nav.CustomNavDrawerItemType
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.NavDrawerItem
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import com.github.damontecres.wholphin.util.supportedCollectionTypes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavDrawerItemRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val serverPreferencesDao: ServerPreferencesDao,
        private val pluginService: WholphinPluginService,
        private val seerrServerRepository: SeerrServerRepository,
    ) {
        suspend fun getNavDrawerItems(): List<NavDrawerItem> {
            val user = serverRepository.currentUser.value
            val server = serverRepository.currentServer.value
            
            // Fetch all available Jellyfin libraries
            val jellyfinLibraries = fetchJellyfinLibraries()
            
            // Try to fetch plugin configuration
            val pluginConfig = server?.url?.let { serverUrl ->
                try {
                    pluginService.getNavDrawerConfiguration(serverUrl)
                } catch (e: Exception) {
                    Timber.w(e, "Error fetching nav drawer configuration")
                    null
                }
            }
            
            val builtins =
                if (seerrServerRepository.active.first()) {
                    listOf(NavDrawerItem.Favorites, NavDrawerItem.Discover)
                } else {
                    listOf(NavDrawerItem.Favorites)
                }
            
            // If plugin config is available and not empty, use it to control middle section
            return if (pluginConfig != null && pluginConfig.items.isNotEmpty()) {
                val visibleItems = applyPluginConfiguration(jellyfinLibraries, pluginConfig, visibleOnly = true)
                builtins + visibleItems
            } else {
                // Fallback to existing behavior (plugin not configured or empty config)
                builtins + jellyfinLibraries
            }
        }

        /**
         * Get items that should be hidden behind the "More" button
         * Returns hidden items when plugin config is active, or unpinned items when using local pins
         */
        suspend fun getHiddenNavDrawerItems(): List<NavDrawerItem> {
            val server = serverRepository.currentServer.value
            
            // Try to fetch plugin configuration
            val pluginConfig = server?.url?.let { serverUrl ->
                try {
                    pluginService.getNavDrawerConfiguration(serverUrl)
                } catch (e: Exception) {
                    null
                }
            }
            
            // If plugin config is active and not empty, return hidden items
            if (pluginConfig != null && pluginConfig.items.isNotEmpty()) {
                val jellyfinLibraries = fetchJellyfinLibraries()
                return applyPluginConfiguration(jellyfinLibraries, pluginConfig, visibleOnly = false)
            }
            
            // Fallback: use pin-based logic (plugin not configured or empty config)
            val allItems = getNavDrawerItems()
            return getFilteredNavDrawerItems(allItems)
        }

        /**
         * Fetch all available Jellyfin libraries and recordings
         */
        private suspend fun fetchJellyfinLibraries(): List<ServerNavDrawerItem> {
            val user = serverRepository.currentUser.value
            val tvAccess =
                serverRepository.currentUserDto.value
                    ?.policy
                    ?.enableLiveTvAccess ?: false
            val userViews =
                api.userViewsApi
                    .getUserViews(userId = user?.id)
                    .content.items
            val recordingFolders =
                if (tvAccess) {
                    api.liveTvApi
                        .getRecordingFolders(userId = user?.id)
                        .content.items
                        .map { it.id }
                        .toSet()
                } else {
                    setOf()
                }

            return userViews
                .filter { it.collectionType in supportedCollectionTypes || it.id in recordingFolders }
                .map {
                    val destination =
                        if (it.id in recordingFolders) {
                            Destination.Recordings(it.id)
                        } else {
                            BaseItem.from(it, api).destination()
                        }
                    ServerNavDrawerItem(
                        itemId = it.id,
                        name = it.name ?: it.id.toString(),
                        destination = destination,
                        type = it.collectionType ?: CollectionType.UNKNOWN,
                    )
                }
        }

        /**
         * Apply plugin configuration to control nav drawer items
         * 
         * Merges Jellyfin libraries with plugin configuration, creating custom items
         * for collections/playlists and respecting the order and visibility defined by the plugin.
         * 
         * @param visibleOnly If true, returns only visible items; if false, returns only hidden items
         */
        private suspend fun applyPluginConfiguration(
            jellyfinLibraries: List<ServerNavDrawerItem>,
            pluginConfig: com.github.damontecres.wholphin.services.NavDrawerConfiguration,
            visibleOnly: Boolean,
        ): List<NavDrawerItem> {
            val user = serverRepository.currentUser.value
            
            // Map Jellyfin libraries by ID for quick lookup
            val librariesById = jellyfinLibraries.associateBy { it.itemId }
            
            // Process plugin configuration items
            val allItems = pluginConfig.items
                .sortedBy { it.order }
                .mapNotNull { config ->
                    val itemId = try {
                        config.id.toUUID()
                    } catch (e: Exception) {
                        Timber.w("Invalid UUID in nav drawer config: ${config.id}")
                        return@mapNotNull null
                    }
                    
                    val item = when (config.type) {
                        NavDrawerItemType.LIBRARY -> {
                            // Use existing library item or create a basic one if not found
                            val existing = librariesById[itemId]
                            if (existing != null) {
                                // Override name if provided in config
                                if (config.name != null) {
                                    existing.copy(name = config.name)
                                } else {
                                    existing
                                }
                            } else {
                                // Library not found in Jellyfin, skip it
                                Timber.w("Library $itemId from plugin config not found in Jellyfin")
                                null
                            }
                        }
                        
                        NavDrawerItemType.COLLECTION, NavDrawerItemType.PLAYLIST -> {
                            // Fetch the collection/playlist item from Jellyfin
                            val item = fetchItemById(itemId, user?.id)
                            if (item != null) {
                                val destination = BaseItem.from(item, api).destination()
                                val displayName = config.name ?: item.name ?: itemId.toString()
                                val imageUrl = config.imageUrl
                                
                                CustomNavDrawerItem(
                                    itemId = itemId,
                                    itemName = displayName,
                                    destination = destination,
                                    itemType = when (config.type) {
                                        NavDrawerItemType.COLLECTION -> CustomNavDrawerItemType.COLLECTION
                                        NavDrawerItemType.PLAYLIST -> CustomNavDrawerItemType.PLAYLIST
                                        else -> CustomNavDrawerItemType.COLLECTION
                                    },
                                    imageUrl = imageUrl,
                                )
                            } else {
                                Timber.w("Collection/Playlist $itemId from plugin config not found in Jellyfin")
                                null
                            }
                        }
                    }
                    
                    // Return item with its visibility flag
                    item?.let { Pair(it, config.visible) }
                }
            
            // Filter by visibility
            val filteredItems = allItems
                .filter { (_, visible) -> visible == visibleOnly }
                .map { (item, _) -> item }
            
            // Add "More" button to visible items if there are hidden items
            return if (visibleOnly) {
                val hasHiddenItems = allItems.any { (_, visible) -> !visible }
                if (hasHiddenItems) {
                    filteredItems + listOf(NavDrawerItem.More)
                } else {
                    filteredItems
                }
            } else {
                filteredItems
            }
        }

        /**
         * Fetch a single item (collection or playlist) by ID
         */
        private suspend fun fetchItemById(itemId: UUID, userId: UUID?): BaseItemDto? {
            return try {
                api.itemsApi.getItems(
                    userId = userId,
                    ids = listOf(itemId),
                ).content.items.firstOrNull()
            } catch (e: Exception) {
                Timber.w(e, "Error fetching item $itemId")
                null
            }
        }

        suspend fun getFilteredNavDrawerItems(items: List<NavDrawerItem>): List<NavDrawerItem> {
            val user = serverRepository.currentUser.value
            val navDrawerPins =
                user
                    ?.let {
                        serverPreferencesDao.getNavDrawerPinnedItems(it)
                    }.orEmpty()
            val filtered = items.filter { navDrawerPins.isPinned(it.id) }
            if (items.size != filtered.size) {
                // Some were filtered out, check if should include More
                if (navDrawerPins.isPinned(NavDrawerItem.More.id)) {
                    return filtered + listOf(NavDrawerItem.More)
                }
            }
            return filtered
        }
    }

fun List<NavDrawerPinnedItem>.isPinned(id: String) = (firstOrNull { it.itemId == id }?.type ?: NavPinType.PINNED) == NavPinType.PINNED
