package com.github.damontecres.wholphin

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.github.damontecres.wholphin.services.DatePlayedInvalidationService
import com.github.damontecres.wholphin.services.DeviceProfileService
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PlaybackLifecycleObserver
import com.github.damontecres.wholphin.services.RefreshRateService
import com.github.damontecres.wholphin.services.ServerEventListener
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.SetupNavigationManager
import com.github.damontecres.wholphin.services.SuggestionsSchedulerService
import com.github.damontecres.wholphin.services.UpdateChecker
import com.github.damontecres.wholphin.services.UserSwitchListener
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.services.tvprovider.TvProviderSchedulerService
import com.github.damontecres.wholphin.ui.CoilConfig
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisodeIds
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.ApplicationContent
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.setup.SwitchServerContent
import com.github.damontecres.wholphin.ui.setup.SwitchUserContent
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.util.ProvideLocalClock
import com.github.damontecres.wholphin.util.DebugLogTree
import com.github.damontecres.wholphin.util.ExceptionHandler
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.jellyfin.sdk.model.api.BaseItemKind
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

    @Inject
    lateinit var userSwitchListener: UserSwitchListener

    @Inject
    lateinit var tvProviderSchedulerService: TvProviderSchedulerService

    @Inject
    lateinit var suggestionsSchedulerService: SuggestionsSchedulerService

    // Note: unused but injected to ensure it is created
    @Inject
    lateinit var serverEventListener: ServerEventListener

    // Note: unused but injected to ensure it is created
    @Inject
    lateinit var datePlayedInvalidationService: DatePlayedInvalidationService

    private var signInAuto = true

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        Timber.i("MainActivity.onCreate: savedInstanceState is null=${savedInstanceState == null}")
        lifecycle.addObserver(playbackLifecycleObserver)
        if (savedInstanceState == null) {
            lifecycleScope.launchIO {
                appUpgradeHandler.copySubfont(false)
            }
        }
        viewModel.serverRepository.currentUser.observe(this) { user ->
            if (user?.hasPin == true) {
                window?.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE,
                )
            } else {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        viewModel.appStart()
        setContent {
            val appPreferences by userPreferencesDataStore.data.collectAsState(null)
            if (appPreferences == null) {
                // Show loading page if it is taking a while to get app preferences
                var showLoading by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(500)
                    Timber.i("Showing loading page")
                    showLoading = true
                }
                if (showLoading) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                    ) {
                        LoadingPage()
                    }
                }
            }
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
                                                    var showContent by remember {
                                                        mutableStateOf(true)
                                                    }
                                                    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                                                        if (!preferences.appPreferences.signInAutomatically) {
                                                            showContent = false
                                                        }
                                                    }

                                                    if (showContent) {
                                                        val requestedDestination =
                                                            remember(intent) {
                                                                intent?.let(::extractDestination)
                                                            }
                                                        ApplicationContent(
                                                            user = current.user,
                                                            server = current.server,
                                                            startDestination =
                                                                requestedDestination
                                                                    ?: Destination.Home(),
                                                            navigationManager = navigationManager,
                                                            preferences = preferences,
                                                            modifier = Modifier.fillMaxSize(),
                                                        )
                                                    } else {
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
        Timber.d("onResume")
        lifecycleScope.launchIO {
            appUpgradeHandler.run()
        }
    }

    override fun onRestart() {
        super.onRestart()
        Timber.d("onRestart")
        viewModel.appStart()
    }

    override fun onStop() {
        super.onStop()
        Timber.d("onStop")
        tvProviderSchedulerService.launchOneTimeRefresh()
    }

    override fun onPause() {
        super.onPause()
        Timber.d("onPause")
    }

    override fun onStart() {
        super.onStart()
        Timber.d("onStart")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Timber.d("onSaveInstanceState")
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        Timber.d("onRestoreInstanceState")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("onDestroy")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Timber.d("onConfigurationChanged")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Timber.v("onNewIntent")
        setIntent(intent)
        extractDestination(intent)?.let {
            navigationManager.replace(it)
        }
    }

    private fun extractDestination(intent: Intent): Destination? =
        intent.let {
            val itemId =
                it.getStringExtra(INTENT_ITEM_ID)?.toUUIDOrNull()
            val type =
                it.getStringExtra(INTENT_ITEM_TYPE)?.let(BaseItemKind::fromNameOrNull)
            if (itemId != null && type != null) {
                val seriesId = it.getStringExtra(INTENT_SERIES_ID)?.toUUIDOrNull()
                val seasonId = it.getStringExtra(INTENT_SEASON_ID)?.toUUIDOrNull()
                val episodeNumber = it.getIntExtra(INTENT_EPISODE_NUMBER, -1)
                val seasonNumber = it.getIntExtra(INTENT_SEASON_NUMBER, -1)
                if (seriesId != null && seasonId != null && episodeNumber >= 0 && seasonNumber >= 0) {
                    Destination.SeriesOverview(
                        itemId = seriesId,
                        type = BaseItemKind.SERIES,
                        seasonEpisode =
                            SeasonEpisodeIds(
                                seasonId = seasonId,
                                seasonNumber = seasonNumber,
                                episodeId = itemId,
                                episodeNumber = episodeNumber,
                            ),
                    )
                } else {
                    Destination.MediaItem(itemId, type)
                }
            } else {
                null
            }
        }

    fun changeDisplayMode(modeId: Int) {
        lifecycleScope.launch(Dispatchers.Main + ExceptionHandler()) {
            val attrs = window.attributes
            if (attrs.preferredDisplayModeId != modeId) {
                Timber.d("Switch preferredDisplayModeId to %s", modeId)
                window.attributes = attrs.apply { preferredDisplayModeId = modeId }
            }
        }
    }

    companion object {
        const val INTENT_ITEM_ID = "itemId"
        const val INTENT_ITEM_TYPE = "itemType"
        const val INTENT_SERIES_ID = "seriesId"
        const val INTENT_EPISODE_NUMBER = "epNum"
        const val INTENT_SEASON_NUMBER = "seaNum"
        const val INTENT_SEASON_ID = "seaId"

        lateinit var instance: MainActivity
            private set
    }
}

@HiltViewModel
class MainActivityViewModel
    @Inject
    constructor(
        private val preferences: DataStore<AppPreferences>,
        val serverRepository: ServerRepository,
        private val navigationManager: SetupNavigationManager,
        private val deviceProfileService: DeviceProfileService,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        fun appStart() {
            viewModelScope.launchIO {
                try {
                    val prefs =
                        preferences.data.firstOrNull() ?: AppPreferences.getDefaultInstance()
                    val userHasPin = serverRepository.currentUser.value?.hasPin == true
                    if (prefs.signInAutomatically && !userHasPin) {
                        val current =
                            serverRepository.restoreSession(
                                prefs.currentServerId?.toUUIDOrNull(),
                                prefs.currentUserId?.toUUIDOrNull(),
                            )
                        if (current != null) {
                            if (current.user.hasPin) {
                                navigationManager.navigateTo(SetupDestination.UserList(current.server))
                            } else {
                                // Restored
                                navigationManager.navigateTo(SetupDestination.AppContent(current))
                            }
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
