package com.github.damontecres.dolphin.ui

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.github.damontecres.dolphin.data.ServerRepository
import okhttp3.Call
import okhttp3.OkHttpClient
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalCoilApi::class)
@Composable
fun CoilConfig(
    serverRepository: ServerRepository,
    okHttpClient: OkHttpClient,
    debugLogging: Boolean,
) {
    setSingletonImageLoaderFactory { ctx ->
        ImageLoader
            .Builder(ctx)
            .diskCache(
                DiskCache
                    .Builder()
                    .directory(ctx.cacheDir.resolve("coil3_image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build(),
            ).crossfade(false)
            .logger(if (debugLogging) DebugLogger() else null)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        cacheStrategy = { CacheControlCacheStrategy() },
                        callFactory = {
                            Call.Factory { request ->
                                // Ref: https://gist.github.com/nielsvanvelzen/ea047d9028f676185832e51ffaf12a6f
                                val token = serverRepository.currentUser?.accessToken
                                okHttpClient.newCall(
                                    request
                                        .newBuilder()
                                        .addHeader("Authorization", "MediaBrowser Token=\"$token\"")
                                        .build(),
                                )
                            }
                        },
                    ),
                )
            }.build()
    }
}
