package com.github.damontecres.wholphin

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
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
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.DefaultUserConfiguration
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.AppUpgradeHandler
import com.github.damontecres.wholphin.services.DeviceProfileService
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PlaybackLifecycleObserver
import com.github.damontecres.wholphin.services.ServerEventListener
import com.github.damontecres.wholphin.services.UpdateChecker
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.services.tvprovider.TvProviderSchedulerService
import com.github.damontecres.wholphin.ui.CoilConfig
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.ApplicationContent
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.util.DebugLogTree
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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

    @Inject
    lateinit var serverEventListener: ServerEventListener

    @Inject
    lateinit var playbackLifecycleObserver: PlaybackLifecycleObserver

    @Inject
    lateinit var deviceProfileService: DeviceProfileService

    @Inject
    lateinit var imageUrlService: ImageUrlService

    @Inject
    lateinit var tvProviderSchedulerService: TvProviderSchedulerService

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("MainActivity.onCreate")
        lifecycle.addObserver(playbackLifecycleObserver)
        if (savedInstanceState == null) {
            appUpgradeHandler.copySubfont(false)
        }
        setContent {
            val appPreferences by userPreferencesDataStore.data.collectAsState(null)
            appPreferences?.let { appPreferences ->
                CoilConfig(
                    diskCacheSizeBytes =
                        appPreferences.advancedPreferences.imageDiskCacheSizeBytes.let {
                            if (it < AppPreference.ImageDiskCacheSize.min * AppPreference.MEGA_BIT) {
                                AppPreference.ImageDiskCacheSize.defaultValue * AppPreference.MEGA_BIT
                            } else {
                                it
                            }
                        },
                    okHttpClient = okHttpClient,
                    debugLogging = false,
                    enableCache = true,
                )
                LaunchedEffect(appPreferences.debugLogging) {
                    DebugLogTree.INSTANCE.enabled = appPreferences.debugLogging
                }
                CompositionLocalProvider(LocalImageUrlService provides imageUrlService) {
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
                                if (appPreferences.signInAutomatically) {
                                    try {
                                        serverRepository.restoreSession(
                                            appPreferences.currentServerId?.toUUIDOrNull(),
                                            appPreferences.currentUserId?.toUUIDOrNull(),
                                        )
                                    } catch (ex: Exception) {
                                        Timber.e(ex, "Exception restoring session")
                                    }
                                }
                                isRestoringSession = false
                            }
                            val current by serverRepository.current.observeAsState()

                            val preferences =
                                UserPreferences(
                                    appPreferences,
                                    current?.userDto?.configuration ?: DefaultUserConfiguration,
                                )

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
                                DisposableEffect(Unit) {
                                    onDispose {
                                        if (!appPreferences.signInAutomatically) {
                                            serverRepository.closeSession()
                                        }
                                    }
                                }
                                key(current?.server?.id, current?.user?.id) {
                                    LaunchedEffect(current?.user?.pin) {
                                        if (current?.user?.pin?.isNotNullOrBlank() == true) {
                                            // If user has a pin, then obscure the window in previews
                                            window?.setFlags(
                                                WindowManager.LayoutParams.FLAG_SECURE,
                                                WindowManager.LayoutParams.FLAG_SECURE,
                                            )
                                        } else {
                                            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                        }
                                    }
                                    val initialDestination =
                                        when {
                                            current != null -> Destination.Home()
                                            !appPreferences.signInAutomatically -> Destination.ServerList // TODO user list?
                                            else -> Destination.ServerList
                                        }
                                    val backStack = rememberNavBackStack(initialDestination)
                                    navigationManager.backStack = backStack
                                    if (UpdateChecker.ACTIVE && appPreferences.autoCheckForUpdates) {
                                        LaunchedEffect(Unit) {
                                            try {
                                                updateChecker.maybeShowUpdateToast(appPreferences.updateUrl)
                                            } catch (ex: Exception) {
                                                Timber.w(ex, "Failed to check for update")
                                            }
                                        }
                                    }
                                    LaunchedEffect(current, preferences) {
                                        withContext(Dispatchers.IO) {
                                            deviceProfileService.getOrCreateDeviceProfile(
                                                preferences.appPreferences.playbackPreferences,
                                                current?.server?.serverVersion,
                                            )
                                        }
                                    }
                                    ApplicationContent(
                                        user = current?.user,
                                        server = current?.server,
                                        navigationManager = navigationManager,
                                        preferences = preferences,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launchIO {
            appUpgradeHandler.run()
        }
    }

    override fun onRestart() {
        super.onRestart()
        val signInAutomatically =
            runBlocking { userPreferencesDataStore.data.firstOrNull()?.signInAutomatically } ?: true
        Timber.i("onRestart: signInAutomatically=$signInAutomatically")
        if (!signInAutomatically) {
            serverRepository.closeSession()
        }
    }

    companion object {
        const val INTENT_ITEM_ID = "itemId"
        const val INTENT_ITEM_TYPE = "itemType"
    }
}
