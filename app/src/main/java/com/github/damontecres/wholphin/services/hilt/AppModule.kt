package com.github.damontecres.wholphin.services.hilt

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.work.WorkManager
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.preferences.updateInterfacePreferences
import com.github.damontecres.wholphin.services.SeerrApi
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.RememberTabManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.android.androidDevice
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

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoCoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun clientInfo(
        @ApplicationContext context: Context,
    ): ClientInfo =
        ClientInfo(
            name = context.getString(R.string.app_name),
            version = BuildConfig.VERSION_NAME,
        )

    @Provides
    @Singleton
    fun deviceInfo(
        @ApplicationContext context: Context,
    ): DeviceInfo = androidDevice(context)

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
                serverRepository.currentUser.value?.accessToken?.let { token ->
                    request
                        .newBuilder()
                        .addHeader(
                            "Authorization",
                            AuthorizationHeaderBuilder.buildHeader(
                                clientName = clientInfo.name,
                                clientVersion = clientInfo.version,
                                deviceId = deviceInfo.id,
                                deviceName = deviceInfo.name,
                                accessToken = token,
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

    /**
     * Implementation of [RememberTabManager] which remembers by server, user, & item
     */
    @Provides
    @Singleton
    fun rememberTabManager(
        serverRepository: ServerRepository,
        appPreference: DataStore<AppPreferences>,
        @IoCoroutineScope scope: CoroutineScope,
    ) = object : RememberTabManager {
        fun key(itemId: String) = "${serverRepository.currentServer.value?.id}_${serverRepository.currentUser.value?.id}_$itemId"

        override fun getRememberedTab(
            preferences: UserPreferences,
            itemId: String,
            defaultTab: Int,
        ): Int {
            if (preferences.appPreferences.interfacePreferences.rememberSelectedTab) {
                return preferences.appPreferences.interfacePreferences
                    .getRememberedTabsOrDefault(key(itemId), defaultTab)
            } else {
                return defaultTab
            }
        }

        override fun saveRememberedTab(
            preferences: UserPreferences,
            itemId: String,
            tabIndex: Int,
        ) {
            if (preferences.appPreferences.interfacePreferences.rememberSelectedTab) {
                scope.launch(ExceptionHandler()) {
                    appPreference.updateData {
                        preferences.appPreferences.updateInterfacePreferences {
                            putRememberedTabs(key(itemId), tabIndex)
                        }
                    }
                }
            }
        }
    }

    @Provides
    @Singleton
    @IoCoroutineScope
    fun ioCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun workManager(
        @ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun seerrApi(
        @StandardOkHttpClient okHttpClient: OkHttpClient,
    ) = SeerrApi(okHttpClient)
}
