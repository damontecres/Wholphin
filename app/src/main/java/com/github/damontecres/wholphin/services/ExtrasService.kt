package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.pluralRes
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.ExtraType
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Get extras for media
 *
 * @see [ExtrasItem]
 */
@Singleton
class ExtrasService
    @Inject
    constructor(
        private val api: ApiClient,
        @param:ApplicationContext private val context: Context,
        private val imageUrlService: ImageUrlService,
    ) {
        /**
         * Get the [ExtrasItem]s for the given item
         */
        suspend fun getExtras(itemId: UUID): List<ExtrasItem> {
            val extrasMap =
                api.userLibraryApi
                    .getSpecialFeatures(itemId)
                    .content
                    .filterNot {
                        it.extraType == ExtraType.THEME_SONG ||
                            it.extraType == ExtraType.THEME_VIDEO ||
                            it.extraType == ExtraType.TRAILER
                    }.map { BaseItem.from(it, api) }
                    .groupBy { it.data.extraType ?: ExtraType.UNKNOWN }

            val result =
                extrasMap
                    .mapNotNull { (type, items) ->
                        if (items.size == 1) {
                            val item = items.first()
                            val title =
                                item.title ?: context.resources.getQuantityString(type.pluralRes, 1)
                            val subtitle =
                                if (item.title == null) {
                                    context.resources.getQuantityString(type.pluralRes, 1)
                                } else {
                                    null
                                }
                            ExtrasItem.Single(
                                parentId = itemId,
                                type = type,
                                item = item,
                                title = title,
                                subtitle = subtitle,
                                imageUrl = imageUrlService.getItemImageUrl(item, ImageType.PRIMARY),
                            )
                        } else if (items.size > 1) {
                            val title =
                                context.resources.getQuantityString(type.pluralRes, items.size)
                            val subtitle =
                                context.resources.getQuantityString(
                                    R.plurals.items,
                                    items.size,
                                    items.size,
                                )
                            ExtrasItem.Group(
                                parentId = itemId,
                                type = type,
                                items = items,
                                title = title,
                                subtitle = subtitle,
                                isPlayed = items.all { it.played },
                                imageUrl =
                                    imageUrlService.getItemImageUrl(
                                        items.random(),
                                        ImageType.PRIMARY,
                                    ),
                            )
                        } else {
                            null
                        }
                    }.sortedBy { it.type.sortOrder }
            return result
        }
    }

/**
 * The order which extras should be shown
 */
private val ExtraType.sortOrder: Int
    get() =
        when (this) {
            ExtraType.TRAILER -> 0
            ExtraType.FEATURETTE -> 1
            ExtraType.SHORT -> 2
            ExtraType.CLIP -> 3
            ExtraType.SCENE -> 4
            ExtraType.SAMPLE -> 5
            ExtraType.DELETED_SCENE -> 6
            ExtraType.INTERVIEW -> 7
            ExtraType.BEHIND_THE_SCENES -> 8
            ExtraType.THEME_SONG -> 9
            ExtraType.THEME_VIDEO -> 10
            ExtraType.UNKNOWN -> 11
        }
