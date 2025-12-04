@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.annotation.OptIn
import androidx.datastore.core.DataStore
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.MediaExtensionStatus
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.util.mpv.MpvPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Constructs a [Player] instance for video playback
 */
@Singleton
class PlayerFactory
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val appPreferences: DataStore<AppPreferences>,
    ) {
        @Volatile
        var currentPlayer: Player? = null
            private set

        fun createVideoPlayer(): Player {
            if (currentPlayer?.isReleased == false) {
                Timber.w("Player was not released before trying to create a new one!")
                currentPlayer?.release()
            }

            val prefs = runBlocking { appPreferences.data.firstOrNull()?.playbackPreferences }
            val backend = prefs?.playerBackend ?: AppPreference.PlayerBackendPref.defaultValue
            val newPlayer =
                when (backend) {
                    PlayerBackend.MPV -> {
                        val enableHardwareDecoding =
                            prefs?.mpvOptions?.enableHardwareDecoding
                                ?: AppPreference.MpvHardwareDecoding.defaultValue
                        val useGpuNext =
                            prefs?.mpvOptions?.useGpuNext
                                ?: AppPreference.MpvGpuNext.defaultValue
                        MpvPlayer(context, enableHardwareDecoding, useGpuNext)
                            .apply {
                                playWhenReady = true
                            }
                    }

                    PlayerBackend.EXO_PLAYER,
                    PlayerBackend.UNRECOGNIZED,
                    -> {
                        val extensions =
                            runBlocking { appPreferences.data.firstOrNull() }?.playbackPreferences?.overrides?.mediaExtensionsEnabled
                        Timber.v("extensions=$extensions")
                        val rendererMode =
                            when (extensions) {
                                MediaExtensionStatus.MES_FALLBACK -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                                MediaExtensionStatus.MES_PREFERRED -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                                MediaExtensionStatus.MES_DISABLED -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                                else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                            }
                        ExoPlayer
                            .Builder(context)
                            .setRenderersFactory(
                                DefaultRenderersFactory(context)
                                    .setEnableDecoderFallback(true)
                                    .setExtensionRendererMode(rendererMode),
                            ).build()
                            .apply {
                                playWhenReady = true
                            }
                    }
                }
            currentPlayer = newPlayer
            return newPlayer
        }
    }

val Player.isReleased: Boolean
    get() {
        return when (this) {
            is ExoPlayer -> isReleased
            is MpvPlayer -> isReleased
            else -> throw IllegalStateException("Unknown Player type: ${this::class.qualifiedName}")
        }
    }
