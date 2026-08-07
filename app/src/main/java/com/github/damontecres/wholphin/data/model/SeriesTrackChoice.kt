@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import com.github.damontecres.wholphin.services.isSigns
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.serializer.UUIDSerializer
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
    primaryKeys = ["userId", "parentId", "type"],
)
@Serializable
data class SeriesTrackChoice(
    val userId: Int,
    /**
     * Season or Series
     */
    val parentId: UUID,
    /**
     * Type of track, ie audio or subtitle
     */
    val type: SeriesTrackChoiceType,
    /**
     * Origin of the [parentId]
     */
    val parentType: TrackChoiceParentType = TrackChoiceParentType.UNKNOWN,
    val itemId: UUID? = null,
    val language: String? = null,
    val activation: ActivationFlag = ActivationFlag.ACTIVATED,
    val trackFlags: Int = 0,
    val codec: String? = null,
    val trackIndex: Int? = null,
    val title: String? = null,
    val audioChannels: Int? = null,
) {
    companion object {
        fun from(plc: PlaybackLanguageChoice): List<SeriesTrackChoice> =
            buildList {
                if (plc.subtitleLanguage != null) {
                    add(
                        SeriesTrackChoice(
                            userId = plc.userId,
                            parentId = plc.seriesId,
                            parentType = TrackChoiceParentType.DEPRECATED,
                            type = SeriesTrackChoiceType.SUBTITLE,
                            itemId = plc.itemId,
                            language = plc.subtitleLanguage,
                            activation = if (plc.subtitlesDisabled == true) ActivationFlag.DISABLED else ActivationFlag.ACTIVATED,
                            trackFlags = 0,
                            codec = null,
                            trackIndex = null,
                            title = null,
                            audioChannels = null,
                        ),
                    )
                }
                if (plc.audioLanguage != null) {
                    add(
                        SeriesTrackChoice(
                            userId = plc.userId,
                            parentId = plc.seriesId,
                            parentType = TrackChoiceParentType.DEPRECATED,
                            type = SeriesTrackChoiceType.AUDIO,
                            itemId = plc.itemId,
                            language = plc.audioLanguage,
                            activation = ActivationFlag.ACTIVATED,
                            trackFlags = 0,
                            codec = null,
                            trackIndex = null,
                            title = null,
                            audioChannels = null,
                        ),
                    )
                }
            }
    }
}

enum class SeriesTrackChoiceType {
    AUDIO,
    SUBTITLE,
}

/**
 * Flags for a track such as forced or defect
 */
enum class TrackFlag(
    val flag: Int,
    val hasFlag: (MediaStream) -> Boolean,
) {
    DEFAULT(flag = 1, hasFlag = { it.isDefault }),
    FORCED(flag = 2, hasFlag = { it.isForced }),
    SDH(flag = 4, hasFlag = { it.isHearingImpaired }),
    EXTERNAL(flag = 8, hasFlag = { it.isExternal }),
    SIGNS(flag = 16, hasFlag = { isSigns(it) }),
    ;

    fun within(flag: Int) = flag and this.flag == this.flag

    companion object {
        fun SeriesTrackChoice.has(flag: TrackFlag) = flag.within(trackFlags)

        fun Iterable<TrackFlag>.calculateFlag() = fold(0) { flag, trackFlag -> flag or trackFlag.flag }
    }
}

/**
 * How was the track activated
 */
enum class ActivationFlag {
    ACTIVATED,
    DISABLED,
    ONLY_FORCED,
}

/**
 * Where the [SeriesTrackChoice.parentId] comes from
 */
enum class TrackChoiceParentType {
    /**
     * Parent ID is from the series
     */
    SERIES,

    /**
     * Parent ID is from the season
     */
    SEASON,

    /**
     * Parent ID is from the deprecated [PlaybackLanguageChoice]
     */
    DEPRECATED,

    /**
     * Parent ID origin is unknown
     */
    UNKNOWN,
}
