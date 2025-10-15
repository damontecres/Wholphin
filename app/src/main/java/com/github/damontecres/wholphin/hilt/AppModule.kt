package com.github.damontecres.wholphin.hilt

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.data.ServerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
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
    @Provides
    @Singleton
    fun clientInfo(): ClientInfo =
        ClientInfo(
            name = "Wholphin",
            version = BuildConfig.VERSION_NAME,
        )

    @Provides
    @Singleton
    fun deviceInfo(
        @ApplicationContext context: Context,
    ): DeviceInfo =
        DeviceInfo(
            id = @SuppressLint("HardwareIds") Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID,
            ),
            name = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME),
        )

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
        clientInfo: ClientInfo,
        deviceInfo: DeviceInfo,
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
                            AuthorizationHeaderBuilder.buildHeader(
                                clientName = clientInfo.name,
                                clientVersion = clientInfo.version,
                                deviceId = deviceInfo.id,
                                deviceName = deviceInfo.name,
                                accessToken = serverRepository.currentUser?.accessToken,
                            ),
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
        clientInfo: ClientInfo,
        deviceInfo: DeviceInfo,
    ): Jellyfin =
        createJellyfin {
            this.context = context
            this.clientInfo = clientInfo
            this.deviceInfo = deviceInfo
            apiClientFactory = okHttpFactory
            socketConnectionFactory = okHttpFactory
            minimumServerVersion = Jellyfin.minimumVersion
        }

    @Provides
    @Singleton
    fun apiClient(jellyfin: Jellyfin) = jellyfin.createApi()
}
