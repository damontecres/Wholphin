package com.github.damontecres.wholphin.services

import androidx.datastore.core.DataStore
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.preferences.updatePlaybackPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Get the current user's [UserPreferences]
 */
@Singleton
class UserPreferencesService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val preferencesDataStore: DataStore<AppPreferences>,
    ) {
        val flow: Flow<UserPreferences> =
            preferencesDataStore.data.combine(serverRepository.currentUserFlow) { appPrefs, user ->
                UserPreferences(appPrefs, user?.appPreferences)
            }

        suspend fun setReduceLoudSounds(value: Boolean) {
            preferencesDataStore.updateData { it.updatePlaybackPreferences { reduceLoudSounds = value } }
        }

        suspend fun getCurrent(): UserPreferences = flow.first()
    }
