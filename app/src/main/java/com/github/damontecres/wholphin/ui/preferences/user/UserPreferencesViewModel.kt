package com.github.damontecres.wholphin.ui.preferences.user

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.JellyfinServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinUserPreferences
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserProfileSettings
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.ui.combineTriple
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.localizationApi
import org.jellyfin.sdk.model.api.CultureDto
import javax.inject.Inject

@HiltViewModel
class UserPreferencesViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        val preferenceDataStore: DataStore<AppPreferences>,
        val navigationManager: NavigationManager,
        val backdropService: BackdropService,
        val screensaverService: ScreensaverService,
        private val serverRepository: ServerRepository,
        private val serverDao: JellyfinServerDao,
    ) : ViewModel() {
        private val mutex = Mutex()

        val currentUser = serverRepository.currentUserFlow
        val currentUserDto =
            serverRepository.currentUserDtoFlow.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                null,
            )
        private val serverLanguages = MutableStateFlow<List<CultureDto>>(emptyList())

        @OptIn(ExperimentalCoroutinesApi::class)
        val userAppPreferences: StateFlow<JellyfinUserPreferences> =
            currentUser.mapNotNull { it?.appPreferences }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                JellyfinUserPreferences(),
            )

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun createLanguageFlow(isAudio: Boolean): StateFlow<PreferredLanguage> =
            currentUser
                .combineTriple(serverRepository.currentUserDtoFlow, serverLanguages)
                .mapLatest { (user, userDto, serverLanguages) ->
                    if (user == null || userDto == null || serverLanguages.isEmpty() || user.id != userDto.id) {
                        return@mapLatest PreferredLanguage()
                    }
                    val languages =
                        serverLanguages.filter { it.threeLetterIsoLanguageName.isNotNullOrBlank() }

                    // Language user has chosen from local app
                    val prefLang = user.appPreferences.let { if (isAudio) it.preferredAudioLanguage else it.preferredSubtitleLanguage }

                    // Language user has chosen from server user profile
                    val userDisplayLang =
                        userDto.configuration
                            ?.let { if (isAudio) it.audioLanguagePreference else it.subtitleLanguagePreference }
                            .let { userLang ->
                                languages.firstOrNull { it.threeLetterIsoLanguageName == userLang }?.displayName
                                    ?: userLang
                            }
                    val selected =
                        when (prefLang) {
                            UserProfileSettings.USE_USER_PROFILE -> {
                                PreferredLanguageType.ServerProfile(userDisplayLang)
                            }

                            UserProfileSettings.PREFER_ANY_LANGUAGE -> {
                                PreferredLanguageType.AnyLanguage
                            }

                            else -> {
                                languages
                                    .firstOrNull { it.threeLetterIsoLanguageName == prefLang }
                                    ?.let {
                                        PreferredLanguageType.Language(
                                            it.threeLetterIsoLanguageName!!,
                                            it.displayName,
                                        )
                                    }
                            }
                        } ?: PreferredLanguageType.ServerProfile(userDisplayLang)
                    val options =
                        buildList {
                            add(PreferredLanguageType.ServerProfile(userDisplayLang))
                            add(PreferredLanguageType.AnyLanguage)
                            add(PreferredLanguageType.Divider)
                            languages.forEach {
                                if (it.threeLetterIsoLanguageName.isNotNullOrBlank()) {
                                    add(
                                        PreferredLanguageType.Language(
                                            it.threeLetterIsoLanguageName!!,
                                            it.displayName,
                                        ),
                                    )
                                }
                            }
                        }
                    PreferredLanguage(selected, options)
                }.stateIn(
                    viewModelScope,
                    SharingStarted.Eagerly,
                    PreferredLanguage(),
                )

        val audioLanguage: StateFlow<PreferredLanguage> = createLanguageFlow(true)
        val subtitleLanguage: StateFlow<PreferredLanguage> = createLanguageFlow(false)

        init {
            viewModelScope.launchIO {
                serverLanguages.value = api.localizationApi.getCultures().content
            }
        }

        fun updatePreferences(block: (JellyfinUserPreferences) -> JellyfinUserPreferences) {
            viewModelScope.launchIO {
                mutex.withLock {
                    val user = currentUser.first { it != null }!!
                    val newAppPrefs = block.invoke(user.appPreferences)
                    serverDao.updateUser(user.copy(appPreferences = newAppPrefs))
                }
            }
        }
    }
