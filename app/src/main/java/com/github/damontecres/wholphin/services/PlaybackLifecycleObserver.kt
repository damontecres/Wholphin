package com.github.damontecres.wholphin.services

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.github.damontecres.wholphin.ui.nav.Destination
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

/**
 * Observes the activity lifecycle in order to pause/resume/stop playback
 */
@ActivityRetainedScoped
class PlaybackLifecycleObserver
    @Inject
    constructor(
        private val navigationManager: NavigationManager,
        private val playerFactory: PlayerFactory,
        private val themeSongPlayer: ThemeSongPlayer,
    ) : DefaultLifecycleObserver {
        private var wasPlaying: Boolean? = null

        override fun onStart(owner: LifecycleOwner) {
            wasPlaying = null
        }

        override fun onResume(owner: LifecycleOwner) {
            if (wasPlaying == true) {
                playerFactory.currentPlayer?.let {
                    if (!it.isReleased) it.play()
                }
            }
        }

        override fun onPause(owner: LifecycleOwner) {
            playerFactory.currentPlayer?.let {
                wasPlaying = it.isPlaying
                it.pause()
            }
            themeSongPlayer.stop()
        }

        override fun onStop(owner: LifecycleOwner) {
            if (navigationManager.backStack.lastOrNull() is Destination.Playback) {
                navigationManager.goBack()
            }
            themeSongPlayer.stop()
        }
    }
