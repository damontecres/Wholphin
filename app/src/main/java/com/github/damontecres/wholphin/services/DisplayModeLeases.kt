package com.github.damontecres.wholphin.services

/**
 * Lets each playback reset the display mode when it ends, unless a later playback has changed the mode since.
 * A new playback can start before the previous one has ended.
 */
class DisplayModeLeases(
    private val changeDisplayMode: (modeId: Int) -> Unit,
) {
    private var lastChangedBy: Lease? = null

    fun open(): Lease = Lease()

    inner class Lease : AutoCloseable {
        private var closed = false

        fun change(modeId: Int) {
            synchronized(this@DisplayModeLeases) {
                if (closed) return
                lastChangedBy = this
                changeDisplayMode(modeId)
            }
        }

        /**
         * Reset the display mode to the original, unless another lease has changed it since this one did
         */
        override fun close() {
            synchronized(this@DisplayModeLeases) {
                closed = true
                if (lastChangedBy === this) {
                    lastChangedBy = null
                    changeDisplayMode(0)
                }
            }
        }
    }
}
