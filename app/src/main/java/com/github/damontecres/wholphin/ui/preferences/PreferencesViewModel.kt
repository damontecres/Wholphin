package com.github.damontecres.wholphin.ui.preferences

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.ui.nav.NavigationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel
    @Inject
    constructor(
        val preferenceDataStore: DataStore<AppPreferences>,
        val navigationManager: NavigationManager,
    ) : ViewModel()
