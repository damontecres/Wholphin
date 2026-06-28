package com.github.damontecres.wholphin.services

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the locally-known outcome of a just-finished playback (final resume position and
 * whether the item is now considered played), keyed by item id.
 *
 * A grid returning from playback can apply this immediately instead of re-querying the server,
 * which would race the asynchronous playback-stopped report (a write-then-read race).
 */
@Singleton
class PlaybackResultCache
    @Inject
    constructor() {
        data class Result(
            val positionTicks: Long,
            val played: Boolean,
        )

        private val results = ConcurrentHashMap<UUID, Result>()

        fun record(
            itemId: UUID,
            positionTicks: Long,
            played: Boolean,
        ) {
            results[itemId] = Result(positionTicks, played)
        }

        fun take(itemId: UUID): Result? = results.remove(itemId)
    }
