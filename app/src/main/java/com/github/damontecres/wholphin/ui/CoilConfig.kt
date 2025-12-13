package com.github.damontecres.wholphin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.CacheStrategy
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.Options
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
                        cacheStrategy = { WholphinCacheStrategy(CacheControlCacheStrategy()) },
                        callFactory = { client },
                    ),
                )
            }.build()
    }
}

@OptIn(ExperimentalCoilApi::class)
private class WholphinCacheStrategy(
    private val delegate: CacheStrategy,
) : CacheStrategy {
    override suspend fun read(
        cacheResponse: NetworkResponse,
        networkRequest: NetworkRequest,
        options: Options,
    ): CacheStrategy.ReadResult =
        if (networkRequest.url.contains("/Trickplay/")) {
            CacheStrategy.ReadResult(cacheResponse)
        } else {
            delegate.read(cacheResponse, networkRequest, options)
        }

    override suspend fun write(
        cacheResponse: NetworkResponse?,
        networkRequest: NetworkRequest,
        networkResponse: NetworkResponse,
        options: Options,
    ): CacheStrategy.WriteResult = delegate.write(cacheResponse, networkRequest, networkResponse, options)
}
