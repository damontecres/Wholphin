package com.github.damontecres.wholphin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.ui.detail.MoreDialogActions
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItems
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.serializer.toUUIDOrNull

@Composable
fun ContextMenu(
    onDismissRequest: () -> Unit,
    fromLongClick: Boolean,
    streamChoiceService: StreamChoiceService,
    item: BaseItem,
    chosenStreams: ChosenStreams?,
    canDelete: Boolean,
    preferredSubtitleLanguage: String?,
    actions: MoreDialogActions,
    onChooseVersion: (BaseItem, MediaSourceInfo) -> Unit,
    onChooseTracks: (MediaStreamType, Int) -> Unit,
    onShowOverview: () -> Unit,
    onClearChosenStreams: () -> Unit,
) {
    val context = LocalContext.current
    var chooseVersion by remember { mutableStateOf<DialogParams?>(null) }

    val dialogItems =
        remember(item, chosenStreams) {
            buildMoreDialogItems(
                context = context,
                item = item,
                watched = item.played,
                favorite = item.favorite,
                seriesId = item.data.seriesId,
                sourceId = chosenStreams?.source?.id?.toUUIDOrNull(),
                canClearChosenStreams = chosenStreams.let { it?.itemPlayback != null || it?.plc != null },
                canDelete = canDelete,
                actions = actions,
                onChooseVersion = {
                    chooseVersion =
                        chooseVersionParams(
                            context,
                            item.data.mediaSources.orEmpty(),
                            chosenStreams?.source?.id?.toUUIDOrNull(),
                        ) { idx ->
                            val source = item.data.mediaSources!![idx]
                            onChooseVersion.invoke(item, source)
                        }
                    onDismissRequest.invoke()
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
                                        onChooseTracks.invoke(type, trackIndex)
                                    },
                                    preferredSubtitleLanguage = preferredSubtitleLanguage,
                                )
                        }
                },
                onShowOverview = onShowOverview,
                onClearChosenStreams = {
                    onClearChosenStreams.invoke()
                    onDismissRequest.invoke()
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
