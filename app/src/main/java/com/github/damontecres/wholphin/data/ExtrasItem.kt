package com.github.damontecres.wholphin.data

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.nav.Destination
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.ExtraType

/**
 * Represents "extras" for media such as behind-the-scenes or deleted scenes
 */
sealed interface ExtrasItem {
    val parentId: UUID
    val type: ExtraType
    val destination: Destination
    val imageUrl: String?
    val title: String
    val subtitle: String?
    val isPlayed: Boolean
    val playedPercentage: Double

    /**
     * Represents multiple extras of the same type
     */
    data class Group(
        override val parentId: UUID,
        override val type: ExtraType,
        val items: List<BaseItem>,
        override val imageUrl: String?,
        override val title: String,
        override val subtitle: String,
        override val isPlayed: Boolean,
    ) : ExtrasItem {
        override val destination: Destination =
            Destination.ItemGrid(null, type.stringRes, items.map { it.id })

        override val playedPercentage
            get() = -1.0
    }

    /**
     * Represents a single extra
     */
    data class Single(
        override val parentId: UUID,
        override val type: ExtraType,
        val item: BaseItem,
        override val imageUrl: String?,
        override val title: String,
        override val subtitle: String?,
    ) : ExtrasItem {
        override val destination: Destination =
            Destination.Playback(
                item = item,
            )
        override val isPlayed: Boolean
            get() = item.played
        override val playedPercentage
            get() = item.data.userData?.playedPercentage ?: 0.0
    }
}

/**
 * Converts [ExtraType] to the string resource ID
 */
@get:StringRes
val ExtraType.stringRes: Int
    get() =
        when (this) {
            ExtraType.UNKNOWN -> R.string.other_extras
            ExtraType.CLIP -> R.string.clips
            ExtraType.TRAILER -> R.string.trailers
            ExtraType.BEHIND_THE_SCENES -> R.string.behind_the_scenes
            ExtraType.DELETED_SCENE -> R.string.deleted_scenes
            ExtraType.INTERVIEW -> R.string.interviews
            ExtraType.SCENE -> R.string.scenes
            ExtraType.SAMPLE -> R.string.samples
            ExtraType.THEME_SONG -> R.string.theme_songs
            ExtraType.THEME_VIDEO -> R.string.theme_videos
            ExtraType.FEATURETTE -> R.string.featurettes
            ExtraType.SHORT -> R.string.shorts
        }

/**
 * Converts [ExtraType] to the plural resource ID
 */
@get:PluralsRes
val ExtraType.pluralRes: Int
    get() =
        when (this) {
            ExtraType.UNKNOWN -> R.plurals.other_extras
            ExtraType.CLIP -> R.plurals.clips
            ExtraType.TRAILER -> R.plurals.trailers
            ExtraType.BEHIND_THE_SCENES -> R.plurals.behind_the_scenes
            ExtraType.DELETED_SCENE -> R.plurals.deleted_scenes
            ExtraType.INTERVIEW -> R.plurals.interviews
            ExtraType.SCENE -> R.plurals.scenes
            ExtraType.SAMPLE -> R.plurals.samples
            ExtraType.THEME_SONG -> R.plurals.theme_songs
            ExtraType.THEME_VIDEO -> R.plurals.theme_videos
            ExtraType.FEATURETTE -> R.plurals.featurettes
            ExtraType.SHORT -> R.plurals.shorts
        }
