package com.github.damontecres.wholphin

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.AppUpgradeHandler
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.DeviceProfileService
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PlaybackLifecycleObserver
import com.github.damontecres.wholphin.services.RefreshRateService
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.SetupNavigationManager
import com.github.damontecres.wholphin.services.UpdateChecker
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.ui.CoilConfig
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.ApplicationContent
import com.github.damontecres.wholphin.ui.setup.SwitchServerContent
import com.github.damontecres.wholphin.ui.setup.SwitchUserContent
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.util.ProvideLocalClock
import com.github.damontecres.wholphin.util.DebugLogTree
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.OkHttpClient
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    @Inject
    lateinit var userPreferencesDataStore: DataStore<AppPreferences>

    @AuthOkHttpClient
    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var navigationManager: NavigationManager

    @Inject
    lateinit var setupNavigationManager: SetupNavigationManager

    @Inject
    lateinit var updateChecker: UpdateChecker

    @Inject
    lateinit var appUpgradeHandler: AppUpgradeHandler

    @Inject
    lateinit var playbackLifecycleObserver: PlaybackLifecycleObserver

    @Inject
    lateinit var imageUrlService: ImageUrlService

    @Inject
    lateinit var refreshRateService: RefreshRateService

    private var signInAuto = true

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("MainActivity.onCreate: savedInstanceState is null=${savedInstanceState == null}")
        lifecycle.addObserver(playbackLifecycleObserver)
        if (savedInstanceState == null) {
            appUpgradeHandler.copySubfont(false)
        }
        refreshRateService.refreshRateMode.observe(this) { mode ->
            // Listen for refresh rate changes
            val attrs = window.attributes
            if (attrs.preferredDisplayModeId != mode.modeId) {
                Timber.d("Switch preferredRefreshRate to %s", mode.refreshRate)
                window.attributes = attrs.apply { preferredRefreshRate = mode.refreshRate }
            }
        }
        viewModel.appStart()
        setContent {
            val appPreferences by userPreferencesDataStore.data.collectAsState(null)
            appPreferences?.let { appPreferences ->
                LaunchedEffect(appPreferences.signInAutomatically) {
                    signInAuto = appPreferences.signInAutomatically
                }
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
//                            val backStack = rememberNavBackStack(SetupDestination.Loading)
//                            setupNavigationManager.backStack = backStack
                            val backStack = setupNavigationManager.backStack
                            NavDisplay(
                                backStack = backStack,
                                onBack = { backStack.removeLastOrNull() },
                                entryDecorators =
                                    listOf(
                                        rememberSaveableStateHolderNavEntryDecorator(),
                                        rememberViewModelStoreNavEntryDecorator(),
                                    ),
                                entryProvider = { key ->
                                    key as SetupDestination
                                    NavEntry(key) {
                                        when (key) {
                                            SetupDestination.Loading -> {
                                                Box(
                                                    modifier = Modifier.size(200.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    CircularProgressIndicator(
                                                        color = MaterialTheme.colorScheme.border,
                                                        modifier = Modifier.align(Alignment.Center),
                                                    )
                                                }
                                            }

                                            SetupDestination.ServerList -> {
                                                SwitchServerContent(Modifier.fillMaxSize())
                                            }

                                            is SetupDestination.UserList -> {
                                                SwitchUserContent(
                                                    currentServer = key.server,
                                                    Modifier.fillMaxSize(),
                                                )
                                            }

                                            is SetupDestination.AppContent -> {
                                                val current = key.current
                                                ProvideLocalClock {
                                                    if (UpdateChecker.ACTIVE && appPreferences.autoCheckForUpdates) {
                                                        LaunchedEffect(Unit) {
                                                            try {
                                                                updateChecker.maybeShowUpdateToast(
                                                                    appPreferences.updateUrl,
                                                                )
                                                            } catch (ex: Exception) {
                                                                Timber.w(
                                                                    ex,
                                                                    "Exception during update check",
                                                                )
                                                            }
                                                        }
                                                    }
                                                    val appPreferences by userPreferencesDataStore.data.collectAsState(
                                                        appPreferences,
                                                    )
                                                    val preferences =
                                                        remember(appPreferences) {
                                                            UserPreferences(appPreferences)
                                                        }
                                                    ApplicationContent(
                                                        user = current.user,
                                                        server = current.server,
                                                        navigationManager = navigationManager,
                                                        preferences = preferences,
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.i("onResume")
        lifecycleScope.launchIO {
            appUpgradeHandler.run()
        }
    }

    override fun onRestart() {
        super.onRestart()
        Timber.i("onRestart")
        viewModel.appStart()
//        val signInAutomatically =
//            runBlocking { userPreferencesDataStore.data.firstOrNull()?.signInAutomatically } ?: true

//        // TODO PIN-related
// //        if (!signInAutomatically || serverRepository.currentUser.value?.hasPin == true) {
//        if (!signInAutomatically) {
//            serverRepository.closeSession()
//        }
    }

//    override fun onStop() {
//        super.onStop()
//        Timber.i("onStop")
//    }
//
//    override fun onPause() {
//        super.onPause()
//        Timber.i("onPause")
//    }
//
//    override fun onStart() {
//        super.onStart()
//        Timber.i("onStart")
//    }
//
//    override fun onSaveInstanceState(outState: Bundle) {
//        super.onSaveInstanceState(outState)
//        Timber.i("onSaveInstanceState")
//    }
//
//    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
//        super.onRestoreInstanceState(savedInstanceState)
//        Timber.i("onRestoreInstanceState")
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        Timber.i("onDestroy")
//    }
}

@HiltViewModel
class MainActivityViewModel
    @Inject
    constructor(
        private val preferences: DataStore<AppPreferences>,
        private val serverRepository: ServerRepository,
        private val navigationManager: SetupNavigationManager,
        private val deviceProfileService: DeviceProfileService,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        fun appStart() {
            viewModelScope.launchIO {
                try {
                    val prefs =
                        preferences.data.firstOrNull() ?: AppPreferences.getDefaultInstance()
                    if (prefs.signInAutomatically) {
                        val current =
                            serverRepository.restoreSession(
                                prefs.currentServerId?.toUUIDOrNull(),
                                prefs.currentUserId?.toUUIDOrNull(),
                            )
                        if (current != null) {
                            // Restored
                            navigationManager.navigateTo(SetupDestination.AppContent(current))
                        } else {
                            // Did not restore
                            navigationManager.navigateTo(SetupDestination.ServerList)
                        }
                    } else {
                        navigationManager.navigateTo(SetupDestination.Loading)
                        backdropService.clearBackdrop()
                        val currentServerId = prefs.currentServerId?.toUUIDOrNull()
                        if (currentServerId != null) {
                            val currentServer =
                                serverRepository.serverDao.getServer(currentServerId)?.server
                            if (currentServer != null) {
                                navigationManager.navigateTo(SetupDestination.UserList(currentServer))
                            } else {
                                navigationManager.navigateTo(SetupDestination.ServerList)
                            }
                        } else {
                            navigationManager.navigateTo(SetupDestination.ServerList)
                        }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error during appStart")
                    navigationManager.navigateTo(SetupDestination.ServerList)
                }
            }
            viewModelScope.launchIO {
                // Create the mediaCodecCapabilitiesTest if needed
                deviceProfileService.mediaCodecCapabilitiesTest.supportsAVC()
            }
        }
    }
