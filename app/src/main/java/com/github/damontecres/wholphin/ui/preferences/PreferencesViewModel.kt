package com.github.damontecres.wholphin.ui.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.NavDrawerItemRepository
import com.github.damontecres.wholphin.data.ServerPreferencesDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.isPinned
import com.github.damontecres.wholphin.data.model.NavDrawerPinnedItem
import com.github.damontecres.wholphin.data.model.NavPinType
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.ui.detail.DebugViewModel
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.NavDrawerItem
import com.github.damontecres.wholphin.ui.nav.NavigationManager
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.RememberTabManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.clientLogApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        val preferenceDataStore: DataStore<AppPreferences>,
        val navigationManager: NavigationManager,
        private val rememberTabManager: RememberTabManager,
        private val serverRepository: ServerRepository,
        private val navDrawerItemRepository: NavDrawerItemRepository,
        private val serverPreferencesDao: ServerPreferencesDao,
        private val deviceInfo: DeviceInfo,
        private val clientInfo: ClientInfo,
    ) : ViewModel(),
        RememberTabManager by rememberTabManager {
        private lateinit var allNavDrawerItems: List<NavDrawerItem>
        val navDrawerPins = MutableLiveData<Map<NavDrawerItem, Boolean>>(mapOf())

        init {
            viewModelScope.launchIO {
                serverRepository.currentUser?.let { user ->
                    allNavDrawerItems = navDrawerItemRepository.getNavDrawerItems()
                    val pins = serverPreferencesDao.getNavDrawerPinnedItems(user)
                    val navDrawerPins = allNavDrawerItems.associateWith { pins.isPinned(it.id) }
                    this@PreferencesViewModel.navDrawerPins.setValueOnMain(navDrawerPins)
                }
            }
        }

        fun updatePins(newSelectedItems: List<NavDrawerItem>) {
            viewModelScope.launchIO(ExceptionHandler(true)) {
                serverRepository.currentUser?.let { user ->
                    val disabledItems =
                        mutableListOf<NavDrawerItem>().apply {
                            addAll(allNavDrawerItems)
                            removeAll(newSelectedItems)
                        }
                    val enabledItems = newSelectedItems.toSet()
                    val toSave =
                        disabledItems.map {
                            NavDrawerPinnedItem(
                                user.rowId,
                                it.id,
                                NavPinType.UNPINNED,
                            )
                        } +
                            enabledItems.map {
                                NavDrawerPinnedItem(
                                    user.rowId,
                                    it.id,
                                    NavPinType.PINNED,
                                )
                            }
                    serverPreferencesDao.saveNavDrawerPinnedItems(*toSave.toTypedArray())
                    val pins = serverPreferencesDao.getNavDrawerPinnedItems(user)
                    val navDrawerPins = allNavDrawerItems.associateWith { pins.isPinned(it.id) }
                    this@PreferencesViewModel.navDrawerPins.setValueOnMain(navDrawerPins)
                }
            }
        }

        fun sendAppLogs() {
            viewModelScope.launchIO(ExceptionHandler(true)) {
                val logcat = DebugViewModel.getLogCatLines().joinToString("\n") { it.text }
                val body =
                    """
                    Send App Logs
                    $clientInfo
                    $deviceInfo

                    """.trimIndent() + logcat
                val response by api.clientLogApi.logFile(body)
                showToast(context, "Sent! Filename=${response.fileName}")
            }
        }
    }
