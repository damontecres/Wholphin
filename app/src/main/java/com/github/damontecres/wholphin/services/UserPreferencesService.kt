package com.github.damontecres.wholphin.services

import androidx.datastore.core.DataStore
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val preferencesDataStore: DataStore<AppPreferences>,
    ) {
        val flow = preferencesDataStore.data.map { UserPreferences(it) }

        suspend fun getCurrent(): UserPreferences =
            serverRepository.currentUserDto.value!!.configuration.let { userConfig ->
                val appPrefs = preferencesDataStore.data.firstOrNull() ?: AppPreferences.getDefaultInstance()
                UserPreferences(
                    appPrefs,
                )
            }
    }
