package com.github.damontecres.wholphin.ui

import android.content.Context
import com.github.damontecres.wholphin.services.hilt.DeviceModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import org.jellyfin.sdk.model.DeviceInfo
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DeviceModule::class],
)
class TestModule {
    @Provides
    @Singleton
    fun deviceInfo(
        @ApplicationContext context: Context,
    ): DeviceInfo = DeviceInfo("test_device_id", "test_device")
}
