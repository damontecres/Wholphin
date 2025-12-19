package com.github.damontecres.wholphin.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.damontecres.wholphin.data.model.BaseItem
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageFormat
import org.jellyfin.sdk.model.api.ImageType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides image URLs for items with several convenience methods
 *
 * This is available in compose UI code via [com.github.damontecres.wholphin.ui.LocalImageUrlService]
 */
@Singleton
class ImageUrlService
    @Inject
    constructor(
        private val api: ApiClient,
    ) {
        fun getItemImageUrl(
            itemId: UUID,
            itemType: BaseItemKind,
            seriesId: UUID?,
            useSeriesForPrimary: Boolean,
            imageType: ImageType,
            fillWidth: Int? = null,
            fillHeight: Int? = null,
        ): String =
            when (imageType) {
                ImageType.BACKDROP,
                ImageType.LOGO,
                -> {
                    if (seriesId != null && (itemType == BaseItemKind.EPISODE || itemType == BaseItemKind.SEASON)) {
                        getItemImageUrl(
                            itemId = seriesId,
                            imageType = imageType,
                            fillWidth = fillWidth,
                            fillHeight = fillHeight,
                        )
                    } else {
                        getItemImageUrl(
                            itemId = itemId,
                            imageType = imageType,
                            fillWidth = fillWidth,
                            fillHeight = fillHeight,
                        )
                    }
                }

                ImageType.PRIMARY,
                ImageType.THUMB,
                ImageType.BANNER,
                -> {
                    if (useSeriesForPrimary && seriesId != null &&
                        (itemType == BaseItemKind.EPISODE || itemType == BaseItemKind.SEASON)
                    ) {
                        getItemImageUrl(
                            itemId = seriesId,
                            imageType = imageType,
                            fillWidth = fillWidth,
                            fillHeight = fillHeight,
                        )
                    } else {
                        getItemImageUrl(
                            itemId = itemId,
                            imageType = imageType,
                            fillWidth = fillWidth,
                            fillHeight = fillHeight,
                        )
                    }
                }

                else -> {
                    getItemImageUrl(
                        itemId = itemId,
                        imageType = imageType,
                        fillWidth = fillWidth,
                        fillHeight = fillHeight,
                    )
                }
            }

        fun getItemImageUrl(
            item: BaseItem?,
            imageType: ImageType,
            fillWidth: Int? = null,
            fillHeight: Int? = null,
        ): String? =
            if (item != null) {
                getItemImageUrl(
                    itemId = item.id,
                    itemType = item.type,
                    seriesId = item.data.seriesId,
                    useSeriesForPrimary = item.useSeriesForPrimary,
                    imageType = imageType,
                    fillWidth = fillWidth,
                    fillHeight = fillHeight,
                )
            } else {
                null
            }

        fun getItemImageUrl(
            itemId: UUID,
            imageType: ImageType,
            maxWidth: Int? = null,
            maxHeight: Int? = null,
            width: Int? = null,
            height: Int? = null,
            quality: Int? = QUALITY,
            fillWidth: Int? = null,
            fillHeight: Int? = null,
            tag: String? = null,
            format: ImageFormat? = null,
            percentPlayed: Double? = null,
            unplayedCount: Int? = null,
            blur: Int? = null,
            backgroundColor: String? = null,
            foregroundLayer: String? = null,
            imageIndex: Int? = null,
        ): String =
            api.imageApi.getItemImageUrl(
                itemId = itemId,
                imageType = imageType,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                width = width,
                height = height,
                quality = quality,
                fillWidth = fillWidth,
                fillHeight = fillHeight,
                tag = tag,
                format = format,
                percentPlayed = percentPlayed,
                unplayedCount = unplayedCount,
                blur = blur,
                backgroundColor = backgroundColor,
                foregroundLayer = foregroundLayer,
                imageIndex = imageIndex,
            )

        fun getUserImageUrl(userId: UUID) = api.imageApi.getUserImageUrl(userId)

        /**
         * Just a convenient way to get the image URL and remember it
         */
        @Composable
        fun rememberImageUrl(
            item: BaseItem?,
            imageType: ImageType = ImageType.PRIMARY,
        ) = remember(item, imageType) {
            if (item != null) {
                getItemImageUrl(item, imageType)
            } else {
                null
            }
        }

        companion object {
            private const val QUALITY = 96
        }
    }
