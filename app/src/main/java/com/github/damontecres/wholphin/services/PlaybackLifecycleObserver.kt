package com.github.damontecres.wholphin.services

import android.app.Activity
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
            val lastDest = navigationManager.backStack.lastOrNull()
            if (lastDest is Destination.Playback || lastDest is Destination.PlaybackList) {
                navigationManager.goBack()
            }
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
            // Skip pausing when activity is finishing (e.g. nuclear restart): the player will be
            // released as the activity is destroyed, and pause() can hit "Handler on a dead thread"
            // if the player's handler is torn down before the message is processed.
            if ((owner as? Activity)?.isFinishing != true) {
                playerFactory.currentPlayer?.let {
                    if (!it.isReleased) {
                        wasPlaying = it.isPlaying
                        it.pause()
                    }
                }
            }
            themeSongPlayer.stop()
        }

        override fun onStop(owner: LifecycleOwner) {
            themeSongPlayer.stop()
        }
    }
