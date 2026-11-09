package com.github.damontecres.wholphin.ui.components

import android.content.res.Resources
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.test.item
import com.github.damontecres.wholphin.test.song
import io.mockk.mockk
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Long-press menu on an album or song should offer Play directly above Add to queue.
 *
 * [DialogItem] labels are composables, so each entry is identified by clicking it
 * and recording which action fired.
 */
class ContextMenuMusicPlayTest {
    private val resources = mockk<Resources>(relaxed = true)

    private fun actionsFor(
        item: BaseItem,
        withPlayMusic: Boolean = true,
    ): List<String> {
        val fired = mutableListOf<String>()
        val actions =
            ContextMenuActions(
                navigateTo = { fired += "navigate" },
                onShowOverview = { fired += "overview" },
                onClickWatch = { _, _ -> fired += "watch" },
                onClickFavorite = { _, _ -> fired += "favorite" },
                onClickAddPlaylist = { fired += "addPlaylist" },
                onSendMediaInfo = { fired += "mediaInfo" },
                onDeleteItem = { fired += "delete" },
                onChooseVersion = { _, _ -> fired += "version" },
                onChooseTracks = { fired += "tracks" },
                onClearChosenStreams = { fired += "clearStreams" },
                onClickGoTo = { fired += "goTo" },
                onClickAddToQueue = { fired += "addToQueue" },
                onClickPlayMusic = if (withPlayMusic) ({ fired += "playMusic" }) else null,
            )
        val menu =
            ContextMenu.ForBaseItem(
                fromLongClick = true,
                item = item,
                chosenStreams = null,
                showGoTo = true,
                showStreamChoices = false,
                canDelete = false,
                canRemoveContinueWatching = false,
                canRemoveNextUp = false,
                actions = actions,
            )
        val entries =
            buildContextMenuItems(
                resources = resources,
                contextMenu = menu,
                item = item,
                seriesId = null,
                sourceId = null,
                watched = false,
                favorite = false,
                canClearChosenStreams = false,
                actions = actions,
                onChooseVersion = { fired += "version" },
                onChooseTracks = { fired += "tracks" },
                onShowOverview = { fired += "overview" },
                onClearChosenStreams = { fired += "clearStreams" },
                onClickDelete = { fired += "delete" },
                onClickPlayWith = { fired += "playWith" },
            )
        return entries.map { entry ->
            fired.clear()
            entry.onClick()
            fired.joinToString("+")
        }
    }

    private fun assertPlayDirectlyAboveQueue(entries: List<String>) {
        val play = entries.indexOf("playMusic")
        val queue = entries.indexOf("addToQueue")
        assertTrue("Play missing from $entries", play >= 0)
        assertTrue("Add to queue missing from $entries", queue >= 0)
        assertEquals("Play should be directly above Add to queue: $entries", queue - 1, play)
        assertEquals("Play should appear once: $entries", 1, entries.count { it == "playMusic" })
    }

    @Test
    fun album_hasPlayDirectlyAboveAddToQueue() {
        assertPlayDirectlyAboveQueue(actionsFor(BaseItem(item(BaseItemKind.MUSIC_ALBUM), false)))
    }

    @Test
    fun song_hasPlayDirectlyAboveAddToQueue() {
        assertPlayDirectlyAboveQueue(actionsFor(BaseItem(song(), false)))
    }

    @Test
    fun album_withoutPlayHandler_keepsAddToQueueOnly() {
        val entries = actionsFor(BaseItem(item(BaseItemKind.MUSIC_ALBUM), false), withPlayMusic = false)
        assertFalse(entries.contains("playMusic"))
        assertTrue(entries.contains("addToQueue"))
    }

    @Test
    fun movie_doesNotGetMusicPlay() {
        val entries = actionsFor(BaseItem(item(BaseItemKind.MOVIE), false))
        assertFalse(entries.contains("playMusic"))
        assertFalse(entries.contains("addToQueue"))
    }
}
