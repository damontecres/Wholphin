@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.annotation.OptIn
import androidx.datastore.core.DataStore
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.MediaExtensionStatus
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.util.mpv.MpvPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.factory.AssRenderersFactory
import io.github.peerless2012.ass.media.kt.withAssMkvSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
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

        fun createVideoPlayer(): PlayerCreation {
            if (currentPlayer?.isReleased == false) {
                Timber.w("Player was not released before trying to create a new one!")
                currentPlayer?.release()
            }
            var assHandler: AssHandler? = null
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
                        val extensions = prefs?.overrides?.mediaExtensionsEnabled
                        val directPlayAss =
                            prefs?.overrides?.directPlayAss
                                ?: AppPreference.DirectPlayAss.defaultValue
                        Timber.v("extensions=$extensions, directPlayAss=$directPlayAss")
                        val rendererMode =
                            when (extensions) {
                                MediaExtensionStatus.MES_FALLBACK -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                                MediaExtensionStatus.MES_PREFERRED -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                                MediaExtensionStatus.MES_DISABLED -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                                else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                            }
                        val dataSourceFactory = DefaultDataSource.Factory(context)
                        val extractorsFactory = DefaultExtractorsFactory()
                        var renderersFactory: RenderersFactory =
                            DefaultRenderersFactory(context)
                                .setEnableDecoderFallback(true)
                                .setExtensionRendererMode(rendererMode)
                        val mediaSourceFactory =
                            if (directPlayAss) {
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
                        ExoPlayer
                            .Builder(context)
                            .setMediaSourceFactory(mediaSourceFactory)
                            .setRenderersFactory(renderersFactory)
                            .build()
                            .apply {
                                assHandler?.init(this)
                                playWhenReady = true
                            }
                    }
                }
            currentPlayer = newPlayer
            return PlayerCreation(newPlayer, assHandler)
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
