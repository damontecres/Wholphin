package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.lifecycle.asFlow
import com.github.damontecres.wholphin.data.ServerPreferencesDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.NavPinType
import com.github.damontecres.wholphin.services.hilt.DefaultCoroutineScope
import com.github.damontecres.wholphin.ui.main.settings.Library
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.NavDrawerItem
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.supportedCollectionTypes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.UserDto
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavDrawerService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:DefaultCoroutineScope private val coroutineScope: CoroutineScope,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val serverPreferencesDao: ServerPreferencesDao,
        private val seerrServerRepository: SeerrServerRepository,
    ) {
        private val _state = MutableStateFlow(NavDrawerItemState.EMPTY)
        val state: StateFlow<NavDrawerItemState> = _state

        init {
            serverRepository.currentUser
                .asFlow()
                .combine(serverRepository.currentUserDto.asFlow()) { user, userDto ->
                    Pair(user, userDto)
                }.onEach { (user, userDto) ->
                    Timber.d("User updated: user=%s, userDto=%s", user?.id, userDto?.id)
                    _state.update {
                        it.copy(
                            items = emptyList(),
                            moreItems = emptyList(),
                        )
                    }
                    if (user != null && userDto != null && user.id == userDto.id) {
                        updateNavDrawer(user, userDto)
                    }
                }.catch { ex ->
                    Timber.e(ex, "Error updating nav drawer")
                    showToast(context, "Error fetching user's views")
                }.launchIn(coroutineScope)

            seerrServerRepository.active
                .onEach { discoverActive ->
                    _state.update { it.copy(discoverEnabled = discoverActive) }
                }.launchIn(coroutineScope)
        }

        suspend fun getAllUserLibraries(
            userId: UUID,
            tvAccess: Boolean,
        ): List<Library> {
            val userViews =
                api.userViewsApi
                    .getUserViews(userId = userId)
                    .content.items
            val recordingFolders =
                if (tvAccess) {
                    api.liveTvApi
                        .getRecordingFolders(userId = userId)
                        .content.items
                        .map { it.id }
                        .toSet()
                } else {
                    setOf()
                }
            val libraries =
                userViews
                    .filter { it.collectionType in supportedCollectionTypes || it.id in recordingFolders }
                    .map {
                        Library(
                            itemId = it.id,
                            name = it.name ?: "",
                            type = it.type,
                            collectionType = it.collectionType ?: CollectionType.UNKNOWN,
                            isRecordingFolder = it.id in recordingFolders,
                        )
                    }
            return libraries
        }

        suspend fun getFilteredUserLibraries(
            user: JellyfinUser,
            tvAccess: Boolean,
        ): List<Library> {
            val pins =
                serverPreferencesDao
                    .getNavDrawerPinnedItems(user)
                    .associateBy { it.itemId }
            val libraries =
                getAllUserLibraries(user.id, tvAccess)
                    .filterNot { pins[ServerNavDrawerItem.getId(it.itemId)]?.type == NavPinType.UNPINNED }
            return libraries
        }

        suspend fun updateNavDrawer(
            user: JellyfinUser,
            userDto: UserDto,
        ) {
            val builtins = listOf(NavDrawerItem.Favorites, NavDrawerItem.Discover)
            val allLibraries = getAllUserLibraries(user.id, userDto.tvAccess)
            val libraries =
                allLibraries
                    .map {
                        val destination =
                            if (it.isRecordingFolder) {
                                Destination.Recordings(it.itemId)
                            } else {
                                Destination.MediaItem(
                                    it.itemId,
                                    it.type,
                                    it.collectionType,
                                )
                            }
                        ServerNavDrawerItem(
                            itemId = it.itemId,
                            name = it.name,
                            destination = destination,
                            type = it.collectionType,
                        )
                    }
            val allItems = builtins + libraries

            val navDrawerPins =
                serverPreferencesDao.getNavDrawerPinnedItems(user).associateBy { it.itemId }

            val items = mutableListOf<NavDrawerItem>()
            val moreItems = mutableListOf<NavDrawerItem>()
            allItems
                // Sort by order if non-default, existing items before customize will have -1 value
                // New items from the server will get Int.MAX_VALUE
                // Items the user doesn't have access to anymore will be skipped
                .sortedBy { navDrawerPins[it.id]?.order?.takeIf { it >= 0 } ?: Int.MAX_VALUE }
                .forEach {
                    // Assume pinned if unknown
                    val pinned = navDrawerPins[it.id]?.type ?: NavPinType.PINNED
                    if (pinned == NavPinType.PINNED) {
                        items.add(it)
                    } else {
                        moreItems.add(it)
                    }
                }

            _state.update {
                it.copy(
                    items = items,
                    moreItems = moreItems,
                )
            }
        }
    }

data class NavDrawerItemState(
    val items: List<NavDrawerItem>,
    val moreItems: List<NavDrawerItem>,
    val discoverEnabled: Boolean,
) {
    companion object {
        val EMPTY = NavDrawerItemState(emptyList(), emptyList(), false)
    }
}

val UserDto.tvAccess: Boolean get() = policy?.enableLiveTvAccess == true
