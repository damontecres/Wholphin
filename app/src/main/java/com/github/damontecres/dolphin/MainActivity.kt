package com.github.damontecres.dolphin

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.github.damontecres.dolphin.data.ServerRepository
import com.github.damontecres.dolphin.hilt.AuthOkHttpClient
import com.github.damontecres.dolphin.preferences.AppPreferences
import com.github.damontecres.dolphin.preferences.DefaultUserConfiguration
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.CoilConfig
import com.github.damontecres.dolphin.ui.nav.ApplicationContent
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.ui.theme.DolphinTheme
import com.github.damontecres.dolphin.util.UpdateChecker
import com.github.damontecres.dolphin.util.profile.createDeviceProfile
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var serverRepository: ServerRepository

    @Inject
    lateinit var userPreferencesDataStore: DataStore<AppPreferences>

    @AuthOkHttpClient
    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var navigationManager: NavigationManager

    @Inject
    lateinit var updateChecker: UpdateChecker

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("MainActivity.onCreate")
        setContent {
            CoilConfig(okHttpClient, false)
            val appPreferences by userPreferencesDataStore.data.collectAsState(null)
            appPreferences?.let { appPreferences ->
                DolphinTheme(
                    true,
                    appThemeColors = appPreferences.interfacePreferences.appThemeColors,
                ) {
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                        shape = RectangleShape,
                    ) {
                        var isRestoringSession by remember { mutableStateOf(true) }
                        LaunchedEffect(Unit) {
                            if (appPreferences.currentServerId.isNotBlank()) {
                                try {
                                    serverRepository.restoreSession(
                                        appPreferences.currentServerId,
                                        appPreferences.currentUserId,
                                    )
                                } catch (ex: Exception) {
                                    Timber.e(ex, "Exception restoring session")
                                }
                                Timber.d("MainActivity session restored")
                            }
                            isRestoringSession = false
                        }
                        val server = serverRepository.currentServer
                        val user = serverRepository.currentUser
                        val userDto = serverRepository.currentUserDto

                        val preferences =
                            UserPreferences(
                                appPreferences,
                                userDto?.configuration ?: DefaultUserConfiguration,
                            )

                        val deviceProfile =
                            remember(appPreferences) {
                                createDeviceProfile(this@MainActivity, preferences, false)
                            }

                        if (isRestoringSession) {
                            Box(
                                modifier = Modifier.size(200.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.border,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                        } else {
                            val initialDestination =
                                if (server != null && user != null) {
                                    Destination.Home()
                                } else {
                                    Destination.ServerList
                                }
                            val backStack = rememberNavBackStack(initialDestination)
                            navigationManager.backStack = backStack
                            if (appPreferences.autoCheckForUpdates) {
                                LaunchedEffect(Unit) {
                                    try {
                                        updateChecker.maybeShowUpdateToast(appPreferences.updateUrl)
                                    } catch (ex: Exception) {
                                        Timber.w(ex, "Failed to check for update")
                                    }
                                }
                            }
                            ApplicationContent(
                                user = user,
                                server = server,
                                navigationManager = navigationManager,
                                preferences = preferences,
                                deviceProfile = deviceProfile,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}
