package com.github.damontecres.dolphin.hilt

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.github.damontecres.dolphin.BuildConfig
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun okHttpClient() =
        OkHttpClient
            .Builder()
            .apply {
                // TODO user agent, timeouts, logging, etc
            }.build()

    @Provides
    @Singleton
    fun okHttpFactory(okHttpClient: OkHttpClient) = OkHttpFactory(okHttpClient)

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
