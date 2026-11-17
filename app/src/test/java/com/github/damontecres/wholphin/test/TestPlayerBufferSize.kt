package com.github.damontecres.wholphin.test

import androidx.media3.common.C
import com.github.damontecres.wholphin.util.PlayerBufferSize
import org.junit.Assert
import org.junit.Test

class TestPlayerBufferSize {
    companion object {
        private const val MIB = 1024 * 1024
        private const val GIB = 1024L * 1024 * 1024

        // MemTotal as reported by the devices
        private const val SHIELD_2019_TUBE = 1_987_624L * 1024
        private const val SHIELD_2019_PRO = 3_016_708L * 1024
        private const val EIGHT_GIB_BOX = 8 * GIB
    }

    @Test
    fun autoUsesSmallBufferOnLowMemoryDevices() {
        Assert.assertEquals(
            PlayerBufferSize.LOW_MEMORY_BUFFER_MB * MIB,
            PlayerBufferSize.targetBufferBytes(PlayerBufferSize.AUTO, SHIELD_2019_TUBE, false),
        )
        Assert.assertEquals(
            PlayerBufferSize.LOW_MEMORY_BUFFER_MB * MIB,
            PlayerBufferSize.targetBufferBytes(PlayerBufferSize.AUTO, 3 * GIB, true),
        )
    }

    @Test
    fun autoKeepsExoPlayerDefaultElsewhere() {
        Assert.assertEquals(
            C.LENGTH_UNSET,
            PlayerBufferSize.targetBufferBytes(PlayerBufferSize.AUTO, SHIELD_2019_PRO, false),
        )
        Assert.assertEquals(
            C.LENGTH_UNSET,
            PlayerBufferSize.targetBufferBytes(PlayerBufferSize.AUTO, EIGHT_GIB_BOX, false),
        )
        // Memory unknown
        Assert.assertEquals(
            C.LENGTH_UNSET,
            PlayerBufferSize.targetBufferBytes(PlayerBufferSize.AUTO, 0, false),
        )
    }

    @Test
    fun manualSettingWins() {
        Assert.assertEquals(
            144 * MIB,
            PlayerBufferSize.targetBufferBytes(144, SHIELD_2019_TUBE, true),
        )
        Assert.assertEquals(
            64 * MIB,
            PlayerBufferSize.targetBufferBytes(64, EIGHT_GIB_BOX, false),
        )
        Assert.assertEquals(
            96 * MIB,
            PlayerBufferSize.targetBufferBytes(96, SHIELD_2019_PRO, false),
        )
    }

    @Test
    fun manualSettingIsClampedToTheRange() {
        Assert.assertEquals(
            PlayerBufferSize.MANUAL_MIN_MB * MIB,
            PlayerBufferSize.targetBufferBytes(8, EIGHT_GIB_BOX, false),
        )
        Assert.assertEquals(
            PlayerBufferSize.MANUAL_MAX_MB * MIB,
            PlayerBufferSize.targetBufferBytes(512, EIGHT_GIB_BOX, false),
        )
    }

    @Test
    fun manualRangeStartsAtLowMemorySizeAndSteps() {
        Assert.assertEquals(PlayerBufferSize.LOW_MEMORY_BUFFER_MB, PlayerBufferSize.MANUAL_MIN_MB)
        Assert.assertEquals(
            0,
            (PlayerBufferSize.MANUAL_MAX_MB - PlayerBufferSize.MANUAL_MIN_MB) % PlayerBufferSize.MANUAL_STEP_MB,
        )
    }
}
