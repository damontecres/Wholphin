package com.github.damontecres.wholphin.test

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.jellyfin.sdk.model.api.MediaStreamType

class TestTracks private constructor(
    val tracks: List<TestTrack> = emptyList(),
) {
    fun toTracks(): Tracks {
        val groups =
            tracks
                .map {
                    val mimeType =
                        when (it.type) {
                            MediaStreamType.VIDEO -> "video/sample"
                            MediaStreamType.AUDIO -> "audio/default"
                            MediaStreamType.SUBTITLE -> "audio/text"
                            else -> throw UnsupportedOperationException("${it.type}")
                        }
                    Format
                        .Builder()
                        .setId(it.id)
                        .setSampleMimeType(mimeType)
                        .build()
                }.map { TrackGroup(it) }
                .map { Tracks.Group(it, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false)) }
        return Tracks(groups)
    }

    class Builder {
        private val tracks = mutableListOf<TestTrackBuilder>()

        fun addVideo(): Builder {
            tracks.add(TestTrackBuilder(MediaStreamType.VIDEO))
            return this
        }

        fun addAudio(): Builder {
            tracks.add(TestTrackBuilder(MediaStreamType.AUDIO))
            return this
        }

        fun addSubtitle(): Builder {
            tracks.add(TestTrackBuilder(MediaStreamType.SUBTITLE, false))
            return this
        }

        fun addExternalSubtitle(): Builder {
            tracks.add(TestTrackBuilder(MediaStreamType.SUBTITLE, true))
            return this
        }

        fun buildForExoPlayer(): TestTracks {
            val testTracks =
                buildList {
                    var externalSubCount = -1
                    val t =
                        tracks
                            .mapIndexed { index, track ->
                                val idx = index + 1
                                if (track.external) {
                                    externalSubCount++
                                    TestTrack("0:e:$externalSubCount", index, track.type, track.external)
                                } else {
                                    TestTrack("$idx", index, track.type, track.external)
                                }
                            }
                    addAll(t)
                }
            return TestTracks(testTracks)
        }

        fun buildForMpv() {
        }
    }
}

data class TestTrack(
    val id: String,
    val index: Int,
    val type: MediaStreamType,
    val external: Boolean,
)

class TestTrackBuilder(
    val type: MediaStreamType,
    val external: Boolean = false,
)
