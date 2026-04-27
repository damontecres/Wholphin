package com.github.damontecres.wholphin.ui.detail

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.Color
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.supportedPlayableTypes
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class MoreDialogActions(
    val navigateTo: (Destination) -> Unit,
    val onClickWatch: (UUID, Boolean) -> Unit,
    val onClickFavorite: (UUID, Boolean) -> Unit,
    val onClickAddPlaylist: (UUID) -> Unit,
    val onSendMediaInfo: (UUID) -> Unit,
    val onClickDelete: (BaseItem) -> Unit,
    val onClickGoTo: (BaseItem) -> Unit = { navigateTo(it.destination()) },
    val onClickRemoveFromNextUp: (BaseItem) -> Unit = {},
    val onClickAddToQueue: (BaseItem) -> Unit = {},
)

fun buildMoreDialogItemsForHome(
    context: Context,
    item: BaseItem,
    seriesId: UUID?,
    playbackPosition: Duration,
    watched: Boolean,
    favorite: Boolean,
    canDelete: Boolean,
    actions: MoreDialogActions,
    canRemoveContinueWatching: Boolean = false,
    canRemoveNextUp: Boolean = false,
): List<DialogItem> =
    buildList {
        val itemId = item.id
        add(
            DialogItem(
                context.getString(R.string.go_to),
                Icons.Default.ArrowForward,
            ) {
                actions.onClickGoTo(item)
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
        if (item.type == BaseItemKind.MUSIC_ALBUM) {
            add(
                DialogItem(
                    context.getString(R.string.add_to_queue),
                    Icons.Default.Add,
                ) {
                    actions.onClickAddToQueue(item)
                },
            )
        }
        add(
            DialogItem(
                text = R.string.add_to_playlist,
                iconStringRes = R.string.fa_list_ul,
            ) {
                actions.onClickAddPlaylist.invoke(itemId)
            },
        )
        if (canDelete) {
            add(
                DialogItem(
                    context.getString(R.string.delete),
                    Icons.Default.Delete,
                    iconColor = Color.Red.copy(alpha = .8f),
                ) {
                    actions.onClickDelete.invoke(item)
                },
            )
        }
        if (canRemoveContinueWatching && !watched && playbackPosition > Duration.ZERO) {
            add(
                DialogItem(
                    text = R.string.remove_continue_watching,
                    iconStringRes = R.string.fa_eye,
                ) {
                    actions.onClickWatch.invoke(itemId, false)
                },
            )
        }
        if (canRemoveNextUp && item.type == BaseItemKind.EPISODE && item.data.seriesId != null) {
            add(
                DialogItem(
                    text = R.string.remove_next_up,
                    iconStringRes = R.string.fa_tag,
                ) {
                    actions.onClickRemoveFromNextUp.invoke(item)
                },
            )
        }
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
                            null,
                        ),
                    )
                },
            )
        }
        add(
            DialogItem(
                text = R.string.send_media_info_log_to_server,
                iconStringRes = R.string.fa_file_video,
            ) {
                actions.onSendMediaInfo.invoke(itemId)
            },
        )
    }
