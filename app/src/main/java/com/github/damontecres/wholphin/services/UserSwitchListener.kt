package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.api.seerr.infrastructure.ClientException
import com.github.damontecres.wholphin.data.SeerrServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrPluginLoginType
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
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
                withContext(WholphinDispatchers.Main) {
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
                    homeSettingsService.loadCurrentSettings(user)
                }
                if (BuildConfig.DISCOVER_ENABLED) {
                    launchIO { restoreOrAutoSetupSeerr(user) }
                }
            }

        private suspend fun restoreOrAutoSetupSeerr(user: JellyfinUser) {
            val existing =
                seerrServerDao
                    .getUsersByJellyfinUser(user.rowId)
                    .lastOrNull()
                    ?: return tryAutoSetupFromPlugin()
            val server = seerrServerDao.getServer(existing.serverId)?.server ?: return
            try {
                seerrApi.update(server.url, existing.credential)
                val userConfig =
                    if (existing.authMethod == SeerrAuthMethod.API_KEY) {
                        seerrApi.api.usersApi.authMeGet()
                    } else {
                        seerrLogin(
                            seerrApi.api,
                            existing.authMethod,
                            existing.username,
                            existing.password,
                        )
                    }
                seerrServerRepository.set(server, existing, userConfig)
            } catch (ex: Exception) {
                Timber.w(ex, "Seerr login to %s failed - credentials kept, will retry on next start", server.url)
                seerrServerRepository.error(server, existing, ex)
            }
        }

        private suspend fun tryAutoSetupFromPlugin() {
            val settings =
                try {
                    serverPluginApi.fetchSeerrSettings()
                } catch (ex: Exception) {
                    Timber.w(ex, "Failed to fetch seerr settings from server plugin")
                    return
                } ?: return
            val url = settings.serverUrl
            if (!url.isNotNullOrBlank()) return
            val login = settings.login
            if (login == null) {
                Timber.i("Pre-filling Seerr URL from plugin")
                seerrServerRepository.prefillFromPlugin(url)
                return
            }
            try {
                when (login.type) {
                    SeerrPluginLoginType.API_KEY -> {
                        val key = login.apiKey
                        if (!key.isNotNullOrBlank()) return
                        Timber.i("Auto-setup Seerr via API key from plugin")
                        seerrServerRepository.addAndChangeServer(url, key)
                    }

                    SeerrPluginLoginType.LOCAL -> {
                        val username = login.local?.username
                        val password = login.local?.password
                        if (!username.isNotNullOrBlank() || !password.isNotNullOrBlank()) return
                        Timber.i("Auto-setup Seerr via local login from plugin")
                        seerrServerRepository.addAndChangeServer(url, SeerrAuthMethod.LOCAL, username, password)
                    }

                    SeerrPluginLoginType.NONE -> {
                        Timber.i("Pre-filling Seerr URL from plugin")
                        seerrServerRepository.prefillFromPlugin(url)
                    }
                }
            } catch (ex: ClientException) {
                seerrServerRepository.prefillFromPlugin(url)
                Timber.w(
                    ex,
                    "Seerr auto-setup from plugin failed for %s with HTTP %s. Check plugin Seerr credentials and auth type.",
                    url,
                    ex.statusCode,
                )
            } catch (ex: Exception) {
                seerrServerRepository.prefillFromPlugin(url)
                Timber.w(ex, "Seerr auto-setup from plugin failed for %s", url)
            }
        }
    }
