package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.data.model.TrackFlag
import com.github.damontecres.wholphin.data.model.TrackFlag.Companion.calculateFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.EnumSet

class TestTrackFlag {
    @Test
    fun basicTest() {
        val flag = listOf(TrackFlag.DEFAULT, TrackFlag.EXTERNAL).calculateFlag()
        assertTrue(TrackFlag.DEFAULT.within(flag))
        assertTrue(TrackFlag.EXTERNAL.within(flag))
        assertFalse(TrackFlag.FORCED.within(flag))
        assertFalse(TrackFlag.SDH.within(flag))
    }

    @Test
    fun none() {
        val flag = EnumSet.noneOf(TrackFlag::class.java).calculateFlag()
        assertFalse(TrackFlag.DEFAULT.within(flag))
        assertFalse(TrackFlag.EXTERNAL.within(flag))
        assertFalse(TrackFlag.FORCED.within(flag))
        assertFalse(TrackFlag.SDH.within(flag))
    }

    @Test
    fun all() {
        val flag = EnumSet.allOf(TrackFlag::class.java).calculateFlag()
        TrackFlag.entries.forEach { assertTrue(it.toString(), it.within(flag)) }
    }

    @Test
    fun checkFlags() {
        TrackFlag.entries.forEach {
            assertTrue("$it is not a power of two", it.flag > 0 && (it.flag and (it.flag - 1)) == 0)
        }
        val distinctFlags = TrackFlag.entries.map { it.flag }.distinct()
        assertEquals(TrackFlag.entries.size, distinctFlags.size)
    }
}
