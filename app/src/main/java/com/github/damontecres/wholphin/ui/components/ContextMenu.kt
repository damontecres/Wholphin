package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.AudioItem
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.supportedPlayableTypes
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import kotlin.time.Duration

sealed interface ContextMenu {
    data class ForBaseItem(
        val fromLongClick: Boolean,
        val item: BaseItem,
        val chosenStreams: ChosenStreams?,
        val showGoTo: Boolean,
        val showStreamChoices: Boolean,
        val canDelete: Boolean,
        val canRemoveContinueWatching: Boolean,
        val canRemoveNextUp: Boolean,
        val actions: ContextMenuActions,
    ) : ContextMenu

    data class ForPerson(
        val fromLongClick: Boolean,
        val person: Person,
        val actions: PersonContextActions,
    ) : ContextMenu

    data class ForMusic(
        val fromLongClick: Boolean,
        val item: BaseItem,
        val index: Int,
        val canDelete: Boolean,
        val canRemoveFromQueue: Boolean,
        val actions: MusicContextActions,
    ) : ContextMenu

    data class ForQueue(
        val fromLongClick: Boolean,
        val item: AudioItem,
        val index: Int,
        val actions: QueueContextActions,
    ) : ContextMenu
}

data class ContextMenuActions(
    val navigateTo: (Destination) -> Unit,
    val onShowOverview: (BaseItem) -> Unit,
    val onClickWatch: (UUID, Boolean) -> Unit,
    val onClickFavorite: (UUID, Boolean) -> Unit,
    val onClickAddPlaylist: (UUID) -> Unit,
    val onSendMediaInfo: (UUID) -> Unit,
    val onClickDelete: (BaseItem) -> Unit,
    val onChooseVersion: (BaseItem, MediaSourceInfo) -> Unit,
    val onChooseTracks: (ChosenTrackResult) -> Unit,
    val onClearChosenStreams: (ChosenStreams?) -> Unit,
    val onClickGoTo: (BaseItem) -> Unit = { navigateTo(it.destination()) },
    val onClickRemoveFromNextUp: (BaseItem) -> Unit = {},
    val onClickAddToQueue: (BaseItem) -> Unit = {},
)

data class PersonContextActions(
    val navigateTo: (Destination) -> Unit,
    val onClickFavorite: (UUID, Boolean) -> Unit,
)

data class MusicContextActions(
    val navigateTo: (Destination) -> Unit,
    val onClickPlay: (Int, BaseItem) -> Unit,
    val onClickPlayNext: (Int, BaseItem) -> Unit,
    val onClickFavorite: (UUID, Boolean) -> Unit,
    val onClickAddPlaylist: (UUID) -> Unit,
    val onClickDelete: (BaseItem) -> Unit,
    val onClickAddToQueue: (BaseItem) -> Unit,
    val onClickRemoveFromQueue: (Int, BaseItem) -> Unit,
    val onClickGoToAlbum: (UUID) -> Unit = {
        navigateTo.invoke(Destination.MediaItem(itemId = it, type = BaseItemKind.MUSIC_ALBUM))
    },
    val onClickGoToArtist: (UUID) -> Unit = {
        navigateTo.invoke(Destination.MediaItem(itemId = it, type = BaseItemKind.MUSIC_ARTIST))
    },
)

data class QueueContextActions(
    val onNavigate: (Destination) -> Unit,
    val onClickPlay: (Int, AudioItem) -> Unit,
    val onClickPlayNext: (Int, AudioItem) -> Unit,
    val onClickGoToAlbum: (java.util.UUID) -> Unit = {
        onNavigate.invoke(Destination.MediaItem(itemId = it, type = BaseItemKind.MUSIC_ALBUM))
    },
    val onClickGoToArtist: (java.util.UUID) -> Unit = {
        onNavigate.invoke(Destination.MediaItem(itemId = it, type = BaseItemKind.MUSIC_ARTIST))
    },
    val onClickRemoveFromQueue: (Int, AudioItem) -> Unit,
)

data class ChosenTrackResult(
    val item: BaseItem,
    val streamType: MediaStreamType,
    val trackIndex: Int,
    val itemPlayback: ItemPlayback?,
)

@Composable
fun ContextMenuDialog(
    onDismissRequest: () -> Unit,
    contextMenu: ContextMenu,
    getMediaSource: ((dto: BaseItemDto, itemPlayback: ItemPlayback?) -> MediaSourceInfo?)?,
    preferredSubtitleLanguage: String?,
) {
    when (contextMenu) {
        is ContextMenu.ForBaseItem -> {
            ContextMenu(
                onDismissRequest,
                contextMenu,
                getMediaSource,
                preferredSubtitleLanguage,
                contextMenu.actions,
            )
        }

        is ContextMenu.ForPerson -> {
            ContextMenu(
                onDismissRequest = onDismissRequest,
                item = contextMenu,
                actions = contextMenu.actions,
            )
        }

        is ContextMenu.ForMusic -> {
            ContextMenu(
                onDismissRequest = onDismissRequest,
                item = contextMenu,
                actions = contextMenu.actions,
            )
        }

        is ContextMenu.ForQueue -> {
            ContextMenu(
                onDismissRequest = onDismissRequest,
                item = contextMenu,
                actions = contextMenu.actions,
            )
        }
    }
}

@Composable
fun ContextMenu(
    onDismissRequest: () -> Unit,
    contextMenu: ContextMenu.ForBaseItem,
    getMediaSource: ((dto: BaseItemDto, itemPlayback: ItemPlayback?) -> MediaSourceInfo?)?,
    preferredSubtitleLanguage: String?,
    actions: ContextMenuActions,
) {
    val item = contextMenu.item
    val chosenStreams = contextMenu.chosenStreams
    val context = LocalContext.current
    var chooseVersion by remember { mutableStateOf<DialogParams?>(null) }

    val dialogItems =
        remember(context, item, chosenStreams) {
            buildContextMenuItems(
                context = context,
                item = item,
                watched = item.played,
                favorite = item.favorite,
                seriesId = item.data.seriesId,
                sourceId = chosenStreams?.source?.id?.toUUIDOrNull(),
                canClearChosenStreams = chosenStreams.let { it?.itemPlayback != null || it?.plc != null },
                showGoTo = contextMenu.showGoTo,
                showStreamChoices = contextMenu.showStreamChoices,
                canDelete = contextMenu.canDelete,
                canRemoveContinueWatching = contextMenu.canRemoveContinueWatching,
                canRemoveNextUp = contextMenu.canRemoveNextUp,
                actions = actions,
                onChooseVersion = {
                    chooseVersion =
                        chooseVersionParams(
                            context,
                            item.data.mediaSources.orEmpty(),
                            chosenStreams?.source?.id?.toUUIDOrNull(),
                        ) { idx ->
                            val source = item.data.mediaSources!![idx]
                            actions.onChooseVersion.invoke(item, source)
                        }
                },
                onChooseTracks = { type ->
                    getMediaSource
                        ?.invoke(
                            item.data,
                            chosenStreams?.itemPlayback,
                        )?.let { source ->
                            chooseVersion =
                                chooseStream(
                                    context = context,
                                    streams = source.mediaStreams.orEmpty(),
                                    type = type,
                                    currentIndex =
                                        if (type == MediaStreamType.AUDIO) {
                                            chosenStreams?.audioStream?.index
                                        } else {
                                            chosenStreams?.subtitleStream?.index
                                        },
                                    onClick = { trackIndex ->
                                        actions.onChooseTracks.invoke(
                                            ChosenTrackResult(
                                                item = item,
                                                streamType = type,
                                                trackIndex = trackIndex,
                                                itemPlayback = chosenStreams?.itemPlayback,
                                            ),
                                        )
                                    },
                                    preferredSubtitleLanguage = preferredSubtitleLanguage,
                                )
                        }
                },
                onShowOverview = { actions.onShowOverview.invoke(item) },
                onClearChosenStreams = {
                    actions.onClearChosenStreams.invoke(chosenStreams)
                },
            )
        }
    DialogPopup(
        showDialog = true,
        title = item.title ?: "",
        dialogItems = dialogItems,
        onDismissRequest = onDismissRequest,
        dismissOnClick = false,
        waitToLoad = contextMenu.fromLongClick,
    )
    if (chooseVersion != null) {
        chooseVersion?.let { params ->
            DialogPopup(
                showDialog = true,
                title = params.title,
                dialogItems = params.items,
                onDismissRequest = { chooseVersion = null },
                dismissOnClick = true,
                waitToLoad = params.fromLongClick,
            )
        }
    }
}

private fun buildContextMenuItems(
    context: Context,
    item: BaseItem,
    seriesId: UUID?,
    sourceId: UUID?,
    watched: Boolean,
    favorite: Boolean,
    canClearChosenStreams: Boolean,
    showGoTo: Boolean,
    showStreamChoices: Boolean,
    canDelete: Boolean,
    canRemoveContinueWatching: Boolean,
    canRemoveNextUp: Boolean,
    actions: ContextMenuActions,
    onChooseVersion: () -> Unit,
    onChooseTracks: (MediaStreamType) -> Unit,
    onShowOverview: () -> Unit,
    onClearChosenStreams: () -> Unit,
): List<DialogItem> =
    buildList {
        if (showGoTo) {
            add(
                DialogItem(
                    context.getString(R.string.go_to),
                    Icons.Default.ArrowForward,
                    dismissOnClick = true,
                ) {
                    actions.onClickGoTo(item)
                },
            )
        }
        if (item.type in supportedPlayableTypes) {
            if (item.playbackPosition >= Duration.ZERO) {
                add(
                    DialogItem(
                        context.getString(R.string.resume),
                        Icons.Default.PlayArrow,
                        iconColor = Color.Green.copy(alpha = .8f),
                        dismissOnClick = true,
                    ) {
                        actions.navigateTo(
                            Destination.Playback(
                                item.id,
                                item.playbackPosition.inWholeMilliseconds,
                            ),
                        )
                    },
                )
                add(
                    DialogItem(
                        context.getString(R.string.restart),
                        Icons.Default.Refresh,
                        dismissOnClick = true,
                    ) {
                        actions.navigateTo(
                            Destination.Playback(
                                item.id,
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
                        dismissOnClick = true,
                    ) {
                        actions.navigateTo(
                            Destination.Playback(
                                item.id,
                                0L,
                            ),
                        )
                    },
                )
            }
        }
        if (showStreamChoices) {
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
                                dismissOnClick = false,
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
                                dismissOnClick = false,
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
                            dismissOnClick = false,
                        ) {
                            onChooseVersion.invoke()
                        },
                    )
                }
            }
        }
        if (item.type == BaseItemKind.MUSIC_ALBUM) {
            add(
                DialogItem(
                    context.getString(R.string.add_to_queue),
                    Icons.Default.Add,
                    dismissOnClick = true,
                ) {
                    actions.onClickAddToQueue(item)
                },
            )
        }
        add(
            DialogItem(
                text = R.string.add_to_playlist,
                iconStringRes = R.string.fa_list_ul,
                dismissOnClick = true,
            ) {
                actions.onClickAddPlaylist.invoke(item.id)
            },
        )
        if (canDelete) {
            add(
                DialogItem(
                    context.getString(R.string.delete),
                    Icons.Default.Delete,
                    iconColor = Color.Red.copy(alpha = .8f),
                    dismissOnClick = true,
                ) {
                    actions.onClickDelete.invoke(item)
                },
            )
        }
        if (canRemoveContinueWatching && !watched && item.playbackPosition > Duration.ZERO) {
            add(
                DialogItem(
                    text = R.string.remove_continue_watching,
                    iconStringRes = R.string.fa_eye,
                    dismissOnClick = true,
                ) {
                    actions.onClickWatch.invoke(item.id, false)
                },
            )
        }
        if (canRemoveNextUp && item.type == BaseItemKind.EPISODE && item.data.seriesId != null) {
            add(
                DialogItem(
                    text = R.string.remove_next_up,
                    iconStringRes = R.string.fa_tag,
                    dismissOnClick = true,
                ) {
                    actions.onClickRemoveFromNextUp.invoke(item)
                },
            )
        }
        add(
            DialogItem(
                text = if (watched) R.string.mark_unwatched else R.string.mark_watched,
                iconStringRes = if (watched) R.string.fa_eye else R.string.fa_eye_slash,
                dismissOnClick = true,
            ) {
                actions.onClickWatch.invoke(item.id, !watched)
            },
        )
        add(
            DialogItem(
                text = if (favorite) R.string.remove_favorite else R.string.add_favorite,
                iconStringRes = R.string.fa_heart,
                iconColor = if (favorite) Color.Red else Color.Unspecified,
                dismissOnClick = true,
            ) {
                actions.onClickFavorite.invoke(item.id, !favorite)
            },
        )
        item.data.albumId?.let { albumId ->
            add(
                DialogItem(
                    context.getString(R.string.go_to_album),
                    R.string.fa_compact_disc,
                    dismissOnClick = true,
                ) {
                    actions.navigateTo(
                        Destination.MediaItem(
                            albumId,
                            BaseItemKind.MUSIC_ALBUM,
                            null,
                        ),
                    )
                },
            )
        }
        item.data.artistItems?.firstOrNull()?.id?.let { artistId ->
            add(
                DialogItem(
                    context.getString(R.string.go_to_artist),
                    R.string.fa_user,
                    dismissOnClick = true,
                ) {
                    actions.navigateTo(
                        Destination.MediaItem(
                            artistId,
                            BaseItemKind.MUSIC_ARTIST,
                            null,
                        ),
                    )
                },
            )
        }
        seriesId?.let {
            add(
                DialogItem(
                    context.getString(R.string.go_to_series),
                    Icons.AutoMirrored.Filled.ArrowForward,
                    dismissOnClick = true,
                ) {
                    actions.navigateTo(
                        Destination.MediaItem(
                            seriesId,
                            BaseItemKind.SERIES,
                            null,
                        ),
                    )
                },
            )
        }
        if (item.data.mediaSources?.isNotEmpty() == true) {
            add(
                DialogItem(
                    context.getString(R.string.media_information),
                    Icons.Default.Info,
                    dismissOnClick = true,
                ) {
                    onShowOverview.invoke()
                },
            )
        }
        if (showStreamChoices && canClearChosenStreams) {
            add(
                DialogItem(
                    context.getString(R.string.clear_track_choices),
                    Icons.Default.Delete,
                    dismissOnClick = true,
                ) {
                    onClearChosenStreams()
                },
            )
        }
        if (item.type in supportedPlayableTypes) {
            add(
                DialogItem(
                    context.getString(R.string.play_with_transcoding),
                    Icons.Default.PlayArrow,
                    dismissOnClick = true,
                ) {
                    actions.navigateTo(
                        Destination.Playback(
                            item.id,
                            item.resumeMs ?: 0L,
                            forceTranscoding = true,
                        ),
                    )
                },
            )
        }
        if (item.data.mediaSources?.isNotEmpty() == true) {
            add(
                DialogItem(
                    text = R.string.send_media_info_log_to_server,
                    iconStringRes = R.string.fa_file_video,
                    dismissOnClick = true,
                ) {
                    actions.onSendMediaInfo.invoke(item.id)
                },
            )
        }
    }

@Composable
fun ContextMenu(
    onDismissRequest: () -> Unit,
    item: ContextMenu.ForPerson,
    actions: PersonContextActions,
) {
    val person = item.person
    val dialogItems =
        buildList {
            val itemId = person.id
            add(
                DialogItem(
                    stringResource(R.string.go_to),
                    Icons.Default.ArrowForward,
                ) {
                    actions.navigateTo(Destination.MediaItem(itemId, BaseItemKind.PERSON, null))
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
    DialogPopup(
        showDialog = true,
        title = person.name ?: "",
        dialogItems = dialogItems,
        onDismissRequest = onDismissRequest,
        dismissOnClick = true,
        waitToLoad = item.fromLongClick,
    )
}

@Composable
fun ContextMenu(
    onDismissRequest: () -> Unit,
    item: ContextMenu.ForMusic,
    actions: MusicContextActions,
) {
    val context = LocalContext.current
    val dialogItems =
        remember(context, item, actions) { buildContextForMusic(context, item, actions) }
    DialogPopup(
        showDialog = true,
        title = item.item.title ?: "",
        dialogItems = dialogItems,
        onDismissRequest = onDismissRequest,
        dismissOnClick = true,
        waitToLoad = item.fromLongClick,
    )
}

fun buildContextForMusic(
    context: Context,
    music: ContextMenu.ForMusic,
    actions: MusicContextActions,
): List<DialogItem> =
    buildList {
        val item = music.item
        val index = music.index
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
        if (music.canRemoveFromQueue) {
            add(
                DialogItem(
                    context.getString(R.string.remove_from_queue),
                    Icons.Default.Delete,
                ) {
                    actions.onClickRemoveFromQueue(index, item)
                },
            )
        } else {
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
                actions.onClickAddPlaylist.invoke(item.id)
            },
        )
        if (music.canDelete) {
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

@Composable
fun ContextMenu(
    onDismissRequest: () -> Unit,
    item: ContextMenu.ForQueue,
    actions: QueueContextActions,
) {
    val context = LocalContext.current
    val dialogItems =
        remember(context, item, actions) {
            buildContextForMusicQueue(
                context,
                item.item,
                item.index,
                actions,
            )
        }
    DialogPopup(
        showDialog = true,
        title = item.item.title ?: "",
        dialogItems = dialogItems,
        onDismissRequest = onDismissRequest,
        dismissOnClick = true,
        waitToLoad = item.fromLongClick,
    )
}

fun buildContextForMusicQueue(
    context: Context,
    item: AudioItem,
    index: Int,
    actions: QueueContextActions,
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
        add(
            DialogItem(
                context.getString(R.string.remove_from_queue),
                Icons.Default.Delete,
            ) {
                actions.onClickRemoveFromQueue(index, item)
            },
        )
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
