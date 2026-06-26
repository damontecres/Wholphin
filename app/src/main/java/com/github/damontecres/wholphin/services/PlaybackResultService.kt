package com.github.damontecres.wholphin.services

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes the locally-known outcome of a just-finished or just-toggled playback (final resume
 * position and whether the item is now considered played) on a flow.
 *
 * ViewModels anywhere in the nav back stack can [playbackResultFlow] and update their state in the
 * background, immediately and race-free, instead of re-querying the server (which would race the
 * asynchronous playback-stopped report). Modelled on [MediaManagementService.deletedItemFlow].
 */
@Singleton
class PlaybackResultService
    @Inject
    constructor() {
        private val _playbackResultFlow =
            MutableSharedFlow<PlaybackResult>(
                replay = 1,
                extraBufferCapacity = 0,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )

        /**
         * Listen for recently-finished playbacks. A late subscriber receives the most recent result
         * (replay = 1), so a freshly created ViewModel still sees an outcome reported while it did
         * not yet exist.
         */
        val playbackResultFlow: SharedFlow<PlaybackResult> = _playbackResultFlow

        /**
         * Record the authoritative local outcome of a playback. Non-suspending so it can be called
         * from non-coroutine contexts (e.g. player teardown); with replay = 1 and DROP_OLDEST,
         * [tryEmit] never fails and always retains the latest value.
         */
        fun record(
            itemId: UUID,
            positionTicks: Long,
            played: Boolean,
        ) {
            _playbackResultFlow.tryEmit(PlaybackResult(itemId, positionTicks, played))
        }
    }

data class PlaybackResult(
    val itemId: UUID,
    val positionTicks: Long,
    val played: Boolean,
)
