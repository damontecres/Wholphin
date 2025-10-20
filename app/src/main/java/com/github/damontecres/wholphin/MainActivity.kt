package com.github.damontecres.wholphin

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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.DefaultUserConfiguration
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.CoilConfig
import com.github.damontecres.wholphin.ui.nav.ApplicationContent
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.NavigationManager
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.util.AppUpgradeHandler
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.UpdateChecker
import com.github.damontecres.wholphin.util.profile.createDeviceProfile
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
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

    @Inject
    lateinit var appUpgradeHandler: AppUpgradeHandler

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("MainActivity.onCreate")
        lifecycleScope.launch(Dispatchers.IO + ExceptionHandler()) {
            appUpgradeHandler.run()
        }
        setContent {
            CoilConfig(okHttpClient, false)
            val appPreferences by userPreferencesDataStore.data.collectAsState(null)
            appPreferences?.let { appPreferences ->
                WholphinTheme(
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
                            if (appPreferences.currentServerId.isNotBlank() && appPreferences.currentUserId.isNotBlank()) {
                                try {
                                    serverRepository.restoreSession(
                                        appPreferences.currentServerId?.toUUIDOrNull(),
                                        appPreferences.currentUserId?.toUUIDOrNull(),
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
                            key(server, user) {
                                val initialDestination =
                                    if (server != null && user != null) {
                                        Destination.Home()
                                    } else if (server != null) {
                                        Destination.UserList
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

    override fun onPause() {
        if (navigationManager.backStack.lastOrNull() is Destination.Playback) {
            navigationManager.goBack()
        }
        super.onPause()
    }
}
