package com.github.damontecres.wholphin

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
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
import com.github.damontecres.wholphin.services.UpdateChecker
import com.github.damontecres.wholphin.services.UserSwitchListener
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.services.tvprovider.TvProviderSchedulerService
import com.github.damontecres.wholphin.ui.CoilConfig
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisodeIds
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.ApplicationContent
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.setup.SwitchServerContent
import com.github.damontecres.wholphin.ui.setup.SwitchUserContent
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.util.ProvideLocalClock
import com.github.damontecres.wholphin.util.DebugLogTree
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
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
        Timber.i("MainActivity.onCreate: savedInstanceState is null=${savedInstanceState == null}")
        lifecycle.addObserver(playbackLifecycleObserver)
        if (savedInstanceState == null) {
            appUpgradeHandler.copySubfont(false)
        }
        refreshRateService.refreshRateMode.observe(this) { modeId ->
            val attrs = window.attributes
            if (attrs.preferredDisplayModeId != modeId) {
                Timber.d("Switch preferredDisplayModeId to %s", modeId)
                window.attributes = attrs.apply { preferredDisplayModeId = modeId }
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
        
        val intentData = intent?.let { extractIntentData(it) }
        val overrideUserId = intentData?.userId ?: parseUUID(intent?.getStringExtra(INTENT_USER_ID))
        val overrideServerId = intentData?.serverId ?: parseUUID(intent?.getStringExtra(INTENT_SERVER_ID))

        if (intentData != null && intentData.itemId != null) {
            viewModel.pendingIntentData = intentData
        }

        intent?.let { 
            val data = extractIntentData(it)
            val currentUser = viewModel.serverRepository.currentUser.value
            val currentServer = viewModel.serverRepository.currentServer.value
            val needsLogin = data.serverId != null && 
                    (currentServer?.id != data.serverId || 
                     data.userId != null && currentUser?.id != data.userId)
            val needsUserSwitch = !needsLogin && data.userId != null && 
                    currentUser?.id != data.userId
            
            if (!needsLogin && !needsUserSwitch) {
                lifecycleScope.launchIO {
                    handleIntent(it, isNewIntent = false)
                }
            }
        }

        viewModel.appStart(overrideUserId, overrideServerId)
        
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
                            val backStack = setupNavigationManager.backStack
                            
                            // Process pendingIntentData when AppContent is reached
                            // Use currentUser?.id as source of truth (not destination.current.user.id)
                            // because destination might not update immediately when switching users
                            val currentDestination = backStack.lastOrNull()
                            val currentUser = viewModel.serverRepository.currentUser.value
                            val currentServer = viewModel.serverRepository.currentServer.value
                            val appContentKey = remember(currentDestination, currentUser?.id, currentServer?.id) {
                                if (currentDestination is SetupDestination.AppContent && currentUser != null && currentServer != null) {
                                    "${currentUser.id}_${currentServer.id}"
                                } else {
                                    null
                                }
                            }
                            
                            LaunchedEffect(appContentKey) {
                                if (appContentKey != null) {
                                    delay(200)
                                    val pendingIntentData = viewModel.pendingIntentData
                                    if (pendingIntentData != null && pendingIntentData.itemId != null) {
                                        val pendingItemId = pendingIntentData.itemId
                                        Timber.i("AppContent reached (key=$appContentKey), processing pendingIntentData for itemId=$pendingItemId")
                                        viewModel.pendingIntentData = null
                                        lifecycleScope.launchIO {
                                            val destination = createDestinationFromIntentData(pendingIntentData)
                                            if (destination != null) {
                                                withContext(Dispatchers.Main) {
                                                    navigationManager.replace(destination)
                                                    viewModel.lastProcessedItemId = pendingItemId
                                                    Timber.i("Successfully navigated to destination from pendingIntentData: $destination")
                                                }
                                            } else {
                                                Timber.w("Failed to create destination from pendingIntentData")
                                            }
                                        }
                                    }
                                }
                            }
                            
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
                                                                if (ex is kotlinx.coroutines.CancellationException) throw ex
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
                                                        var requestedDestination =
                                                            viewModel.pendingRequestedDestination
                                                                ?.also { viewModel.pendingRequestedDestination = null }
                                                        
                                                        if (requestedDestination == null) {
                                                            requestedDestination = intent?.let(::extractDestination)
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
        val overrideUserId = parseUUID(intent?.getStringExtra(INTENT_USER_ID))
        val overrideServerId = parseUUID(intent?.getStringExtra(INTENT_SERVER_ID))
        viewModel.appStart(overrideUserId, overrideServerId)
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
        setIntent(intent)
        
        val currentDestination = setupNavigationManager.backStack.lastOrNull()
        val isOnSetupScreen = currentDestination is SetupDestination.ServerList || 
                              currentDestination is SetupDestination.UserList
        
        if (isOnSetupScreen) {
            Timber.i("onNewIntent: On setup screen ($currentDestination), treating like cold start")
            val intentData = extractIntentData(intent)
            val overrideUserId = intentData.userId ?: parseUUID(intent.getStringExtra(INTENT_USER_ID))
            val overrideServerId = intentData.serverId ?: parseUUID(intent.getStringExtra(INTENT_SERVER_ID))
            
            if (intentData.itemId != null) {
                viewModel.pendingIntentData = intentData
            }
            
            viewModel.appStart(overrideUserId, overrideServerId)
            return
        }
        
        lifecycleScope.launchIO {
            handleIntent(intent, isNewIntent = true)
        }
    }
    
    private suspend fun handleIntent(intent: Intent, isNewIntent: Boolean = false) {
        val intentData = extractIntentData(intent)
        
        if (intentData.itemId == null) {
            Timber.d("Intent has no itemId, ignoring")
            return
        }
        
        val currentUser = viewModel.serverRepository.currentUser.value
        val currentServer = viewModel.serverRepository.currentServer.value
        
        val needsLogin = intentData.serverId != null && 
                (currentServer?.id != intentData.serverId || 
                 intentData.userId != null && currentUser?.id != intentData.userId)
        val needsUserSwitch = !needsLogin && intentData.userId != null && 
                currentUser?.id != intentData.userId
        
        val isInAppContent = setupNavigationManager.backStack.lastOrNull() is SetupDestination.AppContent
        
        if (needsLogin || needsUserSwitch) {
            Timber.i("Intent: Need to login/switch user (userId=${intentData.userId}, serverId=${intentData.serverId}). Current user: ${currentUser?.id}. Treating as fresh start.")
            
            // Stop playback before switching users to avoid stale state
            if (isInAppContent && intentData.autoplay) {
                val currentDestination = navigationManager.backStack.lastOrNull()
                if (currentDestination is Destination.Playback) {
                    Timber.i("Intent: Currently playing ${currentDestination.itemId}, stopping playback before user switch")
                    withContext(Dispatchers.Main) {
                        navigationManager.goToHome()
                    }
                }
            }
            
            viewModel.pendingIntentData = intentData
            Timber.i("Intent: Calling appStart to handle login/switch")
            viewModel.appStart(intentData.userId, intentData.serverId)
            return
        }
        
        if (isInAppContent && intentData.autoplay) {
            val currentDestination = navigationManager.backStack.lastOrNull()
            if (currentDestination is Destination.Playback && currentDestination.itemId == intentData.itemId) {
                val currentUserId = currentUser?.id
                if (currentUserId == intentData.userId || (intentData.userId == null && currentUserId != null)) {
                    Timber.i("Intent: Already playing item ${intentData.itemId} as user $currentUserId, ignoring")
                    return
                }
            }
        }
        
        if (isInAppContent) {
            Timber.i("Intent: In AppContent, navigating directly")
            lifecycleScope.launchIO {
                val destination = createDestinationFromIntentData(intentData)
                if (destination != null) {
                    withContext(Dispatchers.Main) {
                        navigationManager.replace(destination)
                        viewModel.lastProcessedItemId = intentData.itemId
                    }
                }
            }
            return
        }
        
        Timber.i("Intent: Not in AppContent yet, storing destination")
        lifecycleScope.launchIO {
            val destination = createDestinationFromIntentData(intentData)
            withContext(Dispatchers.Main) {
                viewModel.pendingRequestedDestination = destination
                viewModel.pendingIntentData = intentData
            }
        }
    }
    
    private fun extractIntentData(intent: Intent): IntentData {
        val uri = intent.data
        if (uri != null && uri.scheme == "wholphin" && uri.host == "play") {
            val itemId = parseUUID(uri.pathSegments.firstOrNull())
            val autoplay = uri.getQueryParameter("autoplay")?.toBooleanStrictOrNull() ?: false
            val type = uri.getQueryParameter("type")?.let(BaseItemKind::fromNameOrNull)
            val serverId = parseUUID(intent.getStringExtra(INTENT_SERVER_ID))
            val userId = parseUUID(intent.getStringExtra(INTENT_USER_ID))
            
            return IntentData(
                serverId = serverId,
                userId = userId,
                itemId = itemId,
                itemType = type,
                autoplay = autoplay,
                seriesId = null,
                seasonId = null,
                episodeNumber = null,
                seasonNumber = null,
            )
        }
        
        return IntentData(
            serverId = parseUUID(intent.getStringExtra(INTENT_SERVER_ID)),
            userId = parseUUID(intent.getStringExtra(INTENT_USER_ID)),
            itemId = parseUUID(intent.getStringExtra(INTENT_ITEM_ID)),
            itemType = intent.getStringExtra(INTENT_ITEM_TYPE)?.let(BaseItemKind::fromNameOrNull),
            autoplay = intent.getBooleanExtra(INTENT_AUTOPLAY, false),
            seriesId = parseUUID(intent.getStringExtra(INTENT_SERIES_ID)),
            seasonId = parseUUID(intent.getStringExtra(INTENT_SEASON_ID)),
            episodeNumber = intent.getIntExtra(INTENT_EPISODE_NUMBER, -1).takeIf { it >= 0 },
            seasonNumber = intent.getIntExtra(INTENT_SEASON_NUMBER, -1).takeIf { it >= 0 },
        )
    }
    
    private suspend fun createDestinationFromIntentData(intentData: IntentData): Destination? {
        if (intentData.itemId == null) return null
        
        val itemId = intentData.itemId
        val autoplay = intentData.autoplay
        
        var type = intentData.itemType
        if (type == null) {
            val currentUser = viewModel.serverRepository.currentUser.value
            if (currentUser != null) {
                try {
                    val itemDto = viewModel.serverRepository.apiClient.userLibraryApi.getItem(itemId).content
                    type = itemDto.type
                    Timber.d("Fetched item type ${type} for itemId $itemId")
                } catch (e: Exception) {
                    Timber.w(e, "Failed to fetch item type for $itemId")
                    return null
                }
            } else {
                Timber.d("User not logged in, cannot fetch item type for $itemId")
                return null
            }
        }
        
        if (intentData.seriesId != null && intentData.seasonId != null && 
            intentData.episodeNumber != null && intentData.seasonNumber != null) {
            return Destination.SeriesOverview(
                itemId = intentData.seriesId,
                type = BaseItemKind.SERIES,
                seasonEpisode = SeasonEpisodeIds(
                    seasonId = intentData.seasonId,
                    seasonNumber = intentData.seasonNumber,
                    episodeId = itemId,
                    episodeNumber = intentData.episodeNumber,
                ),
            )
        }
        
        if (autoplay) {
            val currentUser = viewModel.serverRepository.currentUser.value
            if (currentUser != null) {
                try {
                    val apiClient = viewModel.serverRepository.apiClient
                    
                    if (type == BaseItemKind.SERIES) {
                        Timber.d("Series autoplay: Finding next unwatched episode for series $itemId")
                        val nextUpResult = apiClient.tvShowsApi.getNextUp(seriesId = itemId).content
                        val nextEpisode = nextUpResult.items.firstOrNull() 
                            ?: apiClient.tvShowsApi.getEpisodes(seriesId = itemId, limit = 1).content.items.firstOrNull()
                        
                        if (nextEpisode != null) {
                            val resumePositionTicks = nextEpisode.userData?.playbackPositionTicks
                            val resumeMs = resumePositionTicks?.ticks?.inWholeMilliseconds ?: 0L
                            Timber.i("Series autoplay: Playing episode ${nextEpisode.id} with resume position $resumeMs ms")
                            return Destination.Playback(itemId = nextEpisode.id, positionMs = resumeMs)
                        } else {
                            Timber.w("Series autoplay: Could not find an episode to play for series $itemId")
                            return null
                        }
                    }
                    
                    if (type == BaseItemKind.SEASON) {
                        Timber.d("Season autoplay: Finding first episode for season $itemId")
                        val seasonDto = apiClient.userLibraryApi.getItem(itemId).content
                        val seriesId = seasonDto.seriesId
                        if (seriesId != null) {
                            val request = GetEpisodesRequest(
                                seriesId = seriesId,
                                seasonId = itemId,
                            )
                            val episodesResult = apiClient.tvShowsApi.getEpisodes(request).content
                            val firstEpisode = episodesResult.items.firstOrNull()
                            if (firstEpisode != null) {
                                val resumePositionTicks = firstEpisode.userData?.playbackPositionTicks
                                val resumeMs = resumePositionTicks?.ticks?.inWholeMilliseconds ?: 0L
                                Timber.i("Season autoplay: Playing first episode ${firstEpisode.id} with resume position $resumeMs ms")
                                return Destination.Playback(itemId = firstEpisode.id, positionMs = resumeMs)
                            } else {
                                Timber.w("Season autoplay: Could not find episodes for season $itemId")
                                return null
                            }
                        } else {
                            Timber.w("Season autoplay: Season $itemId has no seriesId")
                            return null
                        }
                    }
                    
                    Timber.d("Fetching item $itemId for resume position as user ${currentUser.id}")
                    val itemDto = apiClient.userLibraryApi.getItem(itemId).content
                    val resumeMs = itemDto.userData?.playbackPositionTicks?.ticks?.inWholeMilliseconds ?: 0L
                    Timber.i("Creating playback destination for $itemId with resume position $resumeMs ms (from server, user=${currentUser.id})")
                    return Destination.Playback(itemId = itemId, positionMs = resumeMs)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to fetch item for playback as user ${currentUser.id}")
                    if (type == BaseItemKind.SEASON || type == BaseItemKind.SERIES) {
                        return null
                    }
                    return Destination.Playback(itemId = itemId, positionMs = 0L)
                }
            }
            Timber.w("No current user when creating playback destination")
            if (type == BaseItemKind.SEASON || type == BaseItemKind.SERIES) {
                return null
            }
            return Destination.Playback(itemId = itemId, positionMs = 0L)
        }
        
        return Destination.MediaItem(itemId = itemId, type = type, autoPlayOnLoad = false)
    }

    private fun extractDestination(intent: Intent): Destination? =
        intent.let {
            val deepLinkDestination = parseDeepLink(it.data)
            if (deepLinkDestination != null) return deepLinkDestination

            val itemId = parseUUID(it.getStringExtra(INTENT_ITEM_ID))
            val type = it.getStringExtra(INTENT_ITEM_TYPE)?.let(BaseItemKind::fromNameOrNull)
            val autoplay = it.getBooleanExtra(INTENT_AUTOPLAY, false)
            if (itemId != null && type != null) {
                val seriesId = parseUUID(it.getStringExtra(INTENT_SERIES_ID))
                val seasonId = parseUUID(it.getStringExtra(INTENT_SEASON_ID))
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
                    if (autoplay && (type == BaseItemKind.SEASON || type == BaseItemKind.SERIES)) {
                        return null
                    }
                    Destination.MediaItem(itemId, type, autoPlayOnLoad = autoplay)
                }
            } else {
                null
            }
        }

    private fun parseDeepLink(uri: Uri?): Destination? {
        if (uri == null) return null
        if (uri.scheme != "wholphin" || uri.host != "play") return null

        val itemId = parseUUID(uri.pathSegments.firstOrNull()) ?: return null
        val autoplay = uri.getQueryParameter("autoplay")?.toBooleanStrictOrNull() ?: false
        val type = uri.getQueryParameter("type")?.let(BaseItemKind::fromNameOrNull)

        // For SEASON/SERIES with autoplay, or autoplay with unknown type, return null
        // to let createDestinationFromIntentData resolve seasons/series to episodes
        if (autoplay && (type == BaseItemKind.SEASON || type == BaseItemKind.SERIES || type == null)) {
            return null
        }
        
        return if (type != null) {
            Destination.MediaItem(itemId = itemId, type = type, autoPlayOnLoad = autoplay)
        } else {
            Timber.d("Cannot create destination for $itemId: type unknown and autoplay=false")
            null
        }
    }
    
    private fun parseUUID(input: String?): UUID? {
        if (input.isNullOrBlank()) return null
        return try {
            if (input.contains("-")) {
                UUID.fromString(input)
            } else {
                val f = input.replace("-", "")
                if (f.length == 32) {
                    val formatted = "${f.substring(0, 8)}-${f.substring(8, 12)}-${f.substring(12, 16)}-${f.substring(16, 20)}-${f.substring(20)}"
                    UUID.fromString(formatted)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Timber.w("Failed to parse UUID: $input")
            null
        }
    }

    data class IntentData(
        val serverId: UUID?,
        val userId: UUID?,
        val itemId: UUID?,
        val itemType: BaseItemKind?,
        val autoplay: Boolean,
        val seriesId: UUID?,
        val seasonId: UUID?,
        val episodeNumber: Int?,
        val seasonNumber: Int?,
    )

    companion object {
        const val INTENT_ITEM_ID = "itemId"
        const val INTENT_ITEM_TYPE = "itemType"
        const val INTENT_SERIES_ID = "seriesId"
        const val INTENT_EPISODE_NUMBER = "epNum"
        const val INTENT_SEASON_NUMBER = "seaNum"
        const val INTENT_SEASON_ID = "seaId"
        const val INTENT_USER_ID = "userId"
        const val INTENT_SERVER_ID = "serverId"
        const val INTENT_AUTOPLAY = "autoplay"
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

        var pendingRequestedDestination: Destination? = null
        var lastProcessedItemId: UUID? = null
        var pendingIntentData: MainActivity.IntentData? = null

        fun appStart(overrideUserId: java.util.UUID? = null, overrideServerId: java.util.UUID? = null) {
            viewModelScope.launchIO {
                try {
                    val prefs =
                        preferences.data.firstOrNull() ?: AppPreferences.getDefaultInstance()
                    val userHasPin = serverRepository.currentUser.value?.hasPin == true

                    if (overrideUserId != null) {
                        Timber.i("Automation: Override active for user $overrideUserId")
                        val targetServerId = overrideServerId ?: prefs.currentServerId?.toUUIDOrNull()
                        val current =
                            serverRepository.restoreSession(
                                targetServerId,
                                overrideUserId,
                            )
                        if (current != null) {
                            Timber.i("Automation: Login success, navigating to app")
                            navigationManager.navigateTo(SetupDestination.AppContent(current))
                            return@launchIO
                        } 
                        else {
                             Timber.w("Automation: Login failed for user $overrideUserId. Fallback to server selection.")
                             if (targetServerId != null) {
                                 val server = serverRepository.serverDao.getServer(targetServerId)?.server
                                 if (server != null) {
                                     navigationManager.navigateTo(SetupDestination.UserList(server))
                                     return@launchIO
                                 }
                             }
                        }
                    }

                    if (prefs.signInAutomatically && !userHasPin) {
                        val current =
                            serverRepository.restoreSession(
                                prefs.currentServerId?.toUUIDOrNull(),
                                prefs.currentUserId?.toUUIDOrNull(),
                            )
                        if (current != null) {
                            navigationManager.navigateTo(SetupDestination.AppContent(current))
                        } else {
                            navigationManager.navigateTo(SetupDestination.ServerList)
                        }
                    } else {
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
                            // If sign in automatically is disabled and only one server exists, skip server select
                            val servers = serverRepository.serverDao.getServers()
                            if (servers.size == 1) {
                                val singleServer = servers.first().server
                                navigationManager.navigateTo(SetupDestination.UserList(singleServer))
                            } else {
                                navigationManager.navigateTo(SetupDestination.ServerList)
                            }
                        }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error during appStart")
                    navigationManager.navigateTo(SetupDestination.ServerList)
                }
            }
            viewModelScope.launchIO {
                deviceProfileService.mediaCodecCapabilitiesTest.supportsAVC()
            }
        }
    }