package com.github.damontecres.wholphin.services

import android.view.WindowManager
import com.github.damontecres.wholphin.MainActivity
import com.github.damontecres.wholphin.services.hilt.DefaultCoroutineScope
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class ScreensaverService
    @Inject
    constructor(
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
        private val navigationManager: NavigationManager,
        private val userPreferencesService: UserPreferencesService,
    ) {
        private val _state = MutableStateFlow(ScreensaverState(false, false, false))
        val state: StateFlow<ScreensaverState> = _state

        private var waitJob: Job? = null

        init {
            userPreferencesService.flow
                .onEach {
                    _state.update {
                        // TODO prefs
                        keepScreenOnInternal(true)
                        ScreensaverState(true, false, false)
                    }
                }.launchIn(scope)
        }

        fun pulse() {
            waitJob?.cancel()
            if (_state.value.enabled) {
                Timber.v("pulse")
                _state.update {
                    if (!it.active) {
                        it.copy(active = false)
                    } else {
                        it
                    }
                }

                if (!_state.value.paused) {
                    waitJob =
                        scope.launch(ExceptionHandler()) {
                            // TODO
                            delay(10.seconds)
                            _state.update {
                                it.copy(active = true)
                            }
                        }
                }
            }
        }

        fun stop() {
            _state.update { it.copy(active = false) }
        }

        fun keepScreenOn(keep: Boolean) {
            scope.launch {
                val screensaverEnabled = true // TODO userPreferencesService.getCurrent().appPreferences.interfacePreferences
                Timber.d("Keep screen on: %s, screensaverEnabled=%s", keep, screensaverEnabled)
                if (screensaverEnabled) {
                    // Page is requesting to keep screen on, so we don't wait to show the screensaver
                    _state.update {
                        it.copy(active = false, paused = keep)
                    }
                    if (!keep) {
                        pulse()
                    }
                } else {
                    keepScreenOnInternal(keep)
                }
            }
        }

        private fun keepScreenOnInternal(keep: Boolean) {
            val window = MainActivity.instance.window
            if (keep) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

data class ScreensaverState(
    val enabled: Boolean,
    val active: Boolean,
    val paused: Boolean,
) {
    val show get() = enabled && active && !paused
}
