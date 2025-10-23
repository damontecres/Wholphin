package com.github.damontecres.wholphin.data

import android.content.Context
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.NavDrawerPinnedItem
import com.github.damontecres.wholphin.data.model.NavPinType
import com.github.damontecres.wholphin.ui.nav.NavDrawerItem
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import com.github.damontecres.wholphin.util.supportedCollectionTypes
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.api.client.ApiClient
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
    ) {
        suspend fun getNavDrawerItems(): List<NavDrawerItem> {
            val user = serverRepository.currentUser
            val userViews =
                api.userViewsApi
                    .getUserViews(userId = user?.id)
                    .content.items

            val builtins = listOf(NavDrawerItem.Favorites)
            val libraries =
                userViews
                    .filter { it.collectionType in supportedCollectionTypes }
                    .map {
                        ServerNavDrawerItem(
                            itemId = it.id,
                            name = it.name ?: it.id.toString(),
                            destination = BaseItem.from(it, api).destination(),
                            type = it.collectionType ?: CollectionType.UNKNOWN,
                        )
                    }
            return builtins + libraries
        }

        suspend fun getFilteredNavDrawerItems(): List<NavDrawerItem> {
            val user = serverRepository.currentUser
            val navDrawerPins =
                user
                    ?.let {
                        serverPreferencesDao.getNavDrawerPinnedItems(it)
                    }.orEmpty()
            return getNavDrawerItems().filter { navDrawerPins.isPinned(it.id) }
        }
    }

fun List<NavDrawerPinnedItem>.isPinned(id: String) = (firstOrNull { it.itemId == id }?.type ?: NavPinType.PINNED) == NavPinType.PINNED
