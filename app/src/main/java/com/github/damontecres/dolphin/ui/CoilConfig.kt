package com.github.damontecres.dolphin.ui

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import okhttp3.OkHttpClient
import kotlin.time.ExperimentalTime

/**
 * Configure Coil image loading
 */
@OptIn(ExperimentalTime::class, ExperimentalCoilApi::class)
@Composable
fun CoilConfig(
    okHttpClient: OkHttpClient,
    debugLogging: Boolean,
) {
    setSingletonImageLoaderFactory { ctx ->
        ImageLoader
            .Builder(ctx)
            .memoryCache(MemoryCache.Builder().maxSizePercent(ctx).build())
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
                        callFactory = { okHttpClient },
                    ),
                )
            }.build()
    }
}
