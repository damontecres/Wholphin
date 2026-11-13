package com.github.damontecres.wholphin.ui.playback

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.subtitleLong
import com.github.damontecres.wholphin.services.ImageUrlService
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

/**
 * Information to display on the playback overlay
 */
data class PlaybackDisplayInfo(
    val title: String?,
    val subtitle: String?,
    val logoImageUrl: String?,
    val logoText: String?,
) {
    companion object {
        fun from(
            item: BaseItem?,
            imageUrlService: ImageUrlService,
        ): PlaybackDisplayInfo =
            when (item?.type) {
                BaseItemKind.TV_CHANNEL -> {
                    val program = item.data.currentProgram
                    if (program != null) {
                        val subtitle = program.subtitleLong(program.isSeries == true)
                        PlaybackDisplayInfo(
                            title = program.name,
                            subtitle = subtitle,
                            logoImageUrl = imageUrlService.getItemImageUrl(item, ImageType.PRIMARY),
                            logoText = item.name,
                        )
                    } else {
                        PlaybackDisplayInfo(
                            title = item.name,
                            subtitle = item.subtitleLong,
                            logoImageUrl = imageUrlService.getItemImageUrl(item, ImageType.PRIMARY),
                            logoText = null,
                        )
                    }
                }

                null -> {
                    PlaybackDisplayInfo(
                        title = null,
                        subtitle = null,
                        logoImageUrl = null,
                        logoText = null,
                    )
                }

                else -> {
                    PlaybackDisplayInfo(
                        title = item.title,
                        subtitle = item.subtitleLong,
                        logoImageUrl = imageUrlService.getItemImageUrl(item, ImageType.LOGO),
                        logoText = null,
                    )
                }
            }
    }
}
