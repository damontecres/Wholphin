package com.github.damontecres.wholphin.ui.preferences

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
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.NavDrawerItem
import com.github.damontecres.wholphin.ui.nav.NavigationManager
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.RememberTabManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel
    @Inject
    constructor(
        val preferenceDataStore: DataStore<AppPreferences>,
        val navigationManager: NavigationManager,
        val rememberTabManager: RememberTabManager,
        val serverRepository: ServerRepository,
        val navDrawerItemRepository: NavDrawerItemRepository,
        val serverPreferencesDao: ServerPreferencesDao,
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
    }
