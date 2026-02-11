package com.github.damontecres.wholphin.ui.detail.music

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.Color
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.nav.Destination
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

data class MusicMoreDialogActions(
    val onNavigate: (Destination) -> Unit,
    val onClickPlay: (Int, UUID) -> Unit,
    val onClickAddToQueue: (Int, UUID) -> Unit,
    val onClickFavorite: (UUID, Boolean) -> Unit,
    val onClickAddPlaylist: (UUID) -> Unit,
    val onClickGoToAlbum: (UUID) -> Unit = {
        onNavigate.invoke(Destination.MediaItem(itemId = it, type = BaseItemKind.MUSIC_ALBUM))
    },
    val onClickGoToArtist: (UUID) -> Unit = {
        onNavigate.invoke(Destination.MediaItem(itemId = it, type = BaseItemKind.MUSIC_ARTIST))
    },
)

fun buildMoreDialogForMusic(
    context: Context,
    actions: MusicMoreDialogActions,
    item: BaseItem,
    index: Int,
): List<DialogItem> =
    buildList {
        add(
            DialogItem(
                context.getString(R.string.play),
                Icons.Default.PlayArrow,
                iconColor = Color.Green.copy(alpha = .8f),
            ) {
                actions.onClickPlay(index, item.id)
            },
        )
        add(
            DialogItem(
                context.getString(R.string.add_to_queue),
                Icons.Default.Add,
            ) {
                actions.onClickAddToQueue(index, item.id)
            },
        )
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
                text = if (item.favorite) R.string.remove_favorite else R.string.add_favorite,
                iconStringRes = R.string.fa_heart,
                iconColor = if (item.favorite) Color.Red else Color.Unspecified,
            ) {
                actions.onClickFavorite.invoke(item.id, !item.favorite)
            },
        )
        if (item.type == BaseItemKind.AUDIO && item.data.albumId != null) {
            add(
                DialogItem(
                    context.getString(R.string.go_to_album),
                    Icons.Default.ArrowForward,
                ) {
                    actions.onClickGoToAlbum.invoke(item.data.albumId!!)
                },
            )
        }
        if ((item.type == BaseItemKind.AUDIO || item.type == BaseItemKind.MUSIC_ALBUM) && item.data.artistItems?.isNotEmpty() == true) {
            add(
                DialogItem(
                    context.getString(R.string.go_to_artist),
                    Icons.Default.ArrowForward,
                ) {
                    actions.onClickGoToArtist.invoke(
                        item.data.artistItems!!
                            .first()
                            .id,
                    )
                },
            )
        }
    }
