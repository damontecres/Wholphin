package com.github.damontecres.dolphin.hilt

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.github.damontecres.dolphin.BuildConfig
import com.github.damontecres.dolphin.data.ServerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StandardOkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @StandardOkHttpClient
    @Provides
    @Singleton
    fun okHttpClient() =
        OkHttpClient
            .Builder()
            .apply {
                // TODO user agent, timeouts, logging, etc
            }.build()

    @AuthOkHttpClient
    @Provides
    @Singleton
    fun authOkHttpClient(
        serverRepository: ServerRepository,
        @StandardOkHttpClient okHttpClient: OkHttpClient,
    ) = okHttpClient
        .newBuilder()
        .addInterceptor {
            val request = it.request()
            val newRequest =
                serverRepository.currentUser?.accessToken?.let { token ->
                    request
                        .newBuilder()
                        .addHeader(
                            "Authorization",
                            "MediaBrowser Token=\"$token\"",
                        ).build()
                }
            it.proceed(newRequest ?: request)
        }.build()

    @Provides
    @Singleton
    fun okHttpFactory(
        @StandardOkHttpClient okHttpClient: OkHttpClient,
    ) = OkHttpFactory(okHttpClient)

    @Provides
    @Singleton
    fun jellyfin(
        okHttpFactory: OkHttpFactory,
        @ApplicationContext context: Context,
    ): Jellyfin =
        createJellyfin {
            this.context = context
            clientInfo =
                ClientInfo(
                    name = "Dolphin",
                    version = BuildConfig.VERSION_NAME,
                )
            deviceInfo =
                DeviceInfo(
                    id = @SuppressLint("HardwareIds") Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID),
                    name = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME),
                )
            apiClientFactory = okHttpFactory
            socketConnectionFactory = okHttpFactory
            minimumServerVersion = Jellyfin.minimumVersion
        }

    @Provides
    @Singleton
    fun apiClient(jellyfin: Jellyfin) = jellyfin.createApi()
}
