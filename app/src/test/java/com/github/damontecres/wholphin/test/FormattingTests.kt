package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.ui.util.StreamFormatting.resolutionString
import org.junit.Assert
import org.junit.Test

class FormattingTests {
    @Test
    fun testResolutionStrings() {
        Assert.assertEquals("4K", resolutionString(3840, 2160, false))
        Assert.assertEquals("1080p", resolutionString(1920, 1080, false))
        Assert.assertEquals("720p", resolutionString(1280, 720, false))
        Assert.assertEquals("480i", resolutionString(640, 480, true))

        Assert.assertEquals("576p", resolutionString(1024, 576, false))
        Assert.assertEquals("576p", resolutionString(960, 576, false))

        // 21:9
        Assert.assertEquals("1080p", resolutionString(1920, 822, false))

        Assert.assertEquals("1440p", resolutionString(2560, 1440, false))
    }
}
