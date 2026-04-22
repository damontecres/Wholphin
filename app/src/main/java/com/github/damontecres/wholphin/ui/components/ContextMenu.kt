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
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.supportedPlayableTypes
import org.jellyfin.sdk.model.UUID
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
    ) : ContextMenu

    data class ForPerson(
        val fromLongClick: Boolean,
        val person: Person,
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
    streamChoiceService: StreamChoiceService,
    preferredSubtitleLanguage: String?,
    actions: ContextMenuActions,
) {
    when (contextMenu) {
        is ContextMenu.ForBaseItem -> {
            ContextMenuDialog(
                onDismissRequest,
                contextMenu.fromLongClick,
                streamChoiceService,
                contextMenu.item,
                contextMenu.chosenStreams,
                contextMenu.showGoTo,
                contextMenu.showStreamChoices,
                contextMenu.canDelete,
                contextMenu.canRemoveContinueWatching,
                contextMenu.canRemoveNextUp,
                preferredSubtitleLanguage,
                actions,
            )
        }

        is ContextMenu.ForPerson -> {
            PersonContextMenu(
                onDismissRequest = onDismissRequest,
                fromLongClick = contextMenu.fromLongClick,
                person = contextMenu.person,
                actions = actions,
            )
        }
    }
}

@Composable
fun ContextMenuDialog(
    onDismissRequest: () -> Unit,
    fromLongClick: Boolean,
    streamChoiceService: StreamChoiceService,
    item: BaseItem,
    chosenStreams: ChosenStreams?,
    showGoTo: Boolean,
    showStreamChoices: Boolean,
    canDelete: Boolean,
    canRemoveContinueWatching: Boolean,
    canRemoveNextUp: Boolean,
    preferredSubtitleLanguage: String?,
    actions: ContextMenuActions,
) {
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
                showGoTo = showGoTo,
                showStreamChoices = showStreamChoices,
                canDelete = canDelete,
                canRemoveContinueWatching = canRemoveContinueWatching,
                canRemoveNextUp = canRemoveNextUp,
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
                    streamChoiceService
                        .chooseSource(
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
        waitToLoad = fromLongClick,
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
fun PersonContextMenu(
    onDismissRequest: () -> Unit,
    fromLongClick: Boolean,
    person: Person,
    actions: ContextMenuActions,
) {
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
        waitToLoad = fromLongClick,
    )
}
