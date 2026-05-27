package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.data.SeerrServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Listens for JF user switching in the app to also switch other settings like Seerr user/server
 */
@ActivityScoped
class UserSwitchListener
    @Inject
    constructor(
        @param:ActivityContext private val context: Context,
        private val serverRepository: ServerRepository,
        private val seerrServerRepository: SeerrServerRepository,
        private val seerrServerDao: SeerrServerDao,
        private val seerrApi: SeerrApi,
        private val homeSettingsService: HomeSettingsService,
        private val serverPluginApi: ServerPluginApi,
    ) {
        init {
            context as AppCompatActivity
            context.lifecycleScope.launchDefault {
                serverRepository.currentUserFlow.collect { user ->
                    Timber.d("New user")
                    seerrServerRepository.clear()
                    homeSettingsService.currentSettings.update { HomePageResolvedSettings.EMPTY }
                    if (user != null) {
                        switchUser(user)
                    }
                }
            }
        }

        private suspend fun switchUser(user: JellyfinUser) =
            supervisorScope {
                // Switch the locale to either the user's choice or the system default (empty)
                val localeList =
                    user.uiLanguage?.let { LocaleListCompat.forLanguageTags(it) }
                        ?: LocaleListCompat.getEmptyLocaleList()
                Timber.i("Switching locale to %s", localeList)
                withContext(Dispatchers.Main) {
                    AppCompatDelegate.setApplicationLocales(localeList)
                }

                // Check if plugin is installed, then for home settings
                launchIO {
                    val serverPluginInstalled =
                        try {
                            serverPluginApi.public()
                        } catch (ex: Exception) {
                            Timber.e(ex, "Error checking for server plugin")
                            false
                        }
                    Timber.i("Server plugin installed: %s", serverPluginInstalled)
                    serverRepository.serverPluginInstalled.value = serverPluginInstalled

                    // Check for home settings
                    homeSettingsService.loadCurrentSettings(user.id)
                }
                if (BuildConfig.DISCOVER_ENABLED) {
                    // Check for seerr server
                    launchIO {
                        seerrServerDao
                            .getUsersByJellyfinUser(user.rowId)
                            .lastOrNull()
                            ?.let { seerrUser ->
                                val server =
                                    seerrServerDao.getServer(seerrUser.serverId)?.server
                                if (server != null) {
                                    Timber.i("Found a seerr user & server")
                                    try {
                                        seerrApi.update(server.url, seerrUser.credential)
                                        val userConfig =
                                            if (seerrUser.authMethod != SeerrAuthMethod.API_KEY) {
                                                seerrLogin(
                                                    seerrApi.api,
                                                    seerrUser.authMethod,
                                                    seerrUser.username,
                                                    seerrUser.password,
                                                )
                                            } else {
                                                seerrApi.api.usersApi.authMeGet()
                                            }
                                        seerrServerRepository.set(
                                            server,
                                            seerrUser,
                                            userConfig,
                                        )
                                    } catch (ex: Exception) {
                                        Timber.w(
                                            ex,
                                            "Error logging into %s",
                                            server.url,
                                        )
                                        seerrServerRepository.error(server, seerrUser, ex)
                                    }
                                }
                            }
                    }
                }
            }
    }
