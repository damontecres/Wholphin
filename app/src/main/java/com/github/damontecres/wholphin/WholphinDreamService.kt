package com.github.damontecres.wholphin

import android.service.dreams.DreamService
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.components.AppScreensaverContent
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@AndroidEntryPoint
class WholphinDreamService :
    DreamService(),
    SavedStateRegistryOwner {
    @Inject
    lateinit var screensaverService: ScreensaverService

    @Inject
    lateinit var userPreferencesService: UserPreferencesService

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
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val itemFlow = screensaverService.createItemFlow(lifecycleScope)
        setContentView(
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@WholphinDreamService)
                setViewTreeSavedStateRegistryOwner(this@WholphinDreamService)
                setContent {
                    var prefs by remember { mutableStateOf<UserPreferences?>(null) }
                    LaunchedEffect(Unit) {
                        userPreferencesService.flow.collectLatest { prefs = it }
                    }
                    prefs?.let { prefs ->
                        WholphinTheme(appThemeColors = prefs.appPreferences.interfacePreferences.appThemeColors) {
                            val screensaverPrefs =
                                prefs.appPreferences.interfacePreferences.screensaverPreference
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
