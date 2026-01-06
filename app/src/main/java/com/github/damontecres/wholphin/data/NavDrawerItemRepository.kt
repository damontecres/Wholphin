package com.github.damontecres.wholphin.data

import android.content.Context
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.NavDrawerPinnedItem
import com.github.damontecres.wholphin.data.model.NavPinType
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.NavDrawerItem
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import com.github.damontecres.wholphin.util.supportedCollectionTypes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.CollectionType
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
        private val seerrServerRepository: SeerrServerRepository,
    ) {
        suspend fun getNavDrawerItems(): List<NavDrawerItem> {
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

            val builtins =
                if (seerrServerRepository.active.first()) {
                    listOf(NavDrawerItem.Favorites, NavDrawerItem.Discover)
                } else {
                    listOf(NavDrawerItem.Favorites)
                }

            val libraries =
                userViews
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
            return builtins + libraries
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
