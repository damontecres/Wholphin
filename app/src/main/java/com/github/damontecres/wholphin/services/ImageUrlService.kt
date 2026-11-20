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
            imageTags: Map<ImageType, String?>,
            backdropTags: List<String>,
            parentThumbId: UUID? = null,
            parentBackdropId: UUID? = null,
            fillWidth: Int? = null,
            fillHeight: Int? = null,
            seriesPrimaryTag: String? = null,
            parentLogoId: UUID? = null,
            parentLogoTag: String? = null,
            parentThumbTag: String? = null,
            parentBackdropTags: List<String> = emptyList(),
        ): String? =
            when (imageType) {
                ImageType.LOGO -> {
                    if (seriesId != null && (itemType == BaseItemKind.EPISODE || itemType == BaseItemKind.SEASON)) {
                        getItemImageUrl(
                            itemId = seriesId,
                            imageType = imageType,
                            fillWidth = fillWidth,
                            fillHeight = fillHeight,
                            tag = parentLogoTag.takeIf { parentLogoId == seriesId },
                        )
                    } else {
                        getItemImageUrl(
                            itemId = itemId,
                            imageType = imageType,
                            fillWidth = fillWidth,
                            fillHeight = fillHeight,
                            tag = imageTags[imageType],
                        )
                    }
                }

                ImageType.BACKDROP,
                -> {
                    if (seriesId != null && (itemType == BaseItemKind.EPISODE || itemType == BaseItemKind.SEASON)) {
                        getItemImageUrl(
                            itemId = seriesId,
                            imageType = imageType,
                            fillWidth = fillWidth,
                            fillHeight = fillHeight,
                            tag = parentBackdropTags.firstOrNull().takeIf { parentBackdropId == seriesId },
                        )
                    } else if (backdropTags.isNotEmpty()) {
                        getItemImageUrl(
                            itemId = itemId,
                            imageType = imageType,
                            fillWidth = fillWidth,
                            fillHeight = fillHeight,
                            tag = backdropTags.first(),
                        )
                    } else {
                        null
                    }
                }

                ImageType.THUMB -> {
                    if (useSeriesForPrimary && parentThumbId != null &&
                        (itemType == BaseItemKind.EPISODE || itemType == BaseItemKind.SEASON)
                    ) {
                        // Use parent's thumb
                        getItemImageUrl(
                            itemId = parentThumbId,
                            imageType = imageType,
                            fillWidth = fillWidth,
                            fillHeight = fillHeight,
                            tag = parentThumbTag,
                        )
                    } else if (useSeriesForPrimary && parentBackdropId != null &&
                        (itemType == BaseItemKind.EPISODE || itemType == BaseItemKind.SEASON)
                    ) {
                        // No parent thumb, so use backdrop instead
                        getItemImageUrl(
                            itemId = parentBackdropId,
                            imageType = ImageType.BACKDROP,
                            fillWidth = fillWidth,
                            fillHeight = fillHeight,
                            tag = parentBackdropTags.firstOrNull(),
                        )
                    } else if (parentThumbId != null && itemType == BaseItemKind.SEASON && imageType !in imageTags) {
                        getItemImageUrl(
                            itemId = parentThumbId,
                            imageType = imageType,
                            fillWidth = fillWidth,
                            fillHeight = fillHeight,
                            tag = parentThumbTag,
                        )
                    } else if (useSeriesForPrimary &&
                        parentThumbId == null &&
                        itemType == BaseItemKind.EPISODE &&
                        imageType !in imageTags
                    ) {
                        // Workaround to fall back to episode image if no parent thumb
                        getItemImageUrl(
                            itemId = itemId,
                            imageType = ImageType.PRIMARY,
                            fillWidth = fillWidth,
                            fillHeight = fillHeight,
                            tag = imageTags[ImageType.PRIMARY],
                        )
                    } else if (imageType !in imageTags && backdropTags.isNotEmpty()) {
                        // If no thumb, use backdrop if available
                        getItemImageUrl(
                            itemId = itemId,
                            imageType = ImageType.BACKDROP,
                            fillWidth = fillWidth,
                            fillHeight = fillHeight,
                            tag = backdropTags.first(),
                        )
                    } else {
                        getItemImageUrl(
                            itemId = itemId,
                            imageType = imageType,
                            fillWidth = fillWidth,
                            fillHeight = fillHeight,
                            tag = imageTags[imageType],
                        )
                    }
                }

                ImageType.PRIMARY,
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
                            tag = seriesPrimaryTag.takeIf { imageType == ImageType.PRIMARY },
                        )
                    } else if (seriesId != null && itemType == BaseItemKind.SEASON && imageType !in imageTags) {
                        getItemImageUrl(
                            itemId = seriesId,
                            imageType = imageType,
                            fillWidth = fillWidth,
                            fillHeight = fillHeight,
                            tag = seriesPrimaryTag.takeIf { imageType == ImageType.PRIMARY },
                        )
                    } else {
                        getItemImageUrl(
                            itemId = itemId,
                            imageType = imageType,
                            fillWidth = fillWidth,
                            fillHeight = fillHeight,
                            tag = imageTags[imageType],
                        )
                    }
                }

                else -> {
                    getItemImageUrl(
                        itemId = itemId,
                        imageType = imageType,
                        fillWidth = fillWidth,
                        fillHeight = fillHeight,
                        tag = imageTags[imageType],
                    )
                }
            }

        fun getItemImageUrl(
            item: BaseItem?,
            imageType: ImageType,
            fillWidth: Int? = null,
            fillHeight: Int? = null,
            useSeriesForPrimary: Boolean? = null,
        ): String? =
            if (item != null) {
                getItemImageUrl(
                    itemId = item.id,
                    itemType = item.type,
                    seriesId = item.data.seriesId,
                    useSeriesForPrimary = useSeriesForPrimary ?: item.useSeriesForPrimary,
                    imageTags = item.data.imageTags.orEmpty(),
                    imageType = imageType,
                    parentThumbId = item.data.parentThumbItemId,
                    parentBackdropId = item.data.parentBackdropItemId,
                    backdropTags = item.data.backdropImageTags.orEmpty(),
                    fillWidth = fillWidth,
                    fillHeight = fillHeight,
                    seriesPrimaryTag = item.data.seriesPrimaryImageTag,
                    parentLogoId = item.data.parentLogoItemId,
                    parentLogoTag = item.data.parentLogoImageTag,
                    parentThumbTag = item.data.parentThumbImageTag,
                    parentBackdropTags = item.data.parentBackdropImageTags.orEmpty(),
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
        ): String? {
            if (api.baseUrl.isNullOrBlank()) return null
            return api.imageApi.getItemImageUrl(
                itemId = itemId,
                imageType = imageType,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                width = width?.takeIf { it > 0 },
                height = height?.takeIf { it > 0 },
                quality = quality,
                fillWidth = fillWidth?.takeIf { it > 0 },
                fillHeight = fillHeight?.takeIf { it > 0 },
                tag = tag,
                format = format,
                percentPlayed = percentPlayed,
                unplayedCount = unplayedCount,
                blur = blur,
                backgroundColor = backgroundColor,
                foregroundLayer = foregroundLayer,
                imageIndex = imageIndex,
            )
        }

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
