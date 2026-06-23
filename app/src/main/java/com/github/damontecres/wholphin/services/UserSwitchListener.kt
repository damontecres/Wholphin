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
import com.github.damontecres.wholphin.data.model.SeerrPluginLoginType
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
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
                    seerrServerRepository.consumeAndClearPasswordsIfRequested(user.rowId)
                    launchIO { restoreOrAutoSetupSeerr(user) }
                }
            }

        private suspend fun restoreOrAutoSetupSeerr(user: JellyfinUser) {
            val existing =
                seerrServerDao
                    .getUsersByJellyfinUser(user.rowId)
                    .lastOrNull()
                    ?: return tryAutoSetupFromPlugin(user)
            val server = seerrServerDao.getServer(existing.serverId)?.server ?: return
            val effective = mergeStashedPassword(existing, server)
            if (effective.authMethod != SeerrAuthMethod.API_KEY && effective.password.isNullOrBlank()) {
                Timber.i("Seerr entry for %s has no password yet; skipping auto-login", server.url)
                return
            }
            try {
                seerrApi.update(server.url, effective.credential)
                val userConfig =
                    if (effective.authMethod == SeerrAuthMethod.API_KEY) {
                        seerrApi.api.usersApi.authMeGet()
                    } else {
                        seerrLogin(
                            seerrApi.api,
                            effective.authMethod,
                            effective.username,
                            effective.password,
                        )
                    }
                seerrServerRepository.set(server, effective, userConfig)
            } catch (ex: Exception) {
                Timber.w(ex, "Seerr login to %s failed - credentials kept, will retry on next start", server.url)
                seerrServerRepository.error(server, effective, ex)
            }
        }

        private suspend fun mergeStashedPassword(
            existing: SeerrUser,
            server: SeerrServer,
        ): SeerrUser {
            if (existing.authMethod != SeerrAuthMethod.JELLYFIN) return existing
            val stashed = seerrServerRepository.consumeJellyfinPassword()
            if (stashed.isNullOrBlank() || stashed == existing.password) return existing
            val updated = existing.copy(password = stashed)
            seerrServerDao.addUser(updated)
            Timber.i("Updated Seerr password for %s from fresh login", server.url)
            return updated
        }

        private suspend fun tryAutoSetupFromPlugin(user: JellyfinUser) {
            val settings =
                try {
                    serverPluginApi.fetchSeerrSettings()
                } catch (ex: Exception) {
                    Timber.w(ex, "Failed to fetch seerr settings from server plugin")
                    return
                } ?: return
            val url = settings.serverUrl
            if (!url.isNotNullOrBlank()) return
            val login = settings.login ?: return
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

                    SeerrPluginLoginType.JELLYFIN -> {
                        val jf = login.jellyfin ?: return
                        if (jf.useCurrentUser) {
                            val username = user.name
                            if (!username.isNotNullOrBlank()) return
                            val password = seerrServerRepository.consumeJellyfinPassword()
                            if (password.isNullOrBlank()) {
                                Timber.i("Pre-filling Seerr URL + username for %s (password required from user)", username)
                                seerrServerRepository.prefillFromPlugin(url, SeerrAuthMethod.JELLYFIN, username)
                            } else {
                                Timber.i("Auto-setup Seerr via Jellyfin (useCurrentUser=%s)", username)
                                seerrServerRepository.persistAndTryLogin(url, SeerrAuthMethod.JELLYFIN, username, password)
                            }
                            return
                        }
                        val username = jf.username
                        val password = jf.password
                        if (!username.isNotNullOrBlank() || !password.isNotNullOrBlank()) return
                        Timber.i("Auto-setup Seerr via Jellyfin (explicit creds) from plugin")
                        seerrServerRepository.addAndChangeServer(url, SeerrAuthMethod.JELLYFIN, username, password)
                    }

                    SeerrPluginLoginType.NONE -> {
                        Timber.d("Seerr plugin settings present but login type is None")
                    }
                }
            } catch (ex: Exception) {
                Timber.w(ex, "Seerr auto-setup from plugin failed for %s", url)
            }
        }
    }
