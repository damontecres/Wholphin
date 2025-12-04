package com.github.damontecres.wholphin.data

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.nav.Destination
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.ExtraType

sealed interface ExtrasItem {
    val parentId: UUID
    val type: ExtraType
    val destination: Destination
    val title: String?

    data class Group(
        override val parentId: UUID,
        override val type: ExtraType,
        val items: List<BaseItem>,
    ) : ExtrasItem {
        override val destination: Destination =
            Destination.ItemGrid(null, type.stringRes, items.map { it.id })

        override val title: String? = null
    }

    data class Single(
        override val parentId: UUID,
        override val type: ExtraType,
        val item: BaseItem,
    ) : ExtrasItem {
        override val destination: Destination =
            Destination.Playback(
                item = item,
            )
        override val title: String? get() = item.title
    }
}

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
