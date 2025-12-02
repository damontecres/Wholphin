package com.github.damontecres.wholphin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import timber.log.Timber
import kotlin.time.ExperimentalTime

/**
 * Configure Coil image loading
 */
@OptIn(ExperimentalTime::class, ExperimentalCoilApi::class)
@Composable
fun CoilConfig(
    diskCacheSizeBytes: Long,
    okHttpClient: OkHttpClient,
    debugLogging: Boolean,
    enableCache: Boolean = true,
) {
    val client =
        remember(okHttpClient, debugLogging) {
            if (debugLogging) {
                okHttpClient
                    .newBuilder()
                    .addInterceptor {
                        val start = System.currentTimeMillis()
                        val req = it.request()
                        val res = it.proceed(req)
                        val time = System.currentTimeMillis() - start
                        Timber.v("${time}ms - ${req.url}")
                        res
                    }.build()
            } else {
                okHttpClient
            }
        }
    setSingletonImageLoaderFactory { ctx ->
        Timber.i("Image diskCacheSizeBytes=$diskCacheSizeBytes")
        ImageLoader
            .Builder(ctx)
            .apply {
                if (enableCache) {
                    memoryCache(MemoryCache.Builder().maxSizePercent(ctx).build())
                    diskCache(
                        DiskCache
                            .Builder()
                            .directory(ctx.cacheDir.resolve("coil3_image_cache"))
                            .maxSizeBytes(diskCacheSizeBytes)
                            .build(),
                    )
                } else {
                    memoryCache(null)
                    diskCache(null)
                }
            }.crossfade(false)
            .logger(if (debugLogging) DebugLogger() else null)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        cacheStrategy = { CacheControlCacheStrategy() },
                        callFactory = { client },
                    ),
                )
            }.build()
    }
}
