package com.github.damontecres.wholphin.ui.detail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.Color
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.nav.Destination
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.serializer.toUUIDOrNull

/**
 * Build the [DialogItem]s when clicking "More"
 *
 * If there are multiple versions, adds an option to pick one
 *
 * If there is more than one (ie two or more) audio track, adds an option to pick one
 *
 * If there are any (ie one or more) subtitle tracks, adds an option to disable or pick one
 *
 * @param item the media item to build for, typically an Episode or Movie
 * @param series the item's series or null if not a TV episode; a non-null value will include a "Go to Series" option
 * @param sourceId the item's media source UUID
 * @param navigateTo a function to trigger a navigation
 * @param onChooseVersion callback to pick a version of the item
 * @param onChooseTracks callback to pick a track for the given type of the item
 */
fun buildMoreDialogItems(
    item: BaseItem,
    series: BaseItem?,
    sourceId: UUID?,
    watched: Boolean,
    favorite: Boolean,
    navigateTo: (Destination) -> Unit,
    onClickWatch: (Boolean) -> Unit,
    onClickFavorite: (Boolean) -> Unit,
    onChooseVersion: () -> Unit,
    onChooseTracks: (MediaStreamType) -> Unit,
): List<DialogItem> =
    buildList {
        add(
            DialogItem(
                "Play",
                Icons.Default.PlayArrow,
                iconColor = Color.Green.copy(alpha = .8f),
            ) {
                navigateTo(
                    Destination.Playback(
                        item.id,
                        item.resumeMs ?: 0L,
                        item,
                    ),
                )
            },
        )
        add(
            DialogItem(
                text = if (watched) R.string.mark_unwatched else R.string.mark_watched,
                iconStringRes = if (watched) R.string.fa_eye else R.string.fa_eye_slash,
            ) {
                onClickWatch.invoke(!watched)
            },
        )
        add(
            DialogItem(
                text = if (favorite) R.string.remove_favorite else R.string.add_favorite,
                iconStringRes = R.string.fa_heart,
                iconColor = if (favorite) Color.Red else Color.Unspecified,
            ) {
                onClickFavorite.invoke(!favorite)
            },
        )
        series?.let {
            add(
                DialogItem(
                    "Go to series",
                    Icons.AutoMirrored.Filled.ArrowForward,
                ) {
                    navigateTo(
                        Destination.MediaItem(
                            series.id,
                            BaseItemKind.SERIES,
                            series,
                        ),
                    )
                },
            )
        }
        item.data.mediaSources?.letNotEmpty { sources ->
            if (sources.size > 1) {
                add(
                    DialogItem(
                        "Choose Version",
                        R.string.fa_file_video,
                    ) {
                        onChooseVersion.invoke()
                    },
                )
            }
            val source =
                sourceId?.let { sources.firstOrNull { it.id?.toUUIDOrNull() == sourceId } }
                    ?: sources.firstOrNull()
            source?.mediaStreams?.letNotEmpty { streams ->
                val audioCount = streams.count { it.type == MediaStreamType.AUDIO }
                val subtitleCount = streams.count { it.type == MediaStreamType.SUBTITLE }
                if (audioCount > 1) {
                    add(
                        DialogItem(
                            "Choose audio",
                            R.string.fa_volume_low,
                        ) {
                            onChooseTracks.invoke(MediaStreamType.AUDIO)
                        },
                    )
                }
                if (subtitleCount > 0) {
                    add(
                        DialogItem(
                            "Choose subtitles",
                            R.string.fa_closed_captioning,
                        ) {
                            onChooseTracks.invoke(MediaStreamType.SUBTITLE)
                        },
                    )
                }
            }
        }
    }
