package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.preferences.PlaybackPreferences
import com.github.damontecres.wholphin.util.profile.MediaCodecCapabilitiesTest
import com.github.damontecres.wholphin.util.profile.createDeviceProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.api.DeviceProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceProfileService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        val mediaCodecCapabilitiesTest by lazy {
            // Created lazily below on the IO thread since it cn take time
            MediaCodecCapabilitiesTest(context)
        }
        private val mutex = Mutex()

        private var configuration: DeviceProfileConfiguration? = null
        private var deviceProfile: DeviceProfile? = null

        suspend fun getOrCreateDeviceProfile(
            prefs: PlaybackPreferences,
            serverVersion: ServerVersion?,
        ): DeviceProfile =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val newConfig =
                        DeviceProfileConfiguration(
                            maxBitrate = prefs.maxBitrate.toInt(),
                            isAC3Enabled = prefs.overrides.ac3Supported,
                            downMixAudio = prefs.overrides.downmixStereo,
                            assDirectPlay = prefs.overrides.directPlayAss,
                            pgsDirectPlay = prefs.overrides.directPlayPgs,
                            jellyfinTenEleven =
                                serverVersion != null && serverVersion >= ServerVersion(10, 11, 0),
                        )
                    if (deviceProfile == null || this@DeviceProfileService.configuration != newConfig) {
                        this@DeviceProfileService.configuration = newConfig
                        this@DeviceProfileService.deviceProfile =
                            createDeviceProfile(
                                mediaTest = mediaCodecCapabilitiesTest,
                                maxBitrate = newConfig.maxBitrate,
                                isAC3Enabled = newConfig.isAC3Enabled,
                                downMixAudio = newConfig.downMixAudio,
                                assDirectPlay = newConfig.assDirectPlay,
                                pgsDirectPlay = newConfig.pgsDirectPlay,
                                jellyfinTenEleven = newConfig.jellyfinTenEleven,
                            )
                    }
                    this@DeviceProfileService.deviceProfile!!
                }
            }
    }

/**
 * The configuration used in [createDeviceProfile]
 */
data class DeviceProfileConfiguration(
    val maxBitrate: Int,
    val isAC3Enabled: Boolean,
    val downMixAudio: Boolean,
    val assDirectPlay: Boolean,
    val pgsDirectPlay: Boolean,
    val jellyfinTenEleven: Boolean,
)
