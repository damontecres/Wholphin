package com.github.damontecres.wholphin.ui.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerPreferencesDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.NavDrawerPinnedItem
import com.github.damontecres.wholphin.data.model.NavPinType
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.resetSubtitles
import com.github.damontecres.wholphin.preferences.updateSubtitlePreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.ui.detail.DebugViewModel.Companion.sendAppLogs
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.NavDrawerItem
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.RememberTabManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        val preferenceDataStore: DataStore<AppPreferences>,
        val navigationManager: NavigationManager,
        val backdropService: BackdropService,
        private val rememberTabManager: RememberTabManager,
        private val serverRepository: ServerRepository,
        private val navDrawerService: NavDrawerService,
        private val serverPreferencesDao: ServerPreferencesDao,
        private val seerrServerRepository: SeerrServerRepository,
        private val deviceInfo: DeviceInfo,
        private val clientInfo: ClientInfo,
    ) : ViewModel(),
        RememberTabManager by rememberTabManager {
        val navDrawerPins =
            navDrawerService.state
                .combine(
                    serverRepository.currentUser.asFlow(),
                ) { state, user ->
                    Pair(state, user)
                }.combine(seerrServerRepository.active) { (state, user), seerr ->
                    Triple(state, user, seerr)
                }.map { (state, user, seerr) ->
                    withContext(Dispatchers.IO) {
                        val navDrawerPins =
                            serverPreferencesDao
                                .getNavDrawerPinnedItems(user!!)
                                .associateBy { it.itemId }

                        val allItems = state.let { it.items + it.moreItems }
                        val pins =
                            allItems
                                .sortedBy {
                                    navDrawerPins[it.id]?.order?.takeIf { it >= 0 } ?: Int.MAX_VALUE
                                }.mapNotNull {
                                    if (!seerr && it is NavDrawerItem.Discover) {
                                        null
                                    } else {
                                        // Assume pinned if unknown
                                        val pinned = navDrawerPins[it.id]?.type ?: NavPinType.PINNED
                                        NavDrawerPin(
                                            it.id,
                                            it.name(context),
                                            pinned == NavPinType.PINNED,
                                            it,
                                        )
                                    }
                                }

                        pins
                    }
                }

        val currentUser get() = serverRepository.currentUser

        val seerrEnabled =
            seerrServerRepository.currentUser.combine(currentUser.asFlow()) { seerrUser, jellyfinUser ->
                seerrUser != null && jellyfinUser != null && seerrUser.jellyfinUserRowId == jellyfinUser.rowId
            }

        private val _quickConnectStatus = MutableStateFlow<LoadingState>(LoadingState.Pending)
        val quickConnectStatus: StateFlow<LoadingState> = _quickConnectStatus

        init {
            viewModelScope.launchIO {
                serverRepository.currentUser.value?.let { user ->
//                    fetchNavDrawerPins(user)
                }
            }
        }

        private suspend fun fetchNavDrawerPins(user: JellyfinUser) {
            navDrawerService.state.map {
                val navDrawerPins =
                    serverPreferencesDao.getNavDrawerPinnedItems(user).associateBy { it.itemId }

                val allItems = navDrawerService.state.first().let { it.items + it.moreItems }
                val pins =
                    allItems
                        .sortedBy { navDrawerPins[it.id]?.order?.takeIf { it >= 0 } ?: Int.MAX_VALUE }
                        .map {
                            // Assume pinned if unknown
                            val pinned = navDrawerPins[it.id]?.type ?: NavPinType.PINNED
                            NavDrawerPin(it.id, it.name(context), pinned == NavPinType.PINNED, it)
                        }
                pins
            }
        }

        fun updatePins(items: List<NavDrawerPin>) {
            viewModelScope.launchIO(ExceptionHandler(true)) {
                serverRepository.currentUser.value?.let { user ->
                    serverRepository.currentUserDto.value?.let { userDto ->
                        if (user.id == userDto.id) {
                            Timber.v("Updating pins")
                            val toSave =
                                items.mapIndexed { index, item ->
                                    NavDrawerPinnedItem(
                                        user.rowId,
                                        item.id,
                                        if (item.pinned) NavPinType.PINNED else NavPinType.UNPINNED,
                                        index,
                                    )
                                }
                            serverPreferencesDao.saveNavDrawerPinnedItems(*toSave.toTypedArray())
                            navDrawerService.updateNavDrawer(user, userDto)
                        } else {
                            throw IllegalStateException("User IDs do not match")
                        }
                    }
                }
            }
        }

        fun sendAppLogs() {
            sendAppLogs(context, api, clientInfo, deviceInfo)
        }

        fun resetSubtitleSettings() {
            viewModelScope.launchIO {
                resetSubtitleSettings(preferenceDataStore)
            }
        }

        fun setPin(
            user: JellyfinUser,
            pin: String?,
        ) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                serverRepository.setUserPin(user, pin)
            }
        }

        fun resetQuickConnectStatus() {
            _quickConnectStatus.value = LoadingState.Pending
        }

        fun authorizeQuickConnect(code: String) {
            viewModelScope.launchIO {
                _quickConnectStatus.value = LoadingState.Loading
                try {
                    val success = serverRepository.authorizeQuickConnect(code)
                    _quickConnectStatus.value =
                        if (success) {
                            LoadingState.Success
                        } else {
                            LoadingState.Error("Authorization failed")
                        }
                } catch (e: Exception) {
                    _quickConnectStatus.value = LoadingState.Error(e)
                }
            }
        }

        companion object {
            suspend fun resetSubtitleSettings(appPreferences: DataStore<AppPreferences>) {
                appPreferences.updateData {
                    it.updateSubtitlePreferences {
                        resetSubtitles()
                    }
                }
            }
        }
    }
