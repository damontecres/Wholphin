@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.services

import android.content.Context
import android.os.Build
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
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
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.MediaSession
import com.github.damontecres.wholphin.mpv.MpvPlayer
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.AssPlaybackMode
import com.github.damontecres.wholphin.preferences.MediaExtensionStatus
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.preferences.get
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.factory.AssRenderersFactory
import io.github.peerless2012.ass.media.kt.withAssMkvSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
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
            appPreferences: AppPreferences,
        ): PlayerCreation {
            val prefs = appPreferences.playbackPreferences
            withContext(WholphinDispatchers.Main) {
                if (currentPlayer?.isReleased == false) {
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
                        val forceAc3Transcoding = prefs.overrides.forceAc3Transcoding
                        var rendererMode =
                            when (extensions) {
                                MediaExtensionStatus.MES_FALLBACK -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                                MediaExtensionStatus.MES_PREFERRED -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                                MediaExtensionStatus.MES_DISABLED -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                                else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                            }
                        if (forceAc3Transcoding &&
                            rendererMode != DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                        ) {
                            rendererMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                        }
                        val dataSourceFactory = DefaultDataSource.Factory(context)
                        val extractorsFactory = createExtractorsFactory()
                        var renderersFactory: RenderersFactory =
                            WholphinRenderersFactory(context, decodeAv1, forceAc3Transcoding)
                                .setEnableDecoderFallback(true)
                                .setExtensionRendererMode(rendererMode)

                        val mediaSourceFactory =
                            if (useLibAss) {
                                val renderType =
                                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                                        AssRenderType.OVERLAY_CANVAS
                                    } else {
                                        AssRenderType.OVERLAY_OPEN_GL
                                    }
                                assHandler = AssHandler(renderType)
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
                        val tunneling =
                            appPreferences.experimentalPreferences.get { videoTunnelingEnabled }
                        val trackSelector = createTrackSelector(tunneling, forceAc3Transcoding)

                        ExoPlayer
                            .Builder(context)
                            .setMediaSourceFactory(mediaSourceFactory)
                            .setRenderersFactory(renderersFactory)
                            .setTrackSelector(trackSelector)
                            .build()
                            .apply {
                                assHandler?.init(this)
                                withContext(WholphinDispatchers.Main) {
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

        fun createAudioPlayer(
            extensions: MediaExtensionStatus = MediaExtensionStatus.MES_FALLBACK,
            forceAc3Transcoding: Boolean = false,
        ): ExoPlayer {
            var rendererMode =
                when (extensions) {
                    MediaExtensionStatus.MES_FALLBACK -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    MediaExtensionStatus.MES_PREFERRED -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    MediaExtensionStatus.MES_DISABLED -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                    else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                }
            if (forceAc3Transcoding &&
                rendererMode != DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            ) {
                rendererMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            }
            val extractorsFactory = createExtractorsFactory()
            val renderersFactory: RenderersFactory =
                WholphinRenderersFactory(context, false, forceAc3Transcoding)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(rendererMode)
            val mediaSourceFactory =
                DefaultMediaSourceFactory(
                    OkHttpDataSource.Factory(authOkHttpClient),
                    extractorsFactory,
                )
            val trackSelector = createTrackSelector(forceAc3Transcoding)
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

        private fun createTrackSelector(
            tunneling: Boolean? = null,
            forceAc3Transcoding: Boolean = false,
        ): DefaultTrackSelector {
            val offloadMode =
                if (forceAc3Transcoding) {
                    AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                } else {
                    AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                }
            return DefaultTrackSelector(context).apply {
                setParameters(
                    buildUponParameters()
                        .apply {
                            tunneling?.let { setTunnelingEnabled(tunneling) }
                        }.setAudioOffloadPreferences(
                            AudioOffloadPreferences
                                .Builder()
                                .setAudioOffloadMode(offloadMode)
                                .build(),
                        ),
                )
            }
        }

        fun createMediaSession(player: Player) =
            MediaSession
                .Builder(context, player)
                .build()
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
    private val forceAc3Transcoding: Boolean = false,
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

    @OptIn(ExperimentalApi::class)
    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        // Forced AC3 mode needs explicit routing: AC3 can use MediaCodec passthrough, while
        // patched FFmpeg handles all other audio and only AC3-transcodes multichannel inputs.

        val effectiveSink: AudioSink = if (forceAc3Transcoding) {
            buildAc3TranscodingAudioSink(context, audioSink)
        } else {
            audioSink
        }

        if (forceAc3Transcoding) {
            val ac3PassthroughSelector =
                MediaCodecSelector { mimeType: String, requiresSecureDecoder: Boolean, requiresTunnelingDecoder: Boolean ->
                    if (mimeType == MimeTypes.AUDIO_AC3
                        // Only AC3 gets direct passthrough; E-AC-3 (DD+) goes through FFmpeg transcoding
                    ) {
                        MediaCodecSelector.DEFAULT.getDecoderInfos(
                            mimeType,
                            requiresSecureDecoder,
                            requiresTunnelingDecoder,
                        )
                    } else {
                        emptyList()
                    }
                }

            val mediaCodecRenderer = MediaCodecAudioRenderer(
                context,
                ac3PassthroughSelector,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                effectiveSink,
            )
            out.add(mediaCodecRenderer)

            try {
                val clazz = Class.forName("androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer")
                val constructor = clazz.getConstructor(
                    Handler::class.java,
                    AudioRendererEventListener::class.java,
                    AudioSink::class.java,
                )
                val ffmpegRenderer =
                    constructor.newInstance(eventHandler, eventListener, effectiveSink) as Renderer
                try {
                    val method =
                        clazz.getMethod("setForceOpticalPassthrough", Boolean::class.javaPrimitiveType)
                    method.invoke(ffmpegRenderer, true)
                } catch (e: NoSuchMethodException) {
                    Timber.tag("AC3Transcode").e("setForceOpticalPassthrough method NOT found - AAR may not be the transcoding version!")
                } catch (e: Exception) {
                    Timber.tag("AC3Transcode").e(e, "setForceOpticalPassthrough invocation failed")
                }
                out.add(ffmpegRenderer)
            } catch (e: ClassNotFoundException) {
                Timber.tag("AC3Transcode").e("FFmpeg extension not found")
            } catch (e: NoSuchMethodException) {
                Timber.tag("AC3Transcode").e("FFmpeg extension constructor mismatch - check AAR version")
            } catch (e: Exception) {
                Timber.tag("AC3Transcode").e(e, "Failed to instantiate FfmpegAudioRenderer")
            }
        } else {
            val mediaCodecRenderer = MediaCodecAudioRenderer(
                context,
                mediaCodecSelector,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                audioSink,
            )
            out.add(mediaCodecRenderer)

            if (extensionRendererMode != EXTENSION_RENDERER_MODE_OFF) {
                try {
                    val clazz = Class.forName("androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer")
                    val constructor = clazz.getConstructor(
                        Handler::class.java,
                        AudioRendererEventListener::class.java,
                        AudioSink::class.java,
                    )
                    val ffmpegRenderer =
                        constructor.newInstance(eventHandler, eventListener, audioSink) as Renderer
                    out.add(ffmpegRenderer)
                } catch (e: ClassNotFoundException) {
                    Timber.tag("AC3Transcode").d("FFmpeg extension not available for audio")
                } catch (e: Exception) {
                    Timber.tag("AC3Transcode").e(e, "Failed to instantiate FfmpegAudioRenderer")
                }
            }
        }
    }

    private fun buildAc3TranscodingAudioSink(context: Context, baseSink: AudioSink? = null): AudioSink {
        // Preserve the default sink capabilities when possible, then overlay the encodings
        // required by the patched FFmpeg renderer's support checks.
        val maxChannelCount = 8
        val mergedEncodings = mutableSetOf<Int>()
        try {
            val sinkClass = Class.forName("androidx.media3.exoplayer.audio.DefaultAudioSink")
            val mergeCapabilities = { sink: Any ->
                try {
                    val field = sinkClass.getDeclaredField("audioCapabilities")
                    field.isAccessible = true
                    val fieldValue = field.get(sink)
                    val supportedEncodingsField = fieldValue.javaClass.getDeclaredField("supportedEncodings")
                    supportedEncodingsField.isAccessible = true
                    val existingEncodings = supportedEncodingsField.get(fieldValue) as IntArray
                    existingEncodings.forEach { mergedEncodings.add(it) }
                } catch (_: NoSuchFieldException) {
                }
            }
            if (baseSink != null) mergeCapabilities(baseSink)
        } catch (_: Exception) {
        }

        val standardEncodings = intArrayOf(
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_FLOAT,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT,
        )
        standardEncodings.forEach { mergedEncodings.add(it) }

        val forcedEncodings = intArrayOf(
            C.ENCODING_AC3,
            C.ENCODING_E_AC3,
            C.ENCODING_E_AC3_JOC,
            C.ENCODING_DTS,
            C.ENCODING_DTS_HD,
        )
        forcedEncodings.forEach { mergedEncodings.add(it) }

        val combinedEncodings = mergedEncodings.toIntArray()
        val audioCapabilities = AudioCapabilities(combinedEncodings, maxChannelCount)

        return DefaultAudioSink.Builder(context)
            .setAudioCapabilities(audioCapabilities)
            .build()
    }
}
