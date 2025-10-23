package com.github.damontecres.wholphin.data

import android.content.Context
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.NavDrawerPinnedItem
import com.github.damontecres.wholphin.data.model.NavPinType
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.NavDrawerItem
import com.github.damontecres.wholphin.util.supportedCollectionTypes
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.CollectionType
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
    ) {
        suspend fun getNavDrawerItems(): List<NavDrawerItem> {
            val user = serverRepository.currentUser
            val navDrawerPins =
                user
                    ?.let {
                        serverPreferencesDao.getNavDrawerPinnedItems(it)
                    }.orEmpty()
            val userViews =
                api.userViewsApi
                    .getUserViews(userId = user?.id)
                    .content.items

            val libraries =
                userViews
                    .filter { it.collectionType in supportedCollectionTypes }
                    .filter {
                        val id = NavDrawerPinnedItem.idFor(it)
                        navDrawerPins.isPinned(id)
                    }.map {
                        NavDrawerItem(
                            id = it.id,
                            name = it.name ?: it.id.toString(),
                            destination = BaseItem.from(it, api).destination(),
                            type = it.collectionType ?: CollectionType.UNKNOWN,
                            iconStringRes = null,
                        )
                    }
            val extra =
                buildList {
                    if (navDrawerPins.isPinned(NavDrawerPinnedItem.FAVORITES_ID)) {
                        add(
                            NavDrawerItem(
                                id = UUID.randomUUID(),
                                name = context.getString(R.string.favorites),
                                destination = Destination.Favorites,
                                type = CollectionType.UNKNOWN,
                                iconStringRes = R.string.fa_heart,
                            ),
                        )
                    }
                }
            Timber.d("Got ${userViews.size} user views filtered to ${libraries.size}")
            return extra + libraries
        }
    }

fun List<NavDrawerPinnedItem>.isPinned(id: String) = (firstOrNull { it.itemId == id }?.type ?: NavPinType.PINNED) == NavPinType.PINNED
