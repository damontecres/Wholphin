package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.util.Version
import org.junit.Assert
import org.junit.Test

class VersionCompareTests {
    companion object {
        val V_0_2_0 = Version.fromString("v0.2.0")
        val V_0_1_0 = Version.fromString("v0.1.0")
        val V_0_1_1 = Version.fromString("v0.1.1")

        val V_0_2_0_0 = Version.fromString("v0.2.0-0-gabc1234")
        val V_0_2_0_7 = Version.fromString("v0.2.0-7-gabc1234")
        val V_0_1_0_7 = Version.fromString("v0.1.0-7-gabc1234")
        val V_0_1_0_6 = Version.fromString("v0.1.0-6-gabc1234")
        val V_0_1_1_4 = Version.fromString("v0.1.1-4-gabc1234")
        val V_0_1_1_5 = Version.fromString("v0.1.1-5-gabc1234")
    }

    @Test
    fun testIsAtLeast() {
        Assert.assertTrue(V_0_2_0.isAtLeast(V_0_2_0))
        Assert.assertTrue(V_0_2_0.isAtLeast(V_0_1_0))
        Assert.assertTrue(V_0_1_1.isAtLeast(V_0_1_0))
        Assert.assertFalse(V_0_1_0.isAtLeast(V_0_2_0))
        Assert.assertFalse(V_0_1_1.isAtLeast(V_0_2_0))

        Assert.assertTrue(V_0_2_0.isAtLeast(V_0_2_0_0))
        Assert.assertTrue(V_0_2_0_0.isAtLeast(V_0_2_0))
        Assert.assertTrue(V_0_1_0_7.isAtLeast(V_0_1_0_7))
        Assert.assertTrue(V_0_1_0_7.isAtLeast(V_0_1_0_6))

        Assert.assertFalse(V_0_1_0_6.isAtLeast(V_0_1_0_7))
    }

    @Test
    fun testisGreaterThan() {
        Assert.assertFalse(V_0_2_0.isGreaterThan(V_0_2_0))
        Assert.assertTrue(V_0_2_0.isGreaterThan(V_0_1_0))
        Assert.assertTrue(V_0_1_1.isGreaterThan(V_0_1_0))
        Assert.assertFalse(V_0_1_0.isGreaterThan(V_0_2_0))

        Assert.assertFalse(V_0_2_0.isGreaterThan(V_0_2_0_0))
        Assert.assertFalse(V_0_2_0_0.isGreaterThan(V_0_2_0))
        Assert.assertFalse(V_0_1_0_7.isGreaterThan(V_0_1_0_7))
        Assert.assertTrue(V_0_1_0_7.isGreaterThan(V_0_1_0_6))

        Assert.assertTrue(V_0_2_0_7.isGreaterThan(V_0_2_0_0))
        Assert.assertTrue(V_0_1_1_4.isGreaterThan(V_0_1_0_6))
        Assert.assertTrue(V_0_1_1_4.isGreaterThan(V_0_1_0))
        Assert.assertFalse(V_0_1_1_4.isGreaterThan(V_0_1_1_5))
        Assert.assertTrue(V_0_1_1_5.isGreaterThan(V_0_1_1_4))
    }

    @Test
    fun testEqualOrBefore() {
        Assert.assertTrue(V_0_2_0.isEqualOrBefore(V_0_2_0))
        Assert.assertTrue(V_0_1_1.isEqualOrBefore(V_0_2_0))
        Assert.assertTrue(V_0_1_1.isEqualOrBefore(V_0_1_1_5))
        Assert.assertTrue(V_0_1_1_4.isEqualOrBefore(V_0_1_1_5))

        Assert.assertFalse(V_0_2_0.isEqualOrBefore(V_0_1_1))
    }

    @Test
    fun testLessThan() {
        Assert.assertFalse(V_0_2_0.isLessThan(V_0_2_0))
        Assert.assertTrue(V_0_1_1.isLessThan(V_0_2_0))
        Assert.assertTrue(V_0_1_1.isLessThan(V_0_1_1_5))
        Assert.assertTrue(V_0_1_1_4.isLessThan(V_0_1_1_5))

        Assert.assertFalse(V_0_2_0.isLessThan(V_0_1_1))
    }

    @Test
    fun testToString() {
        Assert.assertEquals("v0.2.0", V_0_2_0.toString())
        Assert.assertEquals("v0.2.0", V_0_2_0_0.toString())
        Assert.assertEquals("v0.3.0", Version(0, 3, 0, null).toString())
        Assert.assertEquals("v0.3.0", Version(0, 3, 0, 0, "abc1234").toString())
        Assert.assertEquals("v0.2.0-1-gabc1234", Version(0, 2, 0, 1, "abc1234").toString())
        Assert.assertEquals("v0.2.0-7-gabc1234", V_0_2_0_7.toString())
    }
}
