package com.github.damontecres.wholphin.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class BitstreamFilterChainTest {
    @Test
    fun keepsTheSizeWhenThereIsNoFilter() {
        assertEquals(DATA.size, applyBitstreamFilters(emptyList(), buffer(), 0, DATA.size))
    }

    @Test
    fun keepsTheSizeOfAnInPlaceFilter() {
        val inPlace =
            BitstreamFilter { data, offset, size ->
                data.put(offset, 0xFF.toByte())
                size
            }
        val target = buffer()

        val size = applyBitstreamFilters(listOf(inPlace), target, 0, DATA.size)

        assertEquals(DATA.size, size)
        assertArrayEquals(byteArrayOf(0xFF.toByte()) + DATA.copyOfRange(1, DATA.size), target.array())
    }

    @Test
    fun reportsTheShorterSizeOfAFilterWhichDropsBytes() {
        // What a Dolby Vision profile 7 to 8.1 conversion does: it removes the enhancement layer,
        // compacts what it keeps and comes out shorter than it went in. The buffer is direct,
        // like the decoder input buffer a filter is handed on a device: it has no backing array,
        // so the bytes are moved with absolute gets and puts.
        val dropsTheThirdAndFourthBytes =
            BitstreamFilter { data, offset, size ->
                for (i in 2 until size - 2) {
                    data.put(offset + i, data.get(offset + i + 2))
                }
                size - 2
            }
        val target = directBuffer()

        val size = applyBitstreamFilters(listOf(dropsTheThirdAndFourthBytes), target, 0, DATA.size)

        assertFalse("the test should exercise a buffer without a backing array", target.hasArray())
        assertEquals(DATA.size - 2, size)
        assertEquals(listOf<Byte>(1, 2, 5, 6, 7, 8), (0 until size).map { target.get(it) })
    }

    @Test
    fun handsTheNextFilterTheSizeReportedByThePreviousOne() {
        val seen = mutableListOf<Int>()
        val dropTwo =
            BitstreamFilter { _, _, size ->
                seen.add(size)
                size - 2
            }
        val dropOne =
            BitstreamFilter { _, _, size ->
                seen.add(size)
                size - 1
            }

        val size = applyBitstreamFilters(listOf(dropTwo, dropOne), buffer(), 0, DATA.size)

        assertEquals(listOf(DATA.size, DATA.size - 2), seen)
        assertEquals(DATA.size - 3, size)
    }

    @Test
    fun ignoresAFilterWhichReportsMoreBytesThanItWasGiven() {
        val seen = mutableListOf<Int>()
        val tooLong = BitstreamFilter { _, _, size -> size + 1 }
        val recording =
            BitstreamFilter { _, _, size ->
                seen.add(size)
                size
            }

        val size = applyBitstreamFilters(listOf(tooLong, recording), buffer(), 0, DATA.size)

        assertEquals(DATA.size, size)
        assertEquals(listOf(DATA.size), seen)
    }

    @Test
    fun ignoresAFilterWhichReportsANegativeSize() {
        val negative = BitstreamFilter { _, _, _ -> -1 }

        assertEquals(DATA.size, applyBitstreamFilters(listOf(negative), buffer(), 0, DATA.size))
    }

    @Test
    fun stopsOnceNothingIsLeftOfTheAccessUnit() {
        var called = false
        val dropsEverything = BitstreamFilter { _, _, _ -> 0 }
        val next =
            BitstreamFilter { _, _, size ->
                called = true
                size
            }

        val size = applyBitstreamFilters(listOf(dropsEverything, next), buffer(), 0, DATA.size)

        assertEquals(0, size)
        assertTrue("a filter should not be handed an empty access unit", !called)
    }

    @Test
    fun filtersAreGivenTheOffsetTheyWereCalledWith() {
        val offsets = mutableListOf<Int>()
        val recording =
            BitstreamFilter { _, offset, size ->
                offsets.add(offset)
                size
            }

        applyBitstreamFilters(listOf(recording, recording), buffer(), 2, DATA.size - 2)

        assertEquals(listOf(2, 2), offsets)
    }

    companion object {
        private val DATA = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

        private fun buffer() = ByteBuffer.wrap(DATA.copyOf())

        private fun directBuffer() =
            ByteBuffer.allocateDirect(DATA.size).also { buffer ->
                DATA.forEachIndexed { index, byte -> buffer.put(index, byte) }
            }
    }
}
