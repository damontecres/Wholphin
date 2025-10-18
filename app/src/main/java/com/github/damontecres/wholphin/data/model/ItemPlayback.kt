@file:UseSerializers(UuidSerializer::class)

package com.github.damontecres.wholphin.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.damontecres.wholphin.data.JellyfinUser
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.util.UuidSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SubtitlePlaybackMode
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.util.UUID

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = JellyfinUser::class,
            parentColumns = arrayOf("rowId"),
            childColumns = arrayOf("userId"),
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("userId", "itemId", unique = true)],
)
@Serializable
data class ItemPlayback(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,
    val userId: Int,
    val itemId: UUID,
    val sourceId: UUID? = null,
    val audioIndex: Int = TrackIndex.UNSPECIFIED,
    val subtitleIndex: Int = TrackIndex.UNSPECIFIED,
) {
    @Transient val audioIndexEnabled = audioIndex >= 0

    @Transient val subtitleIndexEnabled = subtitleIndex >= 0
}

/**
 * Returns the [MediaSourceInfo] with the highest video resolution
 */
fun chooseSource(sources: List<MediaSourceInfo>?) =
    sources?.letNotEmpty { sources ->
        val result =
            sources.maxByOrNull { s ->
                s.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }?.let { video ->
                    (video.width ?: 0) * (video.height ?: 0)
                } ?: 0
            }
        result
    }

/**
 * Returns the [MediaSourceInfo] that matched the [ItemPlayback] or else the one with the highest resolution
 */
fun chooseSource(
    dto: BaseItemDto,
    itemPlayback: ItemPlayback?,
) = itemPlayback?.sourceId?.let { dto.mediaSources?.firstOrNull { it.id?.toUUIDOrNull() == itemPlayback.sourceId } }
    ?: chooseSource(dto.mediaSources) // dto.mediaSources?.firstOrNull()

fun chooseStream(
    dto: BaseItemDto,
    itemPlayback: ItemPlayback?,
    type: MediaStreamType,
    prefs: UserPreferences,
): MediaStream? {
    val source = chooseSource(dto, itemPlayback)
    return source?.mediaStreams?.letNotEmpty { streams ->
        val candidates = streams.filter { it.type == type }
        when (type) {
            MediaStreamType.AUDIO -> chooseAudioStream(candidates, itemPlayback, prefs)
            MediaStreamType.SUBTITLE -> chooseSubtitleStream(candidates, itemPlayback, prefs)
            else -> candidates.firstOrNull()
        }
    }
}

fun chooseAudioStream(
    candidates: List<MediaStream>,
    itemPlayback: ItemPlayback?,
    prefs: UserPreferences,
): MediaStream? =
    if (itemPlayback?.audioIndexEnabled == true) {
        candidates.firstOrNull { it.index == itemPlayback.audioIndex }
    } else {
        // TODO audio selection based on channel layout or preferences or default
        val audioLanguage = prefs.userConfig.audioLanguagePreference
        if (audioLanguage.isNotNullOrBlank()) {
            val sorted =
                candidates.sortedWith(compareBy<MediaStream> { it.language }.thenByDescending { it.channels })
            sorted.firstOrNull { it.language == audioLanguage && it.isDefault }
                ?: sorted.firstOrNull { it.language == audioLanguage }
                ?: sorted.firstOrNull { it.isDefault }
                ?: sorted.firstOrNull()
        } else {
            candidates.firstOrNull { it.isDefault }
                ?: candidates.firstOrNull()
        }
    }

fun chooseSubtitleStream(
    candidates: List<MediaStream>,
    itemPlayback: ItemPlayback?,
    prefs: UserPreferences,
): MediaStream? {
    if (itemPlayback?.subtitleIndex == TrackIndex.DISABLED) {
        return null
    } else if (itemPlayback?.subtitleIndexEnabled == true) {
        return candidates.firstOrNull { it.index == itemPlayback.subtitleIndex }
    } else {
        val subtitleLanguage = prefs.userConfig.subtitleLanguagePreference
        return when (prefs.userConfig.subtitleMode) {
            SubtitlePlaybackMode.ALWAYS -> {
                if (subtitleLanguage != null) {
                    candidates.firstOrNull { it.language == subtitleLanguage }
                } else {
                    candidates.firstOrNull()
                }
            }

            SubtitlePlaybackMode.ONLY_FORCED ->
                if (subtitleLanguage != null) {
                    candidates.firstOrNull { it.language == subtitleLanguage && it.isForced }
                } else {
                    candidates.firstOrNull { it.isForced }
                }

            SubtitlePlaybackMode.SMART -> {
                val audioLanguage = prefs.userConfig.audioLanguagePreference
                if (audioLanguage != null && subtitleLanguage != null && audioLanguage != subtitleLanguage) {
                    candidates.firstOrNull { it.language == subtitleLanguage }
                } else {
                    null
                }
            }

            SubtitlePlaybackMode.DEFAULT -> {
                // TODO check for language?
                (
                    candidates.firstOrNull { it.isDefault && it.isForced }
                        ?: candidates.firstOrNull { it.isDefault }
                        ?: candidates.firstOrNull { it.isForced }
                )
            }

            SubtitlePlaybackMode.NONE -> null
        }
    }
}

object TrackIndex {
    /**
     * The user has not explicitly specified a track to use
     */
    const val UNSPECIFIED = -1

    /**
     * The user has explicitly disabled the tracks (eg turned off subtitles)
     */
    const val DISABLED = -2
}
