package com.github.damontecres.dolphin.ui.preferences

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import com.github.damontecres.dolphin.preferences.AppPreferences
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel
    @Inject
    constructor(
        val preferenceDataStore: DataStore<AppPreferences>,
        val navigationManager: NavigationManager,
    ) : ViewModel()
