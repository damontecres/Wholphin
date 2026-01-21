@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.services

import android.content.Context
import android.os.Build
import android.os.Handler
import androidx.annotation.OptIn
import androidx.datastore.core.DataStore
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.MediaExtensionStatus
import com.github.damontecres.wholphin.preferences.PlaybackPreferences
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.util.mpv.MpvPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.reflect.Constructor
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
                    PlayerBackend.PREFER_MPV,
                    PlayerBackend.MPV,
                    -> {
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
                        val extensions = prefs?.overrides?.mediaExtensionsEnabled
                        val decodeAv1 = prefs?.overrides?.decodeAv1 == true
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
                                WholphinRenderersFactory(context, decodeAv1)
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

        suspend fun createVideoPlayer(
            backend: PlayerBackend,
            prefs: PlaybackPreferences,
        ): Player {
            withContext(Dispatchers.Main) {
                if (currentPlayer?.isReleased == false) {
                    Timber.w("Player was not released before trying to create a new one!")
                    currentPlayer?.release()
                }
            }

            val newPlayer =
                when (backend) {
                    PlayerBackend.PREFER_MPV,
                    PlayerBackend.MPV,
                    -> {
                        val enableHardwareDecoding = prefs.mpvOptions.enableHardwareDecoding
                        val useGpuNext = prefs.mpvOptions.useGpuNext
                        MpvPlayer(context, enableHardwareDecoding, useGpuNext)
                    }

                    PlayerBackend.EXO_PLAYER,
                    PlayerBackend.UNRECOGNIZED,
                    -> {
                        val extensions = prefs.overrides.mediaExtensionsEnabled
                        val decodeAv1 = prefs.overrides.decodeAv1
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
                                WholphinRenderersFactory(context, decodeAv1)
                                    .setEnableDecoderFallback(true)
                                    .setExtensionRendererMode(rendererMode),
                            ).build()
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

// Code is adapted from https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultRenderersFactory.java#L436
class WholphinRenderersFactory(
    context: Context,
    private val av1Enabled: Boolean,
) : DefaultRenderersFactory(context) {
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        var videoRendererBuilder =
            MediaCodecVideoRenderer
                .Builder(context)
                .setCodecAdapterFactory(codecAdapterFactory)
                .setMediaCodecSelector(mediaCodecSelector)
                .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
                .setEnableDecoderFallback(enableDecoderFallback)
                .setEventHandler(eventHandler)
                .setEventListener(eventListener)
                .setMaxDroppedFramesToNotify(MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)
                .experimentalSetParseAv1SampleDependencies(false)
                .experimentalSetLateThresholdToDropDecoderInputUs(C.TIME_UNSET)
        if (Build.VERSION.SDK_INT >= 34) {
            videoRendererBuilder =
                videoRendererBuilder.experimentalSetEnableMediaCodecBufferDecodeOnlyFlag(
                    false,
                )
        }
        out.add(videoRendererBuilder.build())

        if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) {
            return
        }
        var extensionRendererIndex = out.size
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
            extensionRendererIndex--
        }

        if (av1Enabled) {
            try {
                val clazz = Class.forName("androidx.media3.decoder.av1.Libdav1dVideoRenderer")
                val constructor: Constructor<*> =
                    clazz.getConstructor(
                        Long::class.javaPrimitiveType,
                        Handler::class.java,
                        VideoRendererEventListener::class.java,
                        Int::class.javaPrimitiveType,
                    )
                val renderer =
                    constructor.newInstance(
                        allowedVideoJoiningTimeMs,
                        eventHandler,
                        eventListener,
                        MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY,
                    ) as Renderer
                out.add(extensionRendererIndex++, renderer)
                Timber.i("Loaded Libdav1dVideoRenderer.")
            } catch (e: Exception) {
                // The extension is present, but instantiation failed.
                throw java.lang.IllegalStateException("Error instantiating AV1 extension", e)
            }
        }
    }
}
