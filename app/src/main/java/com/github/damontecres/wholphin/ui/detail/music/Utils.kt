package com.github.damontecres.wholphin.ui.detail.music

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.AudioItem
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.DefaultItemFields
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

data class MusicMoreDialogActions(
    val onNavigate: (Destination) -> Unit,
    val onClickPlay: (Int, BaseItem) -> Unit,
    val onClickPlayNext: (Int, BaseItem) -> Unit,
    val onClickAddToQueue: (Int, BaseItem) -> Unit,
    val onClickFavorite: (UUID, Boolean) -> Unit,
    val onClickAddPlaylist: (UUID) -> Unit,
    val onClickGoToAlbum: (UUID) -> Unit = {
        onNavigate.invoke(Destination.MediaItem(itemId = it, type = BaseItemKind.MUSIC_ALBUM))
    },
    val onClickGoToArtist: (UUID) -> Unit = {
        onNavigate.invoke(Destination.MediaItem(itemId = it, type = BaseItemKind.MUSIC_ARTIST))
    },
    val onClickRemoveFromQueue: (Int) -> Unit,
)

fun buildMoreDialogForMusic(
    context: Context,
    actions: MusicMoreDialogActions,
    item: BaseItem,
    index: Int,
    canRemove: Boolean,
): List<DialogItem> =
    buildList {
        add(
            DialogItem(
                context.getString(R.string.play),
                Icons.Default.PlayArrow,
                iconColor = Color.Green.copy(alpha = .8f),
            ) {
                actions.onClickPlay(index, item)
            },
        )
        add(
            DialogItem(
                context.getString(R.string.play_next),
                Icons.Default.PlayArrow,
                iconColor = Color.Green.copy(alpha = .8f),
            ) {
                actions.onClickPlayNext(index, item)
            },
        )
        if (canRemove) {
            add(
                DialogItem(
                    context.getString(R.string.remove_from_queue),
                    Icons.Default.Delete,
                ) {
                    actions.onClickRemoveFromQueue(index)
                },
            )
        }
        add(
            DialogItem(
                context.getString(R.string.add_to_queue),
                Icons.Default.Add,
            ) {
                actions.onClickAddToQueue(index, item)
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
                    R.string.fa_compact_disc,
                ) {
                    actions.onClickGoToAlbum.invoke(item.data.albumId!!)
                },
            )
        }
        if ((item.type == BaseItemKind.AUDIO || item.type == BaseItemKind.MUSIC_ALBUM) && item.data.artistItems?.isNotEmpty() == true) {
            add(
                DialogItem(
                    context.getString(R.string.go_to_artist),
                    R.string.fa_user,
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

data class MusicQueueDialogActions(
    val onNavigate: (Destination) -> Unit,
    val onClickPlay: (Int, AudioItem) -> Unit,
    val onClickPlayNext: (Int, AudioItem) -> Unit,
    val onClickGoToAlbum: (UUID) -> Unit = {
        onNavigate.invoke(Destination.MediaItem(itemId = it, type = BaseItemKind.MUSIC_ALBUM))
    },
    val onClickGoToArtist: (UUID) -> Unit = {
        onNavigate.invoke(Destination.MediaItem(itemId = it, type = BaseItemKind.MUSIC_ARTIST))
    },
    val onClickRemoveFromQueue: (Int, AudioItem) -> Unit,
)

fun buildMoreDialogForMusicQueue(
    context: Context,
    actions: MusicQueueDialogActions,
    item: AudioItem,
    index: Int,
    canRemove: Boolean,
): List<DialogItem> =
    buildList {
        add(
            DialogItem(
                context.getString(R.string.play),
                Icons.Default.PlayArrow,
                iconColor = Color.Green.copy(alpha = .8f),
            ) {
                actions.onClickPlay(index, item)
            },
        )
        add(
            DialogItem(
                context.getString(R.string.play_next),
                Icons.Default.PlayArrow,
                iconColor = Color.Green.copy(alpha = .8f),
            ) {
                actions.onClickPlayNext(index, item)
            },
        )
        if (canRemove) {
            add(
                DialogItem(
                    context.getString(R.string.remove_from_queue),
                    Icons.Default.Delete,
                ) {
                    actions.onClickRemoveFromQueue(index, item)
                },
            )
        }
        if (item.albumId != null) {
            add(
                DialogItem(
                    context.getString(R.string.go_to_album),
                    Icons.Default.ArrowForward,
                ) {
                    actions.onClickGoToAlbum.invoke(item.albumId)
                },
            )
        }
        if (item.artistId != null) {
            add(
                DialogItem(
                    context.getString(R.string.go_to_artist),
                    Icons.Default.ArrowForward,
                ) {
                    actions.onClickGoToArtist.invoke(item.artistId)
                },
            )
        }
    }

suspend fun ViewModel.getPagerForAlbum(
    api: ApiClient,
    albumId: UUID,
): ApiRequestPager<GetItemsRequest> {
    val request =
        GetItemsRequest(
            parentId = albumId,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            fields = DefaultItemFields,
            sortBy =
                listOf(
                    ItemSortBy.PARENT_INDEX_NUMBER,
                    ItemSortBy.INDEX_NUMBER,
                    ItemSortBy.SORT_NAME,
                ),
        )
    return ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope).init()
}

suspend fun ViewModel.getPagerForArtist(
    api: ApiClient,
    artistId: UUID,
): ApiRequestPager<GetItemsRequest> {
    val request =
        GetItemsRequest(
            parentId = artistId,
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            fields = DefaultItemFields,
            // TODO better sort
            sortBy =
                listOf(
                    ItemSortBy.PARENT_INDEX_NUMBER,
                    ItemSortBy.INDEX_NUMBER,
                    ItemSortBy.SORT_NAME,
                ),
        )
    return ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope).init()
}
