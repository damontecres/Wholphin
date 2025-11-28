package com.github.damontecres.wholphin.ui.detail

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.Color
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.supportedPlayableTypes
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class MoreDialogActions(
    val navigateTo: (Destination) -> Unit,
    var onClickWatch: (UUID, Boolean) -> Unit,
    var onClickFavorite: (UUID, Boolean) -> Unit,
    var onClickAddPlaylist: (UUID) -> Unit,
)

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
    context: Context,
    item: BaseItem,
    seriesId: UUID?,
    sourceId: UUID?,
    watched: Boolean,
    favorite: Boolean,
    actions: MoreDialogActions,
    onChooseVersion: () -> Unit,
    onChooseTracks: (MediaStreamType) -> Unit,
): List<DialogItem> =
    buildList {
        add(
            DialogItem(
                context.getString(R.string.play),
                Icons.Default.PlayArrow,
                iconColor = Color.Green.copy(alpha = .8f),
            ) {
                actions.navigateTo(
                    Destination.Playback(
                        item.id,
                        item.resumeMs ?: 0L,
                        item,
                    ),
                )
            },
        )
        item.data.mediaSources?.letNotEmpty { sources ->
            val source =
                sourceId?.let { sources.firstOrNull { it.id?.toUUIDOrNull() == sourceId } }
                    ?: sources.firstOrNull()
            source?.mediaStreams?.letNotEmpty { streams ->
                val audioCount = streams.count { it.type == MediaStreamType.AUDIO }
                val subtitleCount = streams.count { it.type == MediaStreamType.SUBTITLE }
                if (audioCount > 1) {
                    add(
                        DialogItem(
                            context.getString(
                                R.string.choose_stream,
                                context.getString(R.string.audio),
                            ),
                            R.string.fa_volume_low,
                        ) {
                            onChooseTracks.invoke(MediaStreamType.AUDIO)
                        },
                    )
                }
                if (subtitleCount > 0) {
                    add(
                        DialogItem(
                            context.getString(
                                R.string.choose_stream,
                                context.getString(R.string.subtitles),
                            ),
                            R.string.fa_closed_captioning,
                        ) {
                            onChooseTracks.invoke(MediaStreamType.SUBTITLE)
                        },
                    )
                }
            }
            if (sources.size > 1) {
                add(
                    DialogItem(
                        context.getString(
                            R.string.choose_stream,
                            context.getString(R.string.version),
                        ),
                        R.string.fa_file_video,
                    ) {
                        onChooseVersion.invoke()
                    },
                )
            }
        }
        add(
            DialogItem(
                text = R.string.add_to_playlist,
                iconStringRes = R.string.fa_list_ul,
            ) {
                actions.onClickAddPlaylist.invoke(item.id)
            },
        )
        add(
            DialogItem(
                text = if (watched) R.string.mark_unwatched else R.string.mark_watched,
                iconStringRes = if (watched) R.string.fa_eye else R.string.fa_eye_slash,
            ) {
                actions.onClickWatch.invoke(item.id, !watched)
            },
        )
        add(
            DialogItem(
                text = if (favorite) R.string.remove_favorite else R.string.add_favorite,
                iconStringRes = R.string.fa_heart,
                iconColor = if (favorite) Color.Red else Color.Unspecified,
            ) {
                actions.onClickFavorite.invoke(item.id, !favorite)
            },
        )
        seriesId?.let {
            add(
                DialogItem(
                    context.getString(R.string.go_to_series),
                    Icons.AutoMirrored.Filled.ArrowForward,
                ) {
                    actions.navigateTo(
                        Destination.MediaItem(
                            seriesId,
                            BaseItemKind.SERIES,
                        ),
                    )
                },
            )
        }
        add(
            DialogItem(
                context.getString(R.string.play_with_transcoding),
                Icons.Default.PlayArrow,
            ) {
                actions.navigateTo(
                    Destination.Playback(
                        item.id,
                        item.resumeMs ?: 0L,
                        item,
                        forceTranscoding = true,
                    ),
                )
            },
        )
    }

fun buildMoreDialogItemsForHome(
    context: Context,
    item: BaseItem,
    seriesId: UUID?,
    playbackPosition: Duration,
    watched: Boolean,
    favorite: Boolean,
    actions: MoreDialogActions,
): List<DialogItem> =
    buildList {
        val itemId = item.id
        add(
            DialogItem(
                context.getString(R.string.go_to),
                Icons.Default.ArrowForward,
            ) {
                actions.navigateTo(item.destination())
            },
        )
        if (item.type in supportedPlayableTypes) {
            if (playbackPosition >= 1.seconds) {
                add(
                    DialogItem(
                        context.getString(R.string.resume),
                        Icons.Default.PlayArrow,
                        iconColor = Color.Green.copy(alpha = .8f),
                    ) {
                        actions.navigateTo(
                            Destination.Playback(
                                itemId,
                                playbackPosition.inWholeMilliseconds,
                            ),
                        )
                    },
                )
                add(
                    DialogItem(
                        context.getString(R.string.restart),
                        Icons.Default.Refresh,
//                    iconColor = Color.Green.copy(alpha = .8f),
                    ) {
                        actions.navigateTo(
                            Destination.Playback(
                                itemId,
                                0L,
                            ),
                        )
                    },
                )
            } else {
                add(
                    DialogItem(
                        context.getString(R.string.play),
                        Icons.Default.PlayArrow,
                        iconColor = Color.Green.copy(alpha = .8f),
                    ) {
                        actions.navigateTo(
                            Destination.Playback(
                                itemId,
                                0L,
                            ),
                        )
                    },
                )
            }
        }
        add(
            DialogItem(
                text = R.string.add_to_playlist,
                iconStringRes = R.string.fa_list_ul,
            ) {
                actions.onClickAddPlaylist.invoke(itemId)
            },
        )
        add(
            DialogItem(
                text = if (watched) R.string.mark_unwatched else R.string.mark_watched,
                iconStringRes = if (watched) R.string.fa_eye else R.string.fa_eye_slash,
            ) {
                actions.onClickWatch.invoke(itemId, !watched)
            },
        )
        add(
            DialogItem(
                text = if (favorite) R.string.remove_favorite else R.string.add_favorite,
                iconStringRes = R.string.fa_heart,
                iconColor = if (favorite) Color.Red else Color.Unspecified,
            ) {
                actions.onClickFavorite.invoke(itemId, !favorite)
            },
        )
        seriesId?.let {
            add(
                DialogItem(
                    context.getString(R.string.go_to_series),
                    Icons.AutoMirrored.Filled.ArrowForward,
                ) {
                    actions.navigateTo(
                        Destination.MediaItem(
                            it,
                            BaseItemKind.SERIES,
                        ),
                    )
                },
            )
        }
    }

fun buildMoreDialogItemsForPerson(
    context: Context,
    person: Person,
    actions: MoreDialogActions,
): List<DialogItem> =
    buildList {
        val itemId = person.id
        add(
            DialogItem(
                context.getString(R.string.go_to),
                Icons.Default.ArrowForward,
            ) {
                actions.navigateTo(Destination.MediaItem(itemId, BaseItemKind.PERSON))
            },
        )
        add(
            DialogItem(
                text = if (person.favorite) R.string.remove_favorite else R.string.add_favorite,
                iconStringRes = R.string.fa_heart,
                iconColor = if (person.favorite) Color.Red else Color.Unspecified,
            ) {
                actions.onClickFavorite.invoke(itemId, !person.favorite)
            },
        )
    }
