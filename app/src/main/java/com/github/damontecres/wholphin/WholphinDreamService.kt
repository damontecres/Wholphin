package com.github.damontecres.wholphin

import android.service.dreams.DreamService
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.datastore.core.DataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.components.AppScreensaverContent
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.util.ProvideLocalClock
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@AndroidEntryPoint
class WholphinDreamService :
    DreamService(),
    SavedStateRegistryOwner {
    @Inject
    lateinit var serverRepository: ServerRepository

    @Inject
    lateinit var screensaverService: ScreensaverService

    @Inject
    lateinit var preferencesDataStore: DataStore<AppPreferences>

    private val lifecycleRegistry = LifecycleRegistry(this)

    private val savedStateRegistryController =
        SavedStateRegistryController.create(this).apply {
            performAttach()
        }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleScope.launchDefault {
            if (serverRepository.current.value == null) {
                val prefs = preferencesDataStore.data.first()
                serverRepository.restoreSession(prefs.currentServerId.toUUIDOrNull(), prefs.currentUserId.toUUIDOrNull())
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val itemFlow = screensaverService.createItemFlow(lifecycleScope)
        setContentView(
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@WholphinDreamService)
                setViewTreeSavedStateRegistryOwner(this@WholphinDreamService)
                setContent {
                    val user by serverRepository.currentUser.observeAsState()
                    if (user != null) {
                        var prefs by remember { mutableStateOf<AppPreferences?>(null) }
                        LaunchedEffect(Unit) {
                            preferencesDataStore.data.collectLatest { prefs = it }
                        }
                        prefs?.let { prefs ->
                            WholphinTheme(appThemeColors = prefs.interfacePreferences.appThemeColors) {
                                ProvideLocalClock {
                                    val screensaverPrefs = prefs.interfacePreferences.screensaverPreference
                                    val currentItem by itemFlow.collectAsState(null)
                                    AppScreensaverContent(
                                        currentItem = currentItem,
                                        showClock = screensaverPrefs.showClock,
                                        duration = screensaverPrefs.duration.milliseconds,
                                        animate = screensaverPrefs.animate,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}
