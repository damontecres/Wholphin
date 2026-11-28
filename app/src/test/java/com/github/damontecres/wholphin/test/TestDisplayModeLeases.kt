package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.services.DisplayModeLeases
import org.junit.Assert
import org.junit.Test

class TestDisplayModeLeases {
    private val modeChanges = mutableListOf<Int>()
    private val leases = DisplayModeLeases(modeChanges::add)

    @Test
    fun `Closing an earlier lease after a later one changed the mode keeps the later mode`() {
        val earlier = leases.open()
        val later = leases.open()
        earlier.change(8)
        later.change(14)

        earlier.close()

        Assert.assertEquals(listOf(8, 14), modeChanges)
    }

    @Test
    fun `Closing the lease that changed the mode last resets it`() {
        val earlier = leases.open()
        val later = leases.open()
        earlier.change(8)
        later.change(14)

        later.close()

        Assert.assertEquals(listOf(8, 14, 0), modeChanges)
    }

    @Test
    fun `Closing a lease that never changed the mode does not reset it`() {
        leases.open().close()

        Assert.assertEquals(emptyList<Int>(), modeChanges)
    }

    @Test
    fun `A closed lease no longer changes the mode`() {
        val lease = leases.open()
        lease.close()

        lease.change(8)

        Assert.assertEquals(emptyList<Int>(), modeChanges)
    }
}
