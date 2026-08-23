package com.github.damontecres.wholphin.util.profile

import android.util.Size
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceProfileUtilsTest {
    @Test
    fun generatedHevcRangeMarkers_relaxOnlySingleLayerHybridDolbyVision() {
        data class TestCase(
            val workaroundActive: Boolean,
            val supportsHevcDolbyVision: Boolean,
            val jellyfinTenEleven: Boolean,
            val knownDefect: Boolean,
            val expectedMarkers: Set<String>,
        )

        val cases =
            listOf(
                TestCase(
                    workaroundActive = false,
                    supportsHevcDolbyVision = true,
                    jellyfinTenEleven = true,
                    knownDefect = true,
                    expectedMarkers =
                        setOf("DOVIInvalid", "DOVIWithHDR10Plus", "DOVIWithELHDR10Plus"),
                ),
                TestCase(
                    workaroundActive = true,
                    supportsHevcDolbyVision = true,
                    jellyfinTenEleven = true,
                    knownDefect = true,
                    expectedMarkers = setOf("DOVIInvalid", "DOVIWithELHDR10Plus"),
                ),
                TestCase(
                    workaroundActive = true,
                    supportsHevcDolbyVision = false,
                    jellyfinTenEleven = true,
                    knownDefect = true,
                    expectedMarkers =
                        setOf("DOVIInvalid", "DOVIWithHDR10Plus", "DOVIWithELHDR10Plus"),
                ),
                TestCase(
                    workaroundActive = true,
                    supportsHevcDolbyVision = true,
                    jellyfinTenEleven = false,
                    knownDefect = true,
                    expectedMarkers = emptySet(),
                ),
                TestCase(
                    workaroundActive = true,
                    supportsHevcDolbyVision = true,
                    jellyfinTenEleven = true,
                    knownDefect = false,
                    expectedMarkers = setOf("DOVIInvalid"),
                ),
            )

        cases.forEach { case ->
            val profile =
                createProfile(
                    workaroundActive = case.workaroundActive,
                    supportsHevcDolbyVision = case.supportsHevcDolbyVision,
                    jellyfinTenEleven = case.jellyfinTenEleven,
                    knownDefect = case.knownDefect,
                )

            assertEquals(case.toString(), case.expectedMarkers, profile.hevcRangeMarkers())
        }
    }

    private fun createProfile(
        workaroundActive: Boolean,
        supportsHevcDolbyVision: Boolean,
        jellyfinTenEleven: Boolean,
        knownDefect: Boolean,
    ): DeviceProfile {
        val mediaTest = mockk<MediaCodecCapabilitiesTest>(relaxed = true)
        every { mediaTest.supportsHevc() } returns true
        every { mediaTest.supportsHevcMain10() } returns true
        every { mediaTest.supportsHevcDolbyVision() } returns supportsHevcDolbyVision
        every { mediaTest.supportsHevcDolbyVisionEL() } returns true
        every { mediaTest.supportsHevcHDR10() } returns true
        every { mediaTest.supportsHevcHDR10Plus() } returns true
        every { mediaTest.supportsAV1DolbyVision() } returns true
        every { mediaTest.supportsAV1HDR10() } returns true
        every { mediaTest.supportsAV1HDR10Plus() } returns true
        val resolution = mockk<Size>()
        every { resolution.width } returns 3840
        every { resolution.height } returns 2160
        every { mediaTest.getMaxResolution(any()) } returns resolution

        return createDeviceProfile(
            mediaTest = mediaTest,
            maxBitrate = 120_000_000,
            isAC3Enabled = true,
            downMixAudio = false,
            assDirectPlay = true,
            pgsDirectPlay = true,
            dolbyVisionELDirectPlay = false,
            fireTvHybridDolbyVisionWorkaround = workaroundActive,
            decodeAv1 = false,
            jellyfinTenEleven = jellyfinTenEleven,
            preferAc3ForSurround = false,
            hevcDoviHdr10PlusDefect = knownDefect,
        )
    }

    private fun DeviceProfile.hevcRangeMarkers(): Set<String> =
        codecProfiles
            .asSequence()
            .filter { it.codec == Codec.Video.HEVC }
            .flatMap { it.applyConditions.asSequence() }
            .filter { it.property == ProfileConditionValue.VIDEO_RANGE_TYPE }
            .flatMap { it.value.orEmpty().split('|').asSequence() }
            .filter { it.isNotEmpty() }
            .toSet()
}
