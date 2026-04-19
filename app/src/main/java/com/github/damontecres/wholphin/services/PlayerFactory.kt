@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.services

import android.content.Context
import android.os.Build
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.extractor.DefaultExtractorsFactory
import com.github.damontecres.wholphin.preferences.AssPlaybackMode
import com.github.damontecres.wholphin.preferences.MediaExtensionStatus
import com.github.damontecres.wholphin.preferences.PlaybackPreferences
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.util.mpv.MpvPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.factory.AssRenderersFactory
import io.github.peerless2012.ass.media.kt.withAssMkvSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
        @param:AuthOkHttpClient private val authOkHttpClient: OkHttpClient,
    ) {
        @Volatile
        var currentPlayer: Player? = null
            private set

        suspend fun createVideoPlayer(
            backend: PlayerBackend,
            prefs: PlaybackPreferences,
        ): PlayerCreation {
            if (currentPlayer?.isReleased == false) {
                withContext(Dispatchers.Main) {
                    Timber.w("Player was not released before trying to create a new one!")
                    currentPlayer?.release()
                }
            }
            var assHandler: AssHandler? = null
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
                        val useLibAss =
                            prefs.overrides.assPlaybackMode == AssPlaybackMode.ASS_LIBASS
                        val decodeAv1 = prefs.overrides.decodeAv1
                        Timber.v(
                            "extensions=%s, assPlaybackMode=%s",
                            extensions,
                            prefs.overrides.assPlaybackMode,
                        )
                        val rendererMode =
                            when (extensions) {
                                MediaExtensionStatus.MES_FALLBACK -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                                MediaExtensionStatus.MES_PREFERRED -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                                MediaExtensionStatus.MES_DISABLED -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                                else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                            }
                        val dataSourceFactory = DefaultDataSource.Factory(context)
                        val extractorsFactory = createExtractorsFactory()
                        var renderersFactory: RenderersFactory =
                            WholphinRenderersFactory(context, decodeAv1)
                                .setEnableDecoderFallback(true)
                                .setExtensionRendererMode(rendererMode)
                        val mediaSourceFactory =
                            if (useLibAss) {
                                assHandler = AssHandler(AssRenderType.OVERLAY_OPEN_GL)
                                val assSubtitleParserFactory = AssSubtitleParserFactory(assHandler)
                                renderersFactory = AssRenderersFactory(assHandler, renderersFactory)
                                DefaultMediaSourceFactory(
                                    dataSourceFactory,
                                    extractorsFactory.withAssMkvSupport(
                                        assSubtitleParserFactory,
                                        assHandler,
                                    ),
                                ).setSubtitleParserFactory(assSubtitleParserFactory)
                            } else {
                                DefaultMediaSourceFactory(
                                    dataSourceFactory,
                                    extractorsFactory,
                                )
                            }
                        val trackSelector = createTrackSelector()

                        ExoPlayer
                            .Builder(context)
                            .setMediaSourceFactory(mediaSourceFactory)
                            .setRenderersFactory(renderersFactory)
                            .setTrackSelector(trackSelector)
                            .build()
                            .apply {
                                assHandler?.init(this)
                                withContext(Dispatchers.Main) {
                                    setAudioAttributes(
                                        AudioAttributes
                                            .Builder()
                                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                                            .build(),
                                        false,
                                    )
                                }
                            }
                    }

                    PlayerBackend.EXTERNAL_PLAYER -> {
                        throw IllegalArgumentException("Cannot create a player for external playback")
                    }
                }
            currentPlayer = newPlayer
            return PlayerCreation(newPlayer, assHandler)
        }

        fun createAudioPlayer(extensions: MediaExtensionStatus = MediaExtensionStatus.MES_FALLBACK): ExoPlayer {
            val rendererMode =
                when (extensions) {
                    MediaExtensionStatus.MES_FALLBACK -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    MediaExtensionStatus.MES_PREFERRED -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    MediaExtensionStatus.MES_DISABLED -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                    else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                }
            val extractorsFactory = createExtractorsFactory()
            val renderersFactory: RenderersFactory =
                WholphinRenderersFactory(context, false)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(rendererMode)
            val mediaSourceFactory =
                DefaultMediaSourceFactory(
                    OkHttpDataSource.Factory(authOkHttpClient),
                    extractorsFactory,
                )
            val trackSelector = createTrackSelector()
            return ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector)
                .build()
                .also {
                    it.setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .build(),
                        false,
                    )
                }
        }

        private fun createExtractorsFactory() =
            DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
                .setConstantBitrateSeekingAlwaysEnabled(true)

        private fun createTrackSelector() =
            DefaultTrackSelector(context).apply {
                setParameters(
                    buildUponParameters()
                        .setAudioOffloadPreferences(
                            AudioOffloadPreferences
                                .Builder()
                                .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                                .build(),
                        ),
                )
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

data class PlayerCreation(
    val player: Player,
    val assHandler: AssHandler? = null,
)

// Code is adapted from https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultRenderersFactory.java#L436
class WholphinRenderersFactory(
    context: Context,
    private val av1Enabled: Boolean,
) : DefaultRenderersFactory(context) {
    @OptIn(ExperimentalApi::class)
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
